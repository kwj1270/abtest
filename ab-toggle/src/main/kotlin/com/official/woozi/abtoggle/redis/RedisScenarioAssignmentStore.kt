package com.official.woozi.abtoggle.redis

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.scenario.ScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioId
import java.time.Duration
import java.util.Base64
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/** 같은 Redis primary와 keyPrefix를 사용하는 모든 인스턴스가 최초 배정을 공유한다. */
class RedisScenarioAssignmentStore(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String = "ab-toggle:scenario",
) : ScenarioAssignmentStore {
    init {
        require(keyPrefix.isNotBlank()) { "keyPrefix must not be blank" }
    }

    override fun find(toggleKey: ToggleKey, scenarioId: ScenarioId): Route? =
        redis.opsForValue().get(key(toggleKey, scenarioId))?.let(Route::valueOf)

    override fun putIfAbsent(toggleKey: ToggleKey, scenarioId: ScenarioId, route: Route, ttl: Duration): Route {
        val ttlMillis = ttl.toMillis()
        require(ttlMillis > 0) { "ttl must be at least one millisecond" }
        val stored = checkNotNull(redis.execute(
            PUT_IF_ABSENT,
            listOf(key(toggleKey, scenarioId)),
            route.name,
            ttlMillis.toString(),
        )) { "Redis did not return a stored scenario route" }
        return Route.valueOf(stored)
    }

    private fun key(toggleKey: ToggleKey, scenarioId: ScenarioId): String =
        "$keyPrefix:${encode(toggleKey.value)}:${encode(scenarioId.value)}"

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private companion object {
        // 저장과 기존 값 조회를 한 번에 실행하여 명령 사이의 만료 경쟁을 방지한다.
        val PUT_IF_ABSENT = DefaultRedisScript(
            """
            local inserted = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
            if inserted then
                return ARGV[1]
            end
            return redis.call('GET', KEYS[1])
            """.trimIndent(),
            String::class.java,
        )
    }
}
