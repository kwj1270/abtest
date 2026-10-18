package com.official.woozi.abtoggle.config

/** value는 설정 조회와 대상 배정에 사용하므로 변경하지 않는다. */
enum class BankToggle(override val value: String) : ToggleKey {
    ACCOUNT_OPERATIONS("account-operations"),
    TRANSFER("transfer"),
    BALANCE_LOOKUP("balance-lookup"),
}
