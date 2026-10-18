package com.official.woozi.abtest.legacy.toggle

import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.scenario.ScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ToggleConfiguration {
    @Bean
    fun toggleEvaluator(source: ToggleConfigSource) = ToggleEvaluator(source)

    @Bean
    fun scenarioRouter(evaluator: ToggleEvaluator, store: ScenarioAssignmentStore) =
        ScenarioRouter(evaluator, store)
}
