package com.official.woozi.abtoggle.assignment

/** 업무가 선택한 배정 기준. 헤더 추출과 인증 검증은 호출자가 담당한다. */
sealed interface AssignmentKey {
    val value: String

    data class AccountId(override val value: String) : AssignmentKey {
        init {
            require(value.isNotBlank()) { "AccountId must not be blank" }
        }
    }

    data class UserId(override val value: String) : AssignmentKey {
        init {
            require(value.isNotBlank()) { "UserId must not be blank" }
        }
    }
    data class DeviceId(override val value: String) : AssignmentKey {
        init {
            require(value.isNotBlank()) { "DeviceId must not be blank" }
        }
    }
}
