# Repository Guidelines

## Project Structure & Module Organization

This repository is a multi-module Kotlin project built with Gradle Kotlin DSL.

- `ab-toggle/`: toggle library (`com.official.woozi.abtoggle`). Includes Spring Data Redis adapters in its `redis` package; includes the shared BankToggle enum used by both servers. Account business logic stays in the servers.
- `legacy-server/`: Spring Boot app standing in for the legacy monolith (`com.official.woozi.abtest.legacy`, port 8080).
- `msa-server/`: Spring Boot app standing in for the new server (`com.official.woozi.abtest.msa`, port 8081).
- Each app keeps its configuration in `src/main/resources/application.yaml` and its tests under `src/test/kotlin`, mirroring production package paths. Keep new packages beneath each app's base package for component scanning.
- `build.gradle.kts`: shared Kotlin, Java toolchain, compiler, and test configuration for all subprojects; module dependencies live in each module's `build.gradle.kts`.
- `docs/`: design documents (see `docs/README.md`).
- `gradle/wrapper/`: pinned Gradle distribution configuration.

The apps expose simple in-memory deposit, withdrawal, and balance APIs in their `account` packages. HTTP controllers/DTOs are in `account.api`; `AccountService` delegates business rules and per-account concurrency to `Account`. The legacy server keeps MSA HTTP integration in `account.client`; each server keeps API error handling in `web`. Both apps depend on `ab-toggle`. The legacy server automatically routes existing account APIs to its local service or msa-server using Redis configuration and assignments. Routing is pinned per user_id for 90 days. user_id and device_id headers are required, but only user_id controls assignment; both are forwarded to MSA. There is no client scenario-start API. Balance-only asynchronous LEGACY-to-MSA shadow comparison is optional via BALANCE_SHADOW_ENABLED (default false). Redis is required for scenario routing; account balances are still in memory. Full tests require Docker for Testcontainers. See `docs/bank-poc-runbook.md`.

The legacy admin page is `/admin/toggles`; ratios are integer 0–100, missing/invalid Redis values default to 0%. Existing user assignments stay pinned. Both servers expose Micrometer Prometheus on separate loopback management ports (18090/18091). The root `compose.yaml` starts Redis, Prometheus, Grafana only; optional k6 is under the `load` profile. Run both application servers locally with JDK 25. Monitoring assets and read-only k6 load tests are under `monitoring/` and `load-test/`; see `docs/monitoring-load-test.md`. Avoid ID-valued metric tags.

## Build, Test, and Development Commands

Use JDK 25 and the checked-in Gradle wrapper (`gradlew.bat` on Windows).

- `./gradlew :legacy-server:bootRun` / `./gradlew :msa-server:bootRun`: start each server locally (run both in separate terminals).
- `./gradlew test`: run the JUnit Platform test suite for all modules.
- `./gradlew build`: compile, run checks, and package all modules.
- `./gradlew bootJar`: create executable JARs in each app's `build/libs/`.
- `./gradlew :legacy-server:test --tests 'com.official.woozi.abtest.legacy.LegacyServerApplicationTests'`: run one test class.

## Coding Style & Naming Conventions

Use idiomatic Kotlin with four-space indentation, matching the existing Gradle script and sources. Use `UpperCamelCase` for classes, `lowerCamelCase` for functions and properties, and lowercase package names. Match filenames to their primary class. Preserve strict nullability compiler settings. No formatter or linter is configured; keep formatting consistent with neighboring code.

## Testing Guidelines

Tests use JUnit Jupiter, Kotlin test support, and Spring Boot test dependencies. Name test classes `*Tests`, following `LegacyServerApplicationTests`. Use focused unit tests for isolated behavior and `@SpringBootTest` when application-context integration is required. Add regression tests for behavior changes. No coverage threshold is configured. Run `./gradlew test` before submitting changes.

## Commit & Pull Request Guidelines

There are no commits yet, so no established commit-message convention exists. Use concise, imperative subjects such as `Add experiment assignment endpoint`. Keep commits focused. Pull requests should describe the change, link relevant issues, and report validation commands and results. Document configuration or service requirements; include request/response examples for API changes.

## Security & Configuration Tips

Keep credentials out of tracked configuration. Supply environment-specific settings through environment variables or external Spring configuration. Document Redis or MySQL setup when introducing functionality that requires those services.
