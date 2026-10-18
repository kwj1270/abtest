package com.official.woozi.abtest.msa.account.api

import com.official.woozi.abtest.msa.account.application.AccountService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/accounts")
class AccountController(private val accounts: AccountService) {
    @GetMapping("/{accountNo}/balance")
    fun balance(@PathVariable accountNo: String) =
        BalanceResponse(accountNo, accounts.balance(accountNo))

    @PostMapping("/{accountNo}/deposits")
    fun deposit(@PathVariable accountNo: String, @Valid @RequestBody request: AmountRequest) =
        BalanceResponse(accountNo, accounts.deposit(accountNo, request.amount))

    @PostMapping("/{accountNo}/withdrawals")
    fun withdraw(@PathVariable accountNo: String, @Valid @RequestBody request: AmountRequest) =
        BalanceResponse(accountNo, accounts.withdraw(accountNo, request.amount))

}
