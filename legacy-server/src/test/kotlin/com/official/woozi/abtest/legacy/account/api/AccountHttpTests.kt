package com.official.woozi.abtest.legacy.account.api

import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.scenario.InMemoryScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioAssignmentStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest

@Import(AccountHttpTests.LocalRoutingConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountHttpTests {
    @Value("\${local.server.port}") private var port: Int = 0
    private val client = HttpClient.newHttpClient()

    @Test
    fun `account endpoints and error responses`() {
        assertEquals(200, get("/accounts/demo-0001/balance").statusCode())
        val deposit = post("/accounts/demo-0001/deposits", "{\"amount\":10000}")
        assertEquals(200, deposit.statusCode())
        assertTrue(deposit.body().contains("\"amount\":10000"))
        val withdrawal = post("/accounts/demo-0001/withdrawals", "{\"amount\":3000}")
        assertEquals(200, withdrawal.statusCode())
        assertTrue(withdrawal.body().contains("\"amount\":7000"))
        assertEquals(409, post("/accounts/demo-0001/withdrawals", "{\"amount\":7001}").statusCode())
        val invalidAmount = post("/accounts/demo-0001/deposits", "{\"amount\":-1}")
        assertEquals(400, invalidAmount.statusCode())
        assertTrue(invalidAmount.body().contains("INVALID_REQUEST"))
        assertTrue(invalidAmount.body().contains("fieldErrors"))
        assertEquals(400, post("/accounts/demo-0001/deposits", "{}").statusCode())
        val missing = get("/accounts/missing/balance")
        assertEquals(404, missing.statusCode())
        assertTrue(missing.body().contains("ACCOUNT_NOT_FOUND"))
        assertTrue(get("/accounts/demo-0001/balance").body().contains("\"amount\":7000"))
    }

    @Test
    fun `사용자와 기기 헤더가 없거나 공백이면 업무 실행 전에 거부한다`() {
        fun call(userId: String?, deviceId: String?): Int {
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/accounts/demo-0002/balance"))
            userId?.let { request.header("user_id", it) }
            deviceId?.let { request.header("device_id", it) }
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()
        }
        assertEquals(400, call(null, "device-1"))
        assertEquals(400, call("user-1", null))
        assertEquals(400, call(" ", "device-1"))
        assertEquals(400, call("user-1", " "))
    }

    @TestConfiguration(proxyBeanMethods = false)
    class LocalRoutingConfiguration {
        @Bean
        @Primary
        fun localScenarioStore(): ScenarioAssignmentStore = InMemoryScenarioAssignmentStore()

        @Bean
        @Primary
        fun localConfigSource(): ToggleConfigSource = StaticToggleConfigSource(
            listOf(ToggleConfig(BankToggle.ACCOUNT_OPERATIONS, 0)),
        )
    }

    private fun get(path: String) = client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).header("user_id", "user-1").header("device_id", "device-1").GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun post(path: String, body: String) = client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .header("user_id", "user-1").header("device_id", "device-1")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
