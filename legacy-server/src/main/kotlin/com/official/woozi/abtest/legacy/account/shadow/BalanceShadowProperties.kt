package com.official.woozi.abtest.legacy.account.shadow

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bank.balance-shadow")
data class BalanceShadowProperties(val enabled: Boolean = false)
