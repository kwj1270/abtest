package com.official.woozi.abtoggle.assignment

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.config.TestToggleKey
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.hash.HashFunction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToggleEvaluatorTests {
    private val ids = (1..10_000).map { AssignmentKey.UserId("user-$it") }

    private fun evaluator(percent: Int) = ToggleEvaluator(
        StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, percent))),
    )

    @Test
    fun `같은 대상의 경로는 고정되고 비율을 올리면 기존 신규 대상을 포함한다`() {
        val at10 = evaluator(10)
        val at20 = evaluator(20)
        val original = ids.filter { at10.decide(TestToggleKey.TRANSFER, it) == Route.NEW }.toSet()
        val expanded = ids.filter { at20.decide(TestToggleKey.TRANSFER, it) == Route.NEW }.toSet()
        assertTrue(expanded.containsAll(original))
        assertTrue(expanded.size > original.size)
        assertTrue(original.size in 900..1100)
        ids.take(100).forEach {
            assertEquals(at10.decide(TestToggleKey.TRANSFER, it), at10.decide(TestToggleKey.TRANSFER, it))
        }
    }

    @Test
    fun `0퍼센트는 모두 기존 100퍼센트는 모두 신규`() {
        val zero = evaluator(0)
        val full = evaluator(100)
        ids.forEach {
            assertEquals(Route.LEGACY, zero.decide(TestToggleKey.TRANSFER, it))
            assertEquals(Route.NEW, full.decide(TestToggleKey.TRANSFER, it))
        }
    }

    @Test
    fun `주입한 해시로 비율 경계를 판단한다`() {
        var value = 999uL
        val evaluator = ToggleEvaluator(
            StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, 10))),
            hashFunction = HashFunction { input ->
                assertEquals("transfer:user_id:user-1", input.toString(Charsets.UTF_8))
                value
            },
        )
        assertEquals(Route.NEW, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.UserId("user-1")))
        value = 1000uL
        assertEquals(Route.LEGACY, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.UserId("user-1")))
        value = ULong.MAX_VALUE // 부호 없는 나머지 1615
        assertEquals(Route.LEGACY, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.UserId("user-1")))
    }

    @Test
    fun `같은 문자열도 사용자 기기 계좌 배정 키를 구분한다`() {
        val inputs = mutableListOf<String>()
        val evaluator = ToggleEvaluator(
            StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, 50))),
            hashFunction = HashFunction {
                val input = it.toString(Charsets.UTF_8)
                inputs.add(input)
                if (input == "transfer:user_id:123") 0uL else 9999uL
            },
        )
        assertEquals(Route.NEW, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.UserId("123")))
        assertEquals(Route.LEGACY, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.DeviceId("123")))
        evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.AccountId("123"))
        assertEquals(listOf("transfer:user_id:123", "transfer:device_id:123", "transfer:account_id:123"), inputs)
    }

    @Test
    fun `배정 키 생성 시 빈 값과 공백을 거부한다`() {
        listOf("", " ", "\t\n").forEach {
            assertFailsWith<IllegalArgumentException> { AssignmentKey.UserId(it) }
            assertFailsWith<IllegalArgumentException> { AssignmentKey.DeviceId(it) }
            assertFailsWith<IllegalArgumentException> { AssignmentKey.AccountId(it) }
        }
        assertEquals("123", AssignmentKey.UserId("123").value)
        assertEquals("123", AssignmentKey.DeviceId("123").value)
    }

    @Test
    fun `설정이 없거나 조회 실패하면 기존 경로로 처리한다`() {
        assertEquals(Route.LEGACY, evaluator(100).decide(TestToggleKey.UNKNOWN, AssignmentKey.UserId("user-1")))
        val evaluator = ToggleEvaluator(ToggleConfigSource { Result.failure(IllegalStateException("store down")) })
        assertEquals(Route.LEGACY, evaluator.decide(TestToggleKey.TRANSFER, AssignmentKey.UserId("user-1")))
    }

    @Test
    fun `설정 조회는 성공 누락 실패를 Result로 구분한다`() {
        val config = ToggleConfig(TestToggleKey.TRANSFER, 30)
        val source = StaticToggleConfigSource(listOf(config))
        assertEquals(Result.success(config), source.find(TestToggleKey.TRANSFER))
        val missing = source.find(TestToggleKey.UNKNOWN)
        assertTrue(missing.isSuccess)
        assertEquals(null, missing.getOrNull())

        val error = IllegalStateException("store down")
        val failing = ToggleConfigSource { Result.failure(error) }
        assertEquals(error, failing.find(TestToggleKey.TRANSFER).exceptionOrNull())
    }

    private enum class RenamedKey(override val value: String) : ToggleKey {
        RENAMED_TRANSFER("transfer"),
    }

    @Test
    fun `enum 이름이 달라도 저장 키가 같으면 같은 설정과 배정을 사용한다`() {
        val evaluator = evaluator(30)
        ids.take(100).forEach {
            assertEquals(
                evaluator.decide(TestToggleKey.TRANSFER, it),
                evaluator.decide(RenamedKey.RENAMED_TRANSFER, it),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StaticToggleConfigSource(listOf(
                ToggleConfig(TestToggleKey.TRANSFER, 10),
                ToggleConfig(RenamedKey.RENAMED_TRANSFER, 20),
            ))
        }
    }

    @Test
    fun `잘못된 비율과 이름 및 중복 설정을 거부한다`() {
        listOf(-1, 101).forEach {
            assertFailsWith<IllegalArgumentException> { ToggleConfig(TestToggleKey.TRANSFER, it) }
        }
        listOf(TestToggleKey.BLANK, TestToggleKey.INVALID).forEach {
            assertFailsWith<IllegalArgumentException> { ToggleConfig(it, 10) }
        }
        assertFailsWith<IllegalArgumentException> {
            StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, 10), ToggleConfig(TestToggleKey.TRANSFER, 20)))
        }
    }
}
