package com.official.woozi.abtest.msa.account.application

import com.official.woozi.abtest.msa.account.domain.exception.AccountError
import com.official.woozi.abtest.msa.account.domain.exception.AccountException
import com.official.woozi.abtest.msa.account.domain.Account
import org.springframework.stereotype.Service

@Service
class AccountService {
    // 등록 목록은 불변이고 각 Account가 자기 잔액의 동시 접근을 보호한다.
    private val accounts = listOf("demo-0001", "demo-0002").associateWith(::Account)

    fun balance(accountNo: String): Long = account(accountNo).balance()
    fun deposit(accountNo: String, amount: Long): Long = account(accountNo).deposit(amount)
    fun withdraw(accountNo: String, amount: Long): Long = account(accountNo).withdraw(amount)

    private fun account(accountNo: String): Account = accounts[accountNo]
        ?: throw AccountException(AccountError.ACCOUNT_NOT_FOUND, "Account not found")
}
