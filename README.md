# AB Toggle 마이그레이션 PoC

동일한 API 안에서 서버가 사용자별로 LEGACY 또는 MSA 경로를 선택하는 Kotlin·Spring Boot PoC다. MurmurHash3로 신규 사용자를 비율 배정하고 Redis에 최초 경로를 저장한다. 어드민 비율 조절, 잔액 조회의 비동기 섀도우 비교, Micrometer·Prometheus·Grafana 관측과 k6 부하 테스트를 제공한다. 계좌 API는 검증용 샘플이다.

> **신규 비율을 0%로 내려도 이미 NEW에 배정된 사용자는 NEW를 유지한다.** 비율은 새로 배정되는 사용자에게만 적용된다. 현재 샘플은 사용자별 최초 경로를 90일간 고정하며, 0% 설정은 기존 사용자까지 즉시 복귀시키는 킬 스위치가 아니다.

## 실행

JDK 25와 Docker Compose가 필요하다. **서버 두 개는 로컬 JVM/IntelliJ에서 실행**하고 Redis·Prometheus·Grafana만 Compose로 실행한다. DB는 사용하지 않는다.

```sh
docker compose up -d --wait
```

별도 터미널 두 개에서 실행한다. IntelliJ를 사용하면 각 서버 Run Configuration에 동일한 환경변수를 등록한다.

```sh
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=6389 \
  MANAGEMENT_SERVER_ADDRESS=0.0.0.0 SERVER_ADDRESS=0.0.0.0 \
  ./gradlew :msa-server:bootRun
```

```sh
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=6389 \
  MANAGEMENT_SERVER_ADDRESS=0.0.0.0 SERVER_ADDRESS=0.0.0.0 BALANCE_SHADOW_ENABLED=true \
  ./gradlew :legacy-server:bootRun
```

서버를 IntelliJ와 터미널에서 중복 실행하지 않는다. `localhost`의 IPv4/IPv6 접속이 서로 다른 프로세스로 향하면 어드민과 메트릭이 다른 서버를 볼 수 있다. 위 실행은 Docker가 로컬 서버에 접근하도록 전체 인터페이스에 바인딩하므로 신뢰하는 로컬 개발 환경에서 사용한다. 어드민에는 인증이 없다.

| 대상 | 주소 |
| --- | --- |
| 토글 어드민 | http://127.0.0.1:8080/admin/toggles |
| LEGACY API | http://127.0.0.1:8080 |
| MSA API | http://127.0.0.1:8081 |
| Grafana | http://127.0.0.1:13000/d/bank-ab-toggle |
| Prometheus targets | http://127.0.0.1:19090/targets |
| Compose Redis | `127.0.0.1:6389` |

**Redis 포트 주의:** 앱 기본값은 `localhost:6379`이고 Compose Redis는 `6389`다. 위 환경변수를 생략하면 기존 Redis를 조회할 수 있다. 어드민은 LEGACY 서버가 연결한 Redis를 그대로 사용한다. 멀티 인스턴스는 같은 Redis database·설정 prefix·배정 prefix를 사용해야 한다.

## 비율과 경로 유지

어드민의 **계좌 입출금·조회** 비율을 0~100 정수로 변경하고 저장한다. 이체·잔액 조회의 별도 토글은 아직 업무에 연결하지 않은 예비 토글이다.

- 설정 누락이나 잘못된 비율은 NEW 0%로 평가한다. Redis 연결 장애는 누락과 구분하며, 배정 조회·저장에 실패하면 업무를 실행하지 않는다.
- `user_id`와 `device_id` 헤더는 모두 필수이며 배정에는 `user_id`만 사용한다.
- 기존 경로는 최초 배정 시점부터 90일 유지하고 조회로 TTL을 연장하지 않는다. 만료 후 다음 요청은 현재 비율로 재배정한다.
- 비율은 신규 사용자 배정의 근사 비율이다. 요청량 비율을 정확히 맞추지 않으며 기존 사용자 배정까지 변경하지 않는다.
- 현재는 실제 신청 ID 단위가 아닌 사용자 단위 고정이다. 완료·취소 시 배정을 종료하는 기능은 없다.

```sh
curl http://127.0.0.1:8080/accounts/demo-0001/balance \
  -H 'user_id: demo-user-1' -H 'device_id: demo-device-1'
```

## 섀도우와 부하 테스트

섀도우 기본값은 비활성이다. 위 LEGACY 실행 예시는 `BALANCE_SHADOW_ENABLED=true`로 활성화한다. 성공한 LEGACY 잔액 조회만 MSA와 비동기 비교하고 NEW 요청과 쓰기 요청은 제외한다. 전용 스레드는 2개, 대기 작업은 최대 100개다. 포화 시 비교를 생략하고 `rejected` 메트릭을 기록하며, 비교 실패도 사용자 응답으로 전파하지 않는다. 별도 메시지 큐는 없다.

```sh
docker compose --profile load run --rm k6
```

기본 10 VU·2분 동안 매 요청마다 랜덤 사용자 ID로 잔액을 조회한다. 로컬 k6가 설치되어 있다면 다음 명령도 가능하다. 제어 API 포트는 자동 할당해 동시 실행 충돌을 피한다.

```sh
BASE_URL=http://127.0.0.1:8080 k6 run --address 127.0.0.1:0 load-test/accounts.js
```

Grafana에서 실제 NEW 요청 비중, 섀도우 매칭률·실패율·큐 거부율, 응답시간과 JVM 자원을 확인한다. 섀도우가 비활성이거나 LEGACY 비교 표본이 없으면 매칭률을 계산할 수 없다. 비율을 바꾸며 테스트했다면 전체 기간의 비중은 여러 설정이 섞인 값이다. 랜덤 신규 사용자 배정도 Redis에 90일간 남는다.

## 데이터와 장애 정책

양 서버의 계좌 잔액은 **각자의 메모리**에 저장되고 동기화하지 않는다. 서버 재시작 시 잔액은 초기화되지만 Redis의 토글 설정과 배정은 남는다. 실제 데이터 마이그레이션·운영 계좌 서비스가 구현된 것은 아니다.

MSA 실패 시 LEGACY에서 재실행하거나 자동 재시도하지 않는다. 연결 타임아웃은 기본 2초, 읽기 타임아웃은 3초다. Redis AOF와 named volume은 로컬 재시작 시 데이터 보존용이며 장애 복구나 백업을 대신하지 않는다.

## 검증과 종료

```sh
./gradlew test
docker compose config --quiet
```

전체 테스트는 Docker가 필요하며 Testcontainers Redis로 개발 데이터와 격리한다. 자격증명은 환경변수로 공급하고 `.env`·로그·빌드 결과는 커밋하지 않는다.

서버는 IntelliJ 또는 각 터미널에서 종료하고 인프라는 아래 명령으로 종료한다. 일반 종료에서는 볼륨 데이터를 유지한다.

```sh
docker compose down
```

`docker compose down -v`는 Redis·Prometheus·Grafana 데이터를 모두 삭제하므로 테스트 환경 전체 초기화가 필요할 때만 사용한다.

- [API 사용법과 오류 정책](docs/bank-poc-runbook.md)
- [배정 알고리즘과 경로 유지](docs/scenario-routing.md)
- [Grafana·k6 실행과 지표 해석](docs/monitoring-load-test.md)
- [문서 목록](docs/README.md)
