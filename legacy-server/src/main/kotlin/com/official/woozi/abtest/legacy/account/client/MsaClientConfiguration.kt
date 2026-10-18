package com.official.woozi.abtest.legacy.account.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MsaClientProperties::class)
class MsaClientConfiguration {
    @Bean
    fun msaAccountRestClient(properties: MsaClientProperties): RestClient {
        val httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(properties.readTimeout) }
        return RestClient.builder().baseUrl(properties.baseUrl.toString()).requestFactory(factory).build()
    }
}
