package com.official.woozi.abtest.legacy.account.shadow

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.official.woozi.abtest.legacy.account.api.AccountRoutingFacade
import com.official.woozi.abtest.legacy.account.application.AccountService
import com.official.woozi.abtest.legacy.account.client.MsaAccountClient
import com.official.woozi.abtest.legacy.web.RequestIdentity
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.scenario.InMemoryScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.web.client.RestClientException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BalanceShadowRunnerTests {
    private val metrics = SimpleMeterRegistry()
    private val legacy = spy(AccountService())
    private val msa = mock(MsaAccountClient::class.java)
    private val identity = RequestIdentity("user-1", "device-1")
    private val pending = mutableListOf<Runnable>()

    @Test
    fun `기준 응답은 Shadow 작업 실행을 기다리지 않고 조회 결과를 반환한다`() {
        legacy.deposit("demo-0001", 100)
        `when`(msa.balance("demo-0001", identity)).thenReturn(200)
        val facade = facade()
        assertEquals(100L, facade.balance("demo-0001", identity))
        assertEquals(1, pending.size)
        verify(msa, never()).balance("demo-0001", identity)
        pending.single().run()
        assertEquals(1.0, metrics.get("bank.shadow.comparisons").tag("result", "mismatched").counter().count())
        verify(msa).balance("demo-0001", identity)
        assertEquals(100L, legacy.balance("demo-0001"))
    }

    @Test
    fun `일치한 섀도우 결과를 집계한다`() {
        `when`(msa.balance("demo-0001", identity)).thenReturn(0)
        assertEquals(0L, facade().balance("demo-0001", identity))
        pending.single().run()
        assertEquals(1.0, metrics.get("bank.shadow.comparisons").tag("result", "matched").counter().count())
    }

    @Test
    fun `NEW 요청은 MSA만 조회하고 Shadow 작업을 등록하지 않는다`() {
        `when`(msa.balance("demo-0001", identity)).thenReturn(200)
        assertEquals(200L, facade(percent = 100).balance("demo-0001", identity))
        verify(legacy, never()).balance("demo-0001")
        assertTrue(pending.isEmpty())
        assertEquals(1.0, metrics.get("bank.routing.requests").tag("route", "NEW").counter().count())
        verify(msa, times(1)).balance("demo-0001", identity)
    }

    @Test
    fun `Shadow 서버 오류와 작업 거부는 기준 응답에 영향을 주지 않는다`() {
        `when`(msa.balance("demo-0001", identity)).thenThrow(RestClientException("shadow failed"))
        assertEquals(0L, facade().balance("demo-0001", identity))
        pending.single().run()
        assertEquals(1.0, metrics.get("bank.shadow.comparisons").tag("result", "failed").counter().count())
        val rejected = facade(executor = Executor { throw RejectedExecutionException("full") })
        assertEquals(0L, rejected.balance("demo-0001", identity))
        assertEquals(1.0, metrics.get("bank.shadow.comparisons").tag("result", "rejected").counter().count())
    }

    @Test
    fun `비활성 상태와 쓰기 API에서는 Shadow를 실행하지 않는다`() {
        assertEquals(0L, facade(enabled = false).balance("demo-0001", identity))
        val facade = facade()
        assertEquals(100L, facade.deposit("demo-0001", 100, identity))
        assertEquals(70L, facade.withdraw("demo-0001", 30, identity))
        assertTrue(pending.isEmpty())
        verify(msa, never()).balance("demo-0001", identity)
    }

    @Test
    fun `기준 조회 실패 시 오류를 그대로 반환하고 Shadow로 복구하지 않는다`() {
        `when`(msa.balance("demo-0001", identity)).thenThrow(RestClientException("primary failed"))
        assertFailsWith<RestClientException> { facade(percent = 100).balance("demo-0001", identity) }
        assertTrue(pending.isEmpty())
        verify(legacy, never()).balance("demo-0001")
    }

    private fun facade(
        percent: Int = 0,
        enabled: Boolean = true,
        executor: Executor = Executor { pending.add(it) },
    ): AccountRoutingFacade {
        val evaluator = ToggleEvaluator(StaticToggleConfigSource(listOf(ToggleConfig(BankToggle.ACCOUNT_OPERATIONS, percent))))
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore())
        return AccountRoutingFacade(legacy, msa, router, BalanceShadowRunner(msa, BalanceShadowProperties(enabled), executor, metrics), metrics)
    }
}
