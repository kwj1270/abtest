package com.official.woozi.abtest.legacy.account.client

import com.official.woozi.abtest.legacy.web.RequestIdentity
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** 신규 서버 오류 시 재시도하거나 LEGACY에서 다시 실행하지 않는다. */
@Component
class MsaAccountClient(@Qualifier("msaAccountRestClient") private val client: RestClient) {
    fun balance(accountNo: String, identity: RequestIdentity): Long = client.get().uri("/accounts/{accountNo}/balance", accountNo)
        .header("user_id", identity.userId.value).header("device_id", identity.deviceId.value)
        .retrieve().body(MsaBalanceResponse::class.java).validatedAmount(accountNo)

    fun deposit(accountNo: String, amount: Long, identity: RequestIdentity): Long = client.post().uri("/accounts/{accountNo}/deposits", accountNo)
        .header("user_id", identity.userId.value).header("device_id", identity.deviceId.value)
        .body(MsaAmountRequest(amount)).retrieve().body(MsaBalanceResponse::class.java).validatedAmount(accountNo)

    fun withdraw(accountNo: String, amount: Long, identity: RequestIdentity): Long = client.post().uri("/accounts/{accountNo}/withdrawals", accountNo)
        .header("user_id", identity.userId.value).header("device_id", identity.deviceId.value)
        .body(MsaAmountRequest(amount)).retrieve().body(MsaBalanceResponse::class.java).validatedAmount(accountNo)

    private fun MsaBalanceResponse?.validatedAmount(accountNo: String): Long {
        if (this == null || this.accountNo != accountNo || amount < 0) {
            throw RestClientException("Invalid MSA balance response")
        }
        return amount
    }

    private data class MsaAmountRequest(val amount: Long)
    private data class MsaBalanceResponse(val accountNo: String, val amount: Long)
}
