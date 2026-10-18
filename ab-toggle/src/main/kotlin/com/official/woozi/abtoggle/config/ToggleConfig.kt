package com.official.woozi.abtoggle.config

/** 어드민에서 관리하는 업무별 신규 배정 비율(0~100%). */
data class ToggleConfig(val key: ToggleKey, val newPercent: Int) {
    init {
        require(key.value.isNotBlank()) { "key must not be blank" }
        require(':' !in key.value) { "key must not contain ':'" }
        require(newPercent in 0..100) { "newPercent must be in 0..100" }
    }
}
