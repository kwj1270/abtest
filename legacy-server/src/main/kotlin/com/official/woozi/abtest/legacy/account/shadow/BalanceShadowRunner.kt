package com.official.woozi.abtest.legacy.account.shadow

import com.official.woozi.abtest.legacy.account.client.MsaAccountClient
import com.official.woozi.abtest.legacy.web.RequestIdentity
import com.official.woozi.abtoggle.Route
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 성공한 LEGACY 잔액조회만 MSA와 비교한다. NEW 요청과 쓰기는 제외한다. */
@Component
class BalanceShadowRunner(
    private val msa: MsaAccountClient,
    private val properties: BalanceShadowProperties,
    @Qualifier("balanceShadowExecutor") private val executor: Executor,
    private val metrics: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun count(result: String) = metrics.counter("bank.shadow.comparisons", "result", result).increment()

    fun compare(accountNo: String, identity: RequestIdentity, primaryRoute: Route, primaryBalance: Long) {
        if (!properties.enabled || primaryRoute != Route.LEGACY) return
        try {
            executor.execute {
                try {
                    val shadowBalance = msa.balance(accountNo, identity)

                    if (primaryBalance == shadowBalance) {
                        count("matched")
                        log.debug("Balance shadow matched: primaryRoute={}", primaryRoute)
                    } else {
                        count("mismatched")
                        log.warn("Balance shadow mismatch: primaryRoute={}", primaryRoute)
                    }
                } catch (e: Exception) {
                    count("failed")
                    log.warn("Balance shadow failed: primaryRoute={}, cause={}", primaryRoute, e.javaClass.simpleName)
                }
            }
        } catch (_: RejectedExecutionException) {
            count("rejected")
            log.warn("Balance shadow skipped: executor queue unavailable")
        }
    }
}
