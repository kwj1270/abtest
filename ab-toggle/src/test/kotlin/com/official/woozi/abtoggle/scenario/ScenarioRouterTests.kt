package com.official.woozi.abtoggle.scenario

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.TestToggleKey
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.exception.ScenarioAssignmentNotFoundException
import com.official.woozi.abtoggle.hash.HashFunction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScenarioRouterTests {
    private var config = ToggleConfig(
        key = TestToggleKey.TRANSFER_MIGRATION,
        newPercent = 0,
    )
    private var evaluations = 0
    private val evaluator = ToggleEvaluator(ToggleConfigSource {
        evaluations++
        Result.success(config)
    })
    private val ttl = Duration.ofMinutes(30)

    @Test
    fun `비율 변경은 신규 시나리오에만 반영하고 재시작은 최초 결정을 반환한다`() {
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(), ttl)
        val original = router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1"))
        config = config.copy(newPercent = 100)

        assertEquals(Route.LEGACY, router.requireRoute(config.key, ScenarioId("1")))
        assertEquals(original, router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("other-user")))
        assertEquals(original, router.requireRoute(config.key, ScenarioId("1")))
        assertEquals(1, evaluations)
        assertEquals(Route.NEW, router.start(config.key, ScenarioId("2"), AssignmentKey.UserId("user-1")))
    }

    @Test
    fun `비율을 0으로 내려도 진행 중인 신규 시나리오는 유지한다`() {
        config = config.copy(newPercent = 100)
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(), ttl)
        router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1"))
        config = config.copy(newPercent = 0)

        assertEquals(Route.NEW, router.requireRoute(config.key, ScenarioId("1")))
        assertEquals(Route.LEGACY, router.start(config.key, ScenarioId("2"), AssignmentKey.UserId("user-1")))
    }

    @Test
    fun `조회와 중복 시작은 TTL을 연장하지 않고 만료 시 재배정하지 않는다`() {
        val clock = MutableClock()
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(clock), ttl)
        router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1"))
        clock.now = clock.now.plus(Duration.ofMinutes(29))
        router.requireRoute(config.key, ScenarioId("1"))
        router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1"))
        clock.now = clock.now.plus(Duration.ofMinutes(1))

        assertFailsWith<ScenarioAssignmentNotFoundException> { router.requireRoute(config.key, ScenarioId("1")) }
        assertFailsWith<ScenarioAssignmentNotFoundException> { router.requireRoute(config.key, ScenarioId("unknown")) }
        assertEquals(1, evaluations)
    }

    @Test
    fun `기본 TTL은 최초 배정부터 90일이다`() {
        val clock = MutableClock()
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(clock))
        router.start(config.key, ScenarioId("1"), AssignmentKey.DeviceId("device-1"))
        clock.now = clock.now.plus(Duration.ofDays(89))
        assertEquals(Route.LEGACY, router.requireRoute(config.key, ScenarioId("1")))
        router.start(config.key, ScenarioId("1"), AssignmentKey.DeviceId("device-1"))
        clock.now = clock.now.plus(Duration.ofDays(1))
        assertFailsWith<ScenarioAssignmentNotFoundException> { router.requireRoute(config.key, ScenarioId("1")) }
    }

    @Test
    fun `다른 토글은 같은 시나리오 ID라도 독립된 경로를 가진다`() {
        val router = ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(), ttl)
        router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1"))
        val originalKey = config.key
        config = ToggleConfig(TestToggleKey.OTHER, 100)
        assertEquals(Route.NEW, router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1")))
        assertEquals(Route.LEGACY, router.requireRoute(originalKey, ScenarioId("1")))
        assertEquals(Route.NEW, router.requireRoute(config.key, ScenarioId("1")))
    }

    @Test
    fun `저장소 장애 시 시작과 후속 조회 모두 실패한다`() {
        val store = object : ScenarioAssignmentStore {
            override fun find(toggleKey: ToggleKey, scenarioId: ScenarioId): Route? = error("store unavailable")
            override fun putIfAbsent(toggleKey: ToggleKey, scenarioId: ScenarioId, route: Route, ttl: Duration): Route =
                error("store unavailable")
        }
        val router = ScenarioRouter(evaluator, store, ttl)
        assertFailsWith<IllegalStateException> { router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1")) }
        assertFailsWith<IllegalStateException> { router.requireRoute(config.key, ScenarioId("1")) }
        assertEquals(0, evaluations)
    }

    @Test
    fun `저장에 실패하면 최초 결정도 반환하지 않는다`() {
        val store = object : ScenarioAssignmentStore {
            override fun find(toggleKey: ToggleKey, scenarioId: ScenarioId): Route? = null
            override fun putIfAbsent(toggleKey: ToggleKey, scenarioId: ScenarioId, route: Route, ttl: Duration): Route =
                error("write failed")
        }
        val router = ScenarioRouter(evaluator, store, ttl)
        assertFailsWith<IllegalStateException> { router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("user-1")) }
    }

    @Test
    fun `경쟁하는 시작 요청은 서로 다른 후보를 계산해도 같은 결정을 받는다`() {
        val barrier = CyclicBarrier(2)
        val concurrentEvaluator = ToggleEvaluator(
            ToggleConfigSource {
                barrier.await(5, TimeUnit.SECONDS)
                Result.success(config.copy(newPercent = 50))
            },
            hashFunction = HashFunction { input ->
                if (input.toString(Charsets.UTF_8).endsWith(":new-user")) 0uL else 9999uL
            },
        )
        val router = ScenarioRouter(concurrentEvaluator, InMemoryScenarioAssignmentStore(), ttl)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(Callable { router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("new-user")) })
            val second = executor.submit(Callable { router.start(config.key, ScenarioId("1"), AssignmentKey.UserId("legacy-user")) })
            val decision = first.get(10, TimeUnit.SECONDS)
            assertEquals(decision, second.get(10, TimeUnit.SECONDS))
            assertEquals(decision, router.requireRoute(config.key, ScenarioId("1")))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `유효하지 않은 TTL과 시나리오 키는 거부한다`() {
        listOf(Duration.ZERO, Duration.ofSeconds(-1)).forEach {
            assertFailsWith<IllegalArgumentException> {
                ScenarioRouter(evaluator, InMemoryScenarioAssignmentStore(), it)
            }
        }
        listOf("", " ", "\t\n").forEach {
            assertFailsWith<IllegalArgumentException> { ScenarioId(it) }
        }
        assertEquals("transfer-123", ScenarioId("transfer-123").value)
    }

    private class MutableClock(var now: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
