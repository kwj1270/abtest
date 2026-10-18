package com.official.woozi.abtoggle.config

internal enum class TestToggleKey(override val value: String) : ToggleKey {
    TRANSFER("transfer"),
    TRANSFER_MIGRATION("transfer-migration"),
    UNKNOWN("unknown"),
    OTHER("other-toggle"),
    BLANK(" "),
    INVALID("a:b"),
}
