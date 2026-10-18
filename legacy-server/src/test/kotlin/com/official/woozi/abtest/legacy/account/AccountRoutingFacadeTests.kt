package com.official.woozi.abtest.legacy.account

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.official.woozi.abtest.legacy.account.api.AccountRoutingFacade
import com.official.woozi.abtest.legacy.account.application.AccountService
import com.official.woozi.abtest.legacy.account.client.MsaAccountClient
import org.springframework.web.client.RestClient
import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtest.legacy.account.shadow.BalanceShadowRunner
import com.official.woozi.abtest.legacy.account.shadow.BalanceShadowProperties
import com.official.woozi.abtest.legacy.web.RequestIdentity
import java.util.concurrent.Executor
import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.scenario.InMemoryScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioId
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import com.sun.net.httpserver.HttpServer
import org.springframework.web.client.RestClientResponseException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountRoutingFacadeTests {
    @Test
    fun `신규 서버 오류 시 기존 서버에서 쓰기를 재실행하지 않는다`() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/accounts/demo-0001/deposits") { exchange ->
            requests.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            val body = "{\"error\":\"unavailable\"}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val legacy = AccountService()
            val store = InMemoryScenarioAssignmentStore()
            val id = ScenarioId("user:user-1")
            store.putIfAbsent(BankToggle.ACCOUNT_OPERATIONS, id, Route.NEW, Duration.ofDays(90))
            val router = ScenarioRouter(ToggleEvaluator(StaticToggleConfigSource(emptyList())), store)
            val msa = MsaAccountClient(RestClient.create("http://127.0.0.1:${server.address.port}"))
            val shadows = BalanceShadowRunner(msa, BalanceShadowProperties(), Executor { it.run() }, SimpleMeterRegistry())
            val service = AccountRoutingFacade(
                legacy,
                msa,
                router,
                shadows,
                SimpleMeterRegistry(),
            )
            assertFailsWith<RestClientResponseException> { service.deposit("demo-0001", 1000, RequestIdentity("user-1", "device-1")) }
            assertEquals(1, requests.get())
            assertEquals(0L, legacy.balance("demo-0001"))
        } finally {
            server.stop(0)
        }
    }
}
