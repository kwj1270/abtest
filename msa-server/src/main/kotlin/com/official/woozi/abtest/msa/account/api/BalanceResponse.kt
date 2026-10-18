package com.official.woozi.abtest.msa.account.api

// Amounts are integer KRW.
data class BalanceResponse(val accountNo: String, val amount: Long)
