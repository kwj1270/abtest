package com.official.woozi.abtoggle.scenario

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.config.ToggleKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 로컬 실행과 테스트용. 서버 간 공유하지 않으며 만료 항목은 접근 시 제거한다. */
class InMemoryScenarioAssignmentStore(
    private val clock: Clock = Clock.systemUTC(),
) : ScenarioAssignmentStore {
    private data class Entry(val route: Route, val expiresAt: Instant)
    private val entries = ConcurrentHashMap<Pair<String, ScenarioId>, Entry>()

    override fun find(toggleKey: ToggleKey, scenarioId: ScenarioId): Route? =
        entries.computeIfPresent(toggleKey.value to scenarioId) { _, entry ->
            entry.takeIf { clock.instant().isBefore(it.expiresAt) }
        }?.route

    override fun putIfAbsent(toggleKey: ToggleKey, scenarioId: ScenarioId, route: Route, ttl: Duration): Route {
        require(!ttl.isNegative && !ttl.isZero) { "ttl must be positive" }
        return entries.compute(toggleKey.value to scenarioId) { _, existing ->
            val now = clock.instant()
            if (existing != null && now.isBefore(existing.expiresAt)) existing
            else Entry(route, now.plus(ttl))
        }!!.route
    }
}
