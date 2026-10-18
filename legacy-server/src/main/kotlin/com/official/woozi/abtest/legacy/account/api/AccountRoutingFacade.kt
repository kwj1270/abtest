package com.official.woozi.abtest.legacy.account.api

import com.official.woozi.abtest.legacy.account.application.AccountService
import com.official.woozi.abtest.legacy.account.client.MsaAccountClient
import com.official.woozi.abtest.legacy.account.shadow.BalanceShadowRunner
import com.official.woozi.abtest.legacy.web.RequestIdentity
import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.Route.LEGACY
import com.official.woozi.abtoggle.Route.NEW
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtoggle.scenario.ScenarioId
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class AccountRoutingFacade(
    private val legacy: AccountService,
    private val msa: MsaAccountClient,
    private val scenarios: ScenarioRouter,
    private val shadows: BalanceShadowRunner,
    private val metrics: MeterRegistry,
) {
    fun balance(accountNo: String, identity: RequestIdentity): Long {
        val primaryRoute = route(identity.userId)
        val balance = when (primaryRoute) {
            LEGACY -> legacy.balance(accountNo)
            NEW -> msa.balance(accountNo, identity)
        }
        shadows.compare(accountNo, identity, primaryRoute, balance)
        return balance
    }

    fun deposit(accountNo: String, amount: Long, identity: RequestIdentity): Long = when (route(identity.userId)) {
        LEGACY -> legacy.deposit(accountNo, amount)
        NEW -> msa.deposit(accountNo, amount, identity)
    }

    fun withdraw(accountNo: String, amount: Long, identity: RequestIdentity): Long = when (route(identity.userId)) {
        LEGACY -> legacy.withdraw(accountNo, amount)
        NEW -> msa.withdraw(accountNo, amount, identity)
    }

    private fun route(userId: AssignmentKey.UserId): Route = scenarios.start(
        BankToggle.ACCOUNT_OPERATIONS,
        ScenarioId("user:${userId.value}"),
        userId,
    ).also { route ->
        metrics.counter("bank.routing.requests", "toggle", BankToggle.ACCOUNT_OPERATIONS.value, "route", route.name).increment()
    }
}
