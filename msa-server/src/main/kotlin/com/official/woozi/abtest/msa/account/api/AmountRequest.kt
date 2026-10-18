package com.official.woozi.abtest.msa.account.api

import jakarta.validation.constraints.Positive

data class AmountRequest(@field:Positive(message = "Amount must be positive") val amount: Long)
