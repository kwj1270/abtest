package com.official.woozi.abtest.msa.account.api.exception

/** HTTP 상태와 함께 클라이언트가 분기할 수 있는 안정적인 오류 코드. */
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)
