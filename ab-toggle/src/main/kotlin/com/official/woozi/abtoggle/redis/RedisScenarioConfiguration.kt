package com.official.woozi.abtoggle.redis

import com.official.woozi.abtoggle.scenario.ScenarioAssignmentStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration(proxyBeanMethods = false)
class RedisScenarioConfiguration {
    @Bean
    fun toggleConfigSource(
        redis: StringRedisTemplate,
        @Value("\${ab-toggle.redis.config-prefix:ab-toggle:config}") keyPrefix: String,
    ): RedisToggleConfigSource = RedisToggleConfigSource(redis, keyPrefix)

    @Bean
    fun scenarioAssignmentStore(
        redis: StringRedisTemplate,
        @Value("\${ab-toggle.redis.key-prefix:ab-toggle:scenario}") keyPrefix: String,
    ): ScenarioAssignmentStore = RedisScenarioAssignmentStore(redis, keyPrefix)
}
