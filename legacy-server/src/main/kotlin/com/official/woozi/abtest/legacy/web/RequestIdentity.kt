package com.official.woozi.abtest.legacy.web

import com.official.woozi.abtoggle.assignment.AssignmentKey

/** user_id는 배정 기준이고 device_id는 요청 문맥으로 MSA에 전달한다. */
data class RequestIdentity(
    val userId: AssignmentKey.UserId,
    val deviceId: AssignmentKey.DeviceId,
) {
    constructor(userId: String, deviceId: String) : this(
        AssignmentKey.UserId(userId),
        AssignmentKey.DeviceId(deviceId),
    )
}
