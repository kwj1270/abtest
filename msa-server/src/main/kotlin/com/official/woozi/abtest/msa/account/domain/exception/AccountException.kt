package com.official.woozi.abtest.msa.account.domain.exception

enum class AccountError {
    INVALID_AMOUNT,
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_BALANCE,
    BALANCE_LIMIT_EXCEEDED,
}

class AccountException(val code: AccountError, message: String) : RuntimeException(message)
