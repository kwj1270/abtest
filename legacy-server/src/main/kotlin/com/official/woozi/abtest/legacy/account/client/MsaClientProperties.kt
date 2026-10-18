package com.official.woozi.abtest.legacy.account.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("bank.msa-server")
data class MsaClientProperties(
    val baseUrl: URI = URI("http://127.0.0.1:8081"),
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(3),
) {
    init {
        require(baseUrl.scheme in setOf("http", "https") && baseUrl.host != null) { "MSA base URL must be HTTP(S)" }
        require(!connectTimeout.isNegative && connectTimeout.toMillis() > 0) { "connectTimeout must be positive" }
        require(!readTimeout.isNegative && readTimeout.toMillis() > 0) { "readTimeout must be positive" }
    }
}
