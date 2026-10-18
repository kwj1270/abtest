package com.official.woozi.abtoggle.scenario

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.config.ToggleKey
import java.time.Duration

interface ScenarioAssignmentStore {
    fun find(toggleKey: ToggleKey, scenarioId: ScenarioId): Route?

    fun putIfAbsent(toggleKey: ToggleKey, scenarioId: ScenarioId, route: Route, ttl: Duration): Route
}
