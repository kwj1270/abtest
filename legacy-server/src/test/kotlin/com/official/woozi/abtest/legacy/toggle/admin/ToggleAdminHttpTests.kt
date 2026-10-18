package com.official.woozi.abtest.legacy.toggle.admin

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtoggle.redis.RedisToggleConfigSource
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ToggleAdminHttpTests {
    @Value("\${local.server.port}") private var port: Int = 0
    @Autowired private lateinit var redis: StringRedisTemplate
    @Autowired private lateinit var configs: RedisToggleConfigSource
    @Autowired private lateinit var router: ScenarioRouter
    @Autowired private lateinit var json: ObjectMapper
    private val client = HttpClient.newHttpClient()
    private val configKey = "$namespace:config:account-operations:new-percent"

    @Test
    fun `페이지와 자산을 제공하고 누락된 설정은 Redis 쓰기 없이 0퍼센트로 조회한다`() {
        redis.delete(configKey)
        val page = call("/admin/toggles")
        assertEquals(200, page.statusCode())
        assertTrue(page.body().contains("토글 설정"))
        assertEquals(200, call("/admin/toggles/app.js").statusCode())
        assertEquals(200, call("/admin/toggles/styles.css").statusCode())
        val response = call("/admin/api/toggles")
        assertEquals(200, response.statusCode())
        val rows = json.readTree(response.body())
        assertEquals(BankToggle.entries.size, rows.size())
        val account = rows.first { it.path("key").asString() == BankToggle.ACCOUNT_OPERATIONS.value }
        assertEquals(0, account.path("newPercent").asInt())
        assertEquals(100, account.path("legacyPercent").asInt())
        assertTrue(account.path("connected").asBoolean())
        assertFalse(redis.hasKey(configKey))
        assertEquals(Route.LEGACY, ToggleEvaluator(configs).decide(BankToggle.ACCOUNT_OPERATIONS, AssignmentKey.UserId("default")))
    }

    @Test
    fun `저장은 Redis와 업무 평가기에 반영되고 기존 사용자 경로는 유지한다`() {
        assertEquals(200, update("{\"newPercent\":0}").statusCode())
        assertEquals(Route.LEGACY, router.start(BankToggle.ACCOUNT_OPERATIONS,
            com.official.woozi.abtoggle.scenario.ScenarioId("admin-test:old"), AssignmentKey.UserId("user-1")))
        val updated = update("{\"newPercent\":100}")
        assertEquals(200, updated.statusCode())
        assertEquals("100", redis.opsForValue().get(configKey))
        assertEquals(-1L, redis.getExpire(configKey))
        assertEquals(100, json.readTree(updated.body()).path("newPercent").asInt())
        assertEquals(Route.NEW, ToggleEvaluator(configs).decide(BankToggle.ACCOUNT_OPERATIONS, AssignmentKey.UserId("new")))
        assertEquals(Route.LEGACY, router.start(BankToggle.ACCOUNT_OPERATIONS,
            com.official.woozi.abtoggle.scenario.ScenarioId("admin-test:old"), AssignmentKey.UserId("user-1")))
        assertEquals(200, update("{\"newPercent\":25}").statusCode())
        assertEquals("25", redis.opsForValue().get(configKey))
    }

    @Test
    fun `잘못된 비율과 누락된 값은 기존 설정을 변경하지 않는다`() {
        redis.opsForValue().set(configKey, "37")
        listOf("-1", "101", "null", "1.5").forEach {
            assertEquals(400, update("{\"newPercent\":$it}").statusCode(), "value=$it")
            assertEquals("37", redis.opsForValue().get(configKey))
        }
        assertEquals(400, update("{}").statusCode())
        assertEquals(404, call("/admin/api/toggles/unknown", "{\"newPercent\":50}").statusCode())
        assertEquals("37", redis.opsForValue().get(configKey))
    }

    @Test
    fun `외부에서 유효하지 않은 문자열을 저장해도 0퍼센트로 방어한다`() {
        listOf("not-a-number", "-1", "101", "").forEach {
            redis.opsForValue().set(configKey, it)
            assertEquals(0, configs.find(BankToggle.ACCOUNT_OPERATIONS).getOrThrow()?.newPercent)
            assertEquals(Route.LEGACY, ToggleEvaluator(configs).decide(BankToggle.ACCOUNT_OPERATIONS, AssignmentKey.UserId("user")))
        }
    }

    @Test
    fun `Redis 명령 오류를 기본 설정처럼 숨기지 않고 503으로 표시한다`() {
        redis.delete(configKey)
        redis.opsForList().rightPush(configKey, "wrong-type")
        try {
            assertEquals(503, call("/admin/api/toggles").statusCode())
        } finally {
            redis.delete(configKey)
        }
    }

    private fun update(body: String) = call("/admin/api/toggles/account-operations", body)

    private fun call(path: String, body: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
        if (body == null) request.GET()
        else request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body))
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private val namespace = "ab-toggle-admin-test:${UUID.randomUUID()}"
        @Container @JvmField
        val container = GenericContainer<Nothing>(DockerImageName.parse("redis:8.2.1-alpine"))
            .also { it.withExposedPorts(6379) }

        @DynamicPropertySource @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { container.host }
            registry.add("spring.data.redis.port") { container.getMappedPort(6379) }
            registry.add("ab-toggle.redis.config-prefix") { "$namespace:config" }
            registry.add("ab-toggle.redis.key-prefix") { "$namespace:scenario" }
        }
    }
}
