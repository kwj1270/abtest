package com.official.woozi.abtest.legacy.account.shadow

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BalanceShadowProperties::class)
class BalanceShadowConfiguration {
    @Bean
    fun balanceShadowExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        queueCapacity = 100
        setThreadNamePrefix("balance-shadow-")
    }
}
