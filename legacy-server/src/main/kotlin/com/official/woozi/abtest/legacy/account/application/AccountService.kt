package com.official.woozi.abtest.legacy.account.application

import com.official.woozi.abtest.legacy.account.domain.Account
import com.official.woozi.abtest.legacy.account.domain.AccountError
import com.official.woozi.abtest.legacy.account.domain.AccountException
import org.springframework.stereotype.Service

@Service
class AccountService {
    private val accounts = listOf("demo-0001", "demo-0002").associateWith(::Account)

    fun balance(accountNo: String): Long =
        account(accountNo).balance()

    fun deposit(accountNo: String, amount: Long): Long =
        account(accountNo).deposit(amount)

    fun withdraw(accountNo: String, amount: Long): Long =
        account(accountNo).withdraw(amount)

    private fun account(accountNo: String): Account = accounts[accountNo]
        ?: throw AccountException(AccountError.ACCOUNT_NOT_FOUND, "Account not found")
}
