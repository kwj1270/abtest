package com.official.woozi.abtest.legacy.account

import com.official.woozi.abtest.legacy.account.application.AccountService
import com.official.woozi.abtest.msa.MsaServerApplication
import org.junit.jupiter.api.AfterAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["bank.balance-shadow.enabled=true"],
)
class AccountRoutingHttpTests {
    @Value("\${local.server.port}") private var port: Int = 0
    @Autowired private lateinit var redis: StringRedisTemplate
    @Autowired private lateinit var legacy: AccountService
    private val client = HttpClient.newHttpClient()

    @Test
    fun `같은 API에서 서버가 자동 배정하고 사용자별로 입출금 조회 경로를 유지한다`() {
        redis.opsForValue().set(configKey, "0")
        assertEquals(200, request("/accounts/demo-0001/deposits", "{\"amount\":10000}").statusCode())
        assertEquals(10000L, legacy.balance("demo-0001"))

        redis.opsForValue().set(configKey, "100")
        assertEquals(200, request("/accounts/demo-0001/withdrawals", "{\"amount\":3000}", deviceId = "device-2").statusCode())
        assertTrue(request("/accounts/demo-0001/balance").body().contains("\"amount\":7000"))
        assertTrue(request("/accounts/demo-0002/balance", deviceId = "device-2").body().contains("\"amount\":0"))
        val deposit = request("/accounts/demo-0002/deposits", "{\"amount\":5000}", userId = "user-2")
        assertEquals(200, deposit.statusCode())
        assertTrue(deposit.body().contains("\"amount\":5000"))
        assertEquals(0L, legacy.balance("demo-0002"))

        redis.opsForValue().set(configKey, "0")
        assertEquals(200, request("/accounts/demo-0002/withdrawals", "{\"amount\":2000}", userId = "user-2").statusCode())
        assertTrue(request("/accounts/demo-0002/balance", userId = "user-2", deviceId = "device-3").body().contains("\"amount\":3000"))
        val rejected = request("/accounts/demo-0002/withdrawals", "{\"amount\":3001}", userId = "user-2")
        assertEquals(409, rejected.statusCode())
        assertTrue(rejected.body().contains("INSUFFICIENT_BALANCE"))
        assertEquals(0L, legacy.balance("demo-0002"))
        assertEquals(7000L, legacy.balance("demo-0001"))
        assertEquals(404, request("/accounts/missing/balance").statusCode())
        assertEquals(404, request("/account-scenarios", "{\"scenarioId\":\"unused\"}").statusCode())

        val remoteBalance = client.send(
            HttpRequest.newBuilder(URI.create("${newUrl()}/accounts/demo-0002/balance")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertTrue(remoteBalance.body().contains("\"amount\":3000"))
    }

    private fun request(path: String, body: String? = null, userId: String = "user-1", deviceId: String = "device-1"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .header("user_id", userId).header("device_id", deviceId)
        if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        else request.GET()
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private val namespace = "ab-toggle-http-test:${UUID.randomUUID()}"
        private val configKey = "$namespace:config:account-operations:new-percent"
        private var newServer: ConfigurableApplicationContext? = null

        @Container
        @JvmField
        val container = GenericContainer<Nothing>(DockerImageName.parse("redis:8.2.1-alpine"))
            .also { it.withExposedPorts(6379) }

        @Synchronized
        private fun newUrl(): String {
            val context = newServer ?: SpringApplicationBuilder(MsaServerApplication::class.java).run(
                "--server.port=0",
                "--spring.data.redis.host=${container.host}",
                "--spring.data.redis.port=${container.getMappedPort(6379)}",
            ).also { newServer = it }
            return "http://127.0.0.1:${context.environment.getRequiredProperty("local.server.port")}" 
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { container.host }
            registry.add("spring.data.redis.port") { container.getMappedPort(6379) }
            registry.add("ab-toggle.redis.key-prefix") { "$namespace:scenario" }
            registry.add("ab-toggle.redis.config-prefix") { "$namespace:config" }
            registry.add("bank.msa-server.base-url") { newUrl() }
        }

        @AfterAll
        @JvmStatic
        fun closeNewServer() { newServer?.close() }
    }
}
