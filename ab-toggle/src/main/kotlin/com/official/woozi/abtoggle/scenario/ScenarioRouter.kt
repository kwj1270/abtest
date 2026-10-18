package com.official.woozi.abtoggle.scenario

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.exception.ScenarioAssignmentNotFoundException
import java.time.Duration

/** 최초 경로를 90일간 유지한다. 실제 업무 실행은 호출자가 담당한다. */
class ScenarioRouter(
    private val evaluator: ToggleEvaluator,
    private val store: ScenarioAssignmentStore,
    private val assignmentTtl: Duration = Duration.ofDays(90),
) {
    init {
        require(!assignmentTtl.isNegative && !assignmentTtl.isZero) { "assignmentTtl must be positive" }
    }

    /** 업무 실행 전에 호출한다. 이미 배정되어 있으면 재평가하거나 TTL을 연장하지 않는다. */
    fun start(toggleKey: ToggleKey, scenarioId: ScenarioId, assignmentKey: AssignmentKey): Route {
        store.find(toggleKey, scenarioId)?.let { return it }
        val route = evaluator.decide(toggleKey, assignmentKey)
        return store.putIfAbsent(toggleKey, scenarioId, route, assignmentTtl)
    }

    /** 후속 API 전용. 누락/만료 시 재배정하지 않으며 저장소 장애도 그대로 전파한다. */
    fun requireRoute(toggleKey: ToggleKey, scenarioId: ScenarioId): Route =
        store.find(toggleKey, scenarioId)
            ?: throw ScenarioAssignmentNotFoundException(toggleKey, scenarioId)
}
