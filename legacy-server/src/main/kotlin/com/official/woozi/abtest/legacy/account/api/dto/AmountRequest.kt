package com.official.woozi.abtest.legacy.account.api.dto

import jakarta.validation.constraints.Positive

data class AmountRequest(@field:Positive(message = "Amount must be positive") val amount: Long)
