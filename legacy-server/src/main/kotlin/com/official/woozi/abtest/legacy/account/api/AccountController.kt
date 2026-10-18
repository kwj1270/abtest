package com.official.woozi.abtest.legacy.account.api

import com.official.woozi.abtest.legacy.account.api.dto.AmountRequest
import com.official.woozi.abtest.legacy.account.api.dto.BalanceResponse
import com.official.woozi.abtest.legacy.web.RequestIdentity
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(private val accounts: AccountRoutingFacade) {
    @GetMapping("/{accountNo}/balance")
    fun balance(
        @PathVariable accountNo: String,
        @RequestHeader("user_id") userId: String,
        @RequestHeader("device_id") deviceId: String,
    ) = BalanceResponse(accountNo, accounts.balance(accountNo, RequestIdentity(userId, deviceId)))

    @PostMapping("/{accountNo}/deposits")
    fun deposit(
        @PathVariable accountNo: String,
        @Valid @RequestBody request: AmountRequest,
        @RequestHeader("user_id") userId: String,
        @RequestHeader("device_id") deviceId: String,
    ) = BalanceResponse(accountNo, accounts.deposit(accountNo, request.amount, RequestIdentity(userId, deviceId)))

    @PostMapping("/{accountNo}/withdrawals")
    fun withdraw(
        @PathVariable accountNo: String,
        @Valid @RequestBody request: AmountRequest,
        @RequestHeader("user_id") userId: String,
        @RequestHeader("device_id") deviceId: String,
    ) = BalanceResponse(accountNo, accounts.withdraw(accountNo, request.amount, RequestIdentity(userId, deviceId)))
}
