package com.official.woozi.abtest.msa.account

import com.official.woozi.abtest.msa.account.application.AccountService
import com.official.woozi.abtest.msa.account.domain.exception.AccountError
import com.official.woozi.abtest.msa.account.domain.exception.AccountException
import java.util.concurrent.Executors
import kotlin.test.*

class AccountServiceTests {
    private val accounts = AccountService()

    @Test
    fun `deposit withdraw and balance`() {
        assertEquals(0L, accounts.balance("demo-0001"))
        assertEquals(10_000L, accounts.deposit("demo-0001", 10_000))
        assertEquals(7_000L, accounts.withdraw("demo-0001", 3_000))
        assertEquals(0L, accounts.balance("demo-0002"))
        assertEquals(0L, accounts.withdraw("demo-0001", 7_000))
    }

    @Test
    fun `invalid amounts and insufficient funds do not change balance`() {
        accounts.deposit("demo-0001", 100)
        for (amount in listOf(0L, -1L)) {
            assertEquals(AccountError.INVALID_AMOUNT, assertFailsWith<AccountException> { accounts.deposit("demo-0001", amount) }.code)
            assertEquals(AccountError.INVALID_AMOUNT, assertFailsWith<AccountException> { accounts.withdraw("demo-0001", amount) }.code)
        }
        assertEquals(AccountError.INSUFFICIENT_BALANCE, assertFailsWith<AccountException> { accounts.withdraw("demo-0001", 101) }.code)
        assertEquals(100L, accounts.balance("demo-0001"))
    }

    @Test
    fun `unknown account is not implicitly created`() {
        assertFailsWith<AccountException> { accounts.balance("missing") }
        assertFailsWith<AccountException> { accounts.deposit("missing", 1) }
        assertFailsWith<AccountException> { accounts.withdraw("missing", 1) }
    }

    @Test
    fun `overflow is rejected without changing balance`() {
        accounts.deposit("demo-0001", Long.MAX_VALUE)
        assertEquals(AccountError.BALANCE_LIMIT_EXCEEDED, assertFailsWith<AccountException> { accounts.deposit("demo-0001", 1) }.code)
        assertEquals(Long.MAX_VALUE, accounts.balance("demo-0001"))
    }

    @Test
    fun `concurrent updates do not lose deposits or overdraw`() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val deposits = (1..100).map { pool.submit { accounts.deposit("demo-0001", 1) } }
            deposits.forEach { it.get() }
            assertEquals(100L, accounts.balance("demo-0001"))
            val withdrawals = (1..200).map {
                pool.submit<Boolean> {
                    try { accounts.withdraw("demo-0001", 1); true }
                    catch (e: AccountException) {
                        assertEquals(AccountError.INSUFFICIENT_BALANCE, e.code)
                        false
                    }
                }
            }
            assertEquals(100, withdrawals.count { it.get() })
            assertEquals(0L, accounts.balance("demo-0001"))
        } finally { pool.shutdownNow() }
    }
}
