# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

PoC for **ABToggle**, a feature toggle for gradually migrating traffic from a legacy monolith to a new server (ratio-based routing, shadow comparison). A/B business-metric analysis is out of scope for now. A banking domain (account balance / transaction lookup first) is only the test vehicle. Read `docs/feature-toggle-migration.md` (requirements, open decisions) and `docs/bank-poc-phase1.md` (current phase scenarios) before designing anything. Redis-backed toggles and scenario routing are implemented. Account APIs support deposit, withdrawal, and balance queries; current usage is documented in `docs/bank-poc-runbook.md`. See `AGENTS.md` for style, testing, and commit conventions.

## Modules

| Module | Role | Port |
| --- | --- | --- |
| `ab-toggle` | Toggle library with Spring Data Redis adapters. The shared BankToggle enum lives here; account business logic stays in the servers; assignment keys are sealed types and results are Route. | — |
| `legacy-server` | Legacy monolith stand-in. Entry point that evaluates toggles and calls `msa-server` for NEW scenarios. Has its own in-memory account state; shadow execution is not implemented. | 8080 |
| `msa-server` | New server stand-in (runs on k8s in reality; plain process locally). | 8081 |

Root `build.gradle.kts` applies Kotlin JVM, the JDK 25 toolchain, compiler flags, and JUnit to every subproject. The apps add the Spring Boot plugins themselves; `ab-toggle` imports the Spring Boot dependency BOM for its Redis adapters and tests.

## Stack

- Kotlin 2.3, JDK 25, Gradle 9.8 wrapper, Kotlin DSL
- Spring Boot 4.1 with `spring-boot-starter-webmvc` (servlet MVC, not WebFlux) and `spring-boot-starter-data-redis`
- Jackson 3 (`tools.jackson.module:jackson-module-kotlin`): use `tools.jackson.*` packages, not Jackson 2's `com.fasterxml.jackson.*`
- Account balances are in memory; no database driver or JDBC/JPA starter is declared
- Compiler flags: `-Xjsr305=strict` and `-Xannotation-default-target=param-property`

## Commands

```bash
./gradlew build                                   # all modules: compile, test, package
./gradlew :legacy-server:bootRun                     # run one server (use two terminals for both)
./gradlew :msa-server:bootRun
./gradlew :ab-toggle:test                    # one module's tests
./gradlew :legacy-server:test --tests 'com.official.woozi.abtest.legacy.LegacyServerApplicationTests.contextLoads'  # one test
```

No linter or formatter is configured. Redis defaults to `localhost:6379`, configurable through Spring environment variables. Full tests require Docker. MSA client settings use `bank.msa-server.*` and `MSA_SERVER_URL`/`MSA_CONNECT_TIMEOUT`/`MSA_READ_TIMEOUT`. Supply credentials via environment variables, not `application.yaml`.

## Packages

- `ab-toggle`: `com.official.woozi.abtoggle`
- `legacy-server`: `com.official.woozi.abtest.legacy`
- `msa-server`: `com.official.woozi.abtest.msa` (not `new`, which is a Java keyword)
