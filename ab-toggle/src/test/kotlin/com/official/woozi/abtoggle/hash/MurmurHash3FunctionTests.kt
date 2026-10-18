package com.official.woozi.abtoggle.hash

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.assignment.AssignmentKey
import com.official.woozi.abtoggle.assignment.ToggleEvaluator
import com.official.woozi.abtoggle.config.StaticToggleConfigSource
import com.official.woozi.abtoggle.config.TestToggleKey
import com.official.woozi.abtoggle.config.ToggleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MurmurHash3FunctionTests {
    @Test
    fun `x64 128비트 seed 0의 h1 테스트 벡터`() {
        val vectors = mapOf(
            "" to 0uL,
            "foo" to 0xe271865701f54561uL,
            "hello" to 0xcbd8a7b341bd9b02uL,
        )
        vectors.forEach { (input, expected) ->
            assertEquals(expected, MurmurHash3Function.hash(input.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `기본 평가기는 MurmurHash3 배정 결과를 사용한다`() {
        val source = StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, 50)))
        val defaultEvaluator = ToggleEvaluator(source)
        val explicitEvaluator = ToggleEvaluator(source, hashFunction = MurmurHash3Function)
        (1..100).forEach {
            val key = AssignmentKey.UserId("user-$it")
            assertEquals(
                explicitEvaluator.decide(TestToggleKey.TRANSFER, key),
                defaultEvaluator.decide(TestToggleKey.TRANSFER, key),
            )
        }
    }

    @Test
    fun `MurmurHash를 주입하면 동일 대상의 경로를 유지하고 비율대로 분산한다`() {
        val evaluator = ToggleEvaluator(
            StaticToggleConfigSource(listOf(ToggleConfig(TestToggleKey.TRANSFER, 10))),
            hashFunction = MurmurHash3Function,
        )
        val subjects = (1..10_000).map { AssignmentKey.UserId("사용자-$it") }
        val routes = subjects.map { evaluator.decide(TestToggleKey.TRANSFER, it) }
        assertEquals(routes, subjects.map { evaluator.decide(TestToggleKey.TRANSFER, it) })
        assertTrue(routes.count { it == Route.NEW } in 900..1100)
    }
}
