package com.official.woozi.abtoggle.exception

import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.scenario.ScenarioId

class ScenarioAssignmentNotFoundException(val toggleKey: ToggleKey, val scenarioId: ScenarioId) :
    IllegalStateException("scenario assignment not found or expired")
