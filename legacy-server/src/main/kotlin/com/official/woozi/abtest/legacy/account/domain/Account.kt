package com.official.woozi.abtest.legacy.account.domain

/** 잔액 변경 규칙과 계좌 단위 원자성을 보장한다. 금액 단위는 원이다. */
class Account(val number: String) {
    init {
        require(number.isNotBlank()) { "Account number must not be blank" }
    }

    private var currentBalance = 0L

    @Synchronized
    fun balance(): Long = currentBalance

    @Synchronized
    fun deposit(amount: Long): Long {
        validateAmount(amount)
        if (amount > Long.MAX_VALUE - currentBalance) {
            throw AccountException(AccountError.BALANCE_LIMIT_EXCEEDED, "Balance limit exceeded")
        }
        currentBalance += amount
        return currentBalance
    }

    @Synchronized
    fun withdraw(amount: Long): Long {
        validateAmount(amount)
        if (amount > currentBalance) {
            throw AccountException(AccountError.INSUFFICIENT_BALANCE, "Insufficient balance")
        }
        currentBalance -= amount
        return currentBalance
    }

    private fun validateAmount(amount: Long) {
        if (amount <= 0) throw AccountException(AccountError.INVALID_AMOUNT, "Amount must be positive")
    }
}
