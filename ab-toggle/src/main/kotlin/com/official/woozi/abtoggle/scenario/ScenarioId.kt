package com.official.woozi.abtoggle.scenario

/** 연속 API가 공유하는 시나리오 실행 ID. */
@JvmInline
value class ScenarioId(val value: String) {
    init {
        require(value.isNotBlank()) { "scenarioId must not be blank" }
    }
}
