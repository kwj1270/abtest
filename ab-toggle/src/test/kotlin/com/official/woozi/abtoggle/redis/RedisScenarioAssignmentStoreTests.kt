package com.official.woozi.abtoggle.redis

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.exception.ScenarioAssignmentNotFoundException
import com.official.woozi.abtoggle.scenario.ScenarioId
import com.official.woozi.abtoggle.scenario.ScenarioRouter
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class RedisScenarioAssignmentStoreTests {
    private enum class Key(override val value: String) : ToggleKey {
        TRANSFER("transfer"),
        BALANCE("balance-lookup"),
    }

    private lateinit var firstConnection: LettuceConnectionFactory
    private lateinit var secondConnection: LettuceConnectionFactory
    private lateinit var redis: StringRedisTemplate
    private lateinit var first: RedisScenarioAssignmentStore
    private lateinit var second: RedisScenarioAssignmentStore
    private lateinit var prefix: String
    private val id = ScenarioId("신청:123")
    private val ttl = Duration.ofDays(90)

    @BeforeEach
    fun connect() {
        firstConnection = connection()
        secondConnection = connection()
        redis = StringRedisTemplate(firstConnection)
        prefix = "ab-toggle-test:${UUID.randomUUID()}"
        first = RedisScenarioAssignmentStore(redis, prefix)
        second = RedisScenarioAssignmentStore(StringRedisTemplate(secondConnection), prefix)
    }

    @AfterEach
    fun disconnect() {
        firstConnection.destroy()
        secondConnection.destroy()
    }

    @Test
    fun `다른 클라이언트도 최초 경로를 읽고 중복 배정은 값과 TTL을 유지한다`() {
        assertNull(first.find(Key.TRANSFER, id))
        assertEquals(Route.NEW, first.putIfAbsent(Key.TRANSFER, id, Route.NEW, ttl))
        val key = redis.keys("$prefix:*").single()
        val initialTtl = redis.getExpire(key, TimeUnit.MILLISECONDS)
        assertTrue(initialTtl in 1..ttl.toMillis())

        assertEquals(Route.NEW, second.find(Key.TRANSFER, id))
        assertEquals(Route.NEW, second.putIfAbsent(Key.TRANSFER, id, Route.LEGACY, Duration.ofDays(180)))
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) in 1..initialTtl)
    }

    @Test
    fun `동시에 반대 경로를 요청해도 모든 클라이언트가 같은 최초 배정을 반환한다`() {
        val count = 16
        val ready = CountDownLatch(count)
        val go = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(count)
        try {
            val requests = (0 until count).map { index ->
                executor.submit(Callable {
                    ready.countDown()
                    check(go.await(10, TimeUnit.SECONDS))
                    if (index % 2 == 0) first.putIfAbsent(Key.TRANSFER, id, Route.NEW, ttl)
                    else second.putIfAbsent(Key.TRANSFER, id, Route.LEGACY, ttl)
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            go.countDown()
            val routes = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, routes.toSet().size)
            assertEquals(routes.first(), second.find(Key.TRANSFER, id))
        } finally {
            go.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `서로 다른 라우터의 비율이 달라도 기존 시나리오는 유지한다`() {
        val oldRouter = ScenarioRouter(ToggleEvaluator(StaticToggleConfigSource(listOf(ToggleConfig(Key.TRANSFER, 0)))), first)
        val newRouter = ScenarioRouter(ToggleEvaluator(StaticToggleConfigSource(listOf(ToggleConfig(Key.TRANSFER, 100)))), second)
        assertEquals(Route.LEGACY, oldRouter.start(Key.TRANSFER, id, AssignmentKey.UserId("1")))
        assertEquals(Route.LEGACY, newRouter.start(Key.TRANSFER, id, AssignmentKey.UserId("1")))
        assertEquals(Route.LEGACY, newRouter.requireRoute(Key.TRANSFER, id))
        assertEquals(Route.NEW, newRouter.start(Key.TRANSFER, ScenarioId("new"), AssignmentKey.UserId("1")))
    }

    @Test
    fun `만료된 시나리오의 후속 조회는 재배정하지 않는다`() {
        first.putIfAbsent(Key.TRANSFER, id, Route.NEW, ttl)
        val key = redis.keys("$prefix:*").single()
        redis.expireAt(key, Instant.EPOCH)
        assertNull(second.find(Key.TRANSFER, id))
        val router = ScenarioRouter(ToggleEvaluator(StaticToggleConfigSource(emptyList())), second)
        assertFailsWith<ScenarioAssignmentNotFoundException> { router.requireRoute(Key.TRANSFER, id) }
    }

    @Test
    fun `토글과 시나리오 및 namespace가 다르면 독립적으로 저장한다`() {
        val otherId = ScenarioId("신청:124")
        first.putIfAbsent(Key.TRANSFER, id, Route.NEW, ttl)
        first.putIfAbsent(Key.BALANCE, id, Route.LEGACY, ttl)
        first.putIfAbsent(Key.TRANSFER, otherId, Route.LEGACY, ttl)
        val otherNamespace = RedisScenarioAssignmentStore(redis, "$prefix:other")
        assertEquals(Route.LEGACY, otherNamespace.putIfAbsent(Key.TRANSFER, id, Route.LEGACY, ttl))
        assertEquals(Route.NEW, second.find(Key.TRANSFER, id))
        assertEquals(Route.LEGACY, second.find(Key.BALANCE, id))
        assertEquals(Route.LEGACY, second.find(Key.TRANSFER, otherId))
    }

    @Test
    fun `잘못된 저장 값과 Redis 오류는 누락으로 처리하거나 덮어쓰지 않는다`() {
        first.putIfAbsent(Key.TRANSFER, id, Route.NEW, ttl)
        val key = redis.keys("$prefix:*").single()
        redis.opsForValue().set(key, "BROKEN")
        assertFailsWith<IllegalArgumentException> { second.find(Key.TRANSFER, id) }
        assertFailsWith<IllegalArgumentException> { second.putIfAbsent(Key.TRANSFER, id, Route.LEGACY, ttl) }
        assertEquals("BROKEN", redis.opsForValue().get(key))

        redis.delete(key)
        redis.opsForList().rightPush(key, "wrong-type")
        assertFailsWith<org.springframework.dao.DataAccessException> { second.find(Key.TRANSFER, id) }
        assertFailsWith<org.springframework.dao.DataAccessException> {
            second.putIfAbsent(Key.TRANSFER, id, Route.LEGACY, ttl)
        }
    }

    @Test
    fun `밀리초 미만 TTL과 오버플로는 저장 전에 거부한다`() {
        listOf(Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(1)).forEach {
            assertFailsWith<IllegalArgumentException> { first.putIfAbsent(Key.TRANSFER, id, Route.NEW, it) }
        }
        assertFailsWith<ArithmeticException> {
            first.putIfAbsent(Key.TRANSFER, id, Route.NEW, Duration.ofSeconds(Long.MAX_VALUE))
        }
        assertNull(first.find(Key.TRANSFER, id))
    }

    private fun connection(): LettuceConnectionFactory =
        LettuceConnectionFactory(container.host, container.getMappedPort(6379)).also {
            it.afterPropertiesSet()
            it.start()
        }

    companion object {
        @Container
        @JvmField
        val container = GenericContainer<Nothing>(DockerImageName.parse("redis:8.2.1-alpine"))
            .also { it.withExposedPorts(6379) }
    }
}
