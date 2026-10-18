package com.official.woozi.abtoggle.assignment

import com.official.woozi.abtoggle.Route
import com.official.woozi.abtoggle.Route.LEGACY
import com.official.woozi.abtoggle.Route.NEW
import com.official.woozi.abtoggle.assignment.AssignmentKey.DeviceId
import com.official.woozi.abtoggle.assignment.AssignmentKey.UserId
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleKey
import com.official.woozi.abtoggle.hash.HashFunction
import com.official.woozi.abtoggle.hash.MurmurHash3Function
import java.util.logging.Level.WARNING
import java.util.logging.Logger

/** 현재 비율로 경로를 결정한다. 실제 업무 실행은 호출자가 담당한다. */
class ToggleEvaluator(
    private val source: ToggleConfigSource,
    private val hashFunction: HashFunction = MurmurHash3Function,
) {
    private val logger = Logger.getLogger(ToggleEvaluator::class.java.name)

    fun decide(toggleKey: ToggleKey, assignmentKey: AssignmentKey): Route {
        val config = toggleConfig(toggleKey) ?: return LEGACY
        val assignmentKeyName = assignmentKeyValue(assignmentKey)
        val hashValue = hashFunction.hash(
            "${config.key.value}:$assignmentKeyName:${assignmentKey.value}".toByteArray(Charsets.UTF_8),
        )
        val bucket = (hashValue % 10_000uL).toInt()
        return if (bucket < config.newPercent * 100) NEW else LEGACY
    }

    private fun assignmentKeyValue(assignmentKey: AssignmentKey): String = when (assignmentKey) {
        is AssignmentKey.AccountId -> "account_id"
        is UserId -> "user_id"
        is DeviceId -> "device_id"
    }

    private fun toggleConfig(toggleKey: ToggleKey): ToggleConfig? = source.find(toggleKey)
        .onFailure {
            logger.log(WARNING, "Toggle config unavailable: toggleKey=${toggleKey.value}; using LEGACY", it)
        }
        .getOrNull()
}
