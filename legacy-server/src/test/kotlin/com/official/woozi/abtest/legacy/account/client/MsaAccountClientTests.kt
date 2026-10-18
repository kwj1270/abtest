package com.official.woozi.abtest.legacy.account.client

import com.official.woozi.abtest.legacy.web.RequestIdentity
import com.sun.net.httpserver.HttpServer
import org.springframework.web.client.RestClientException
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MsaAccountClientTests {
    private val identity = RequestIdentity("user-1", "device-1")
    @Test
    fun `MSA의 계좌 불일치 음수 잔액 빈 응답 및 잘못된 JSON을 거부한다`() {
        val invalidResponses = listOf(
            "{\"accountNo\":\"other-account\",\"amount\":100}",
            "{\"accountNo\":\"demo-0001\",\"amount\":-1}",
            "",
            "not-json",
        )
        invalidResponses.forEach { body ->
            withServer(body) { client ->
                assertFailsWith<RestClientException> { client.balance("demo-0001", identity) }
            }
        }
    }

    @Test
    fun `정상 응답은 업무에서 사용하는 잔액만 반환한다`() {
        withServer("{\"accountNo\":\"demo-0001\",\"amount\":123}") {
            assertEquals(123L, it.balance("demo-0001", identity))
        }
    }

    @Test
    fun `잘못된 서버 주소 및 타임아웃은 시작 전에 거부한다`() {
        assertFailsWith<IllegalArgumentException> { MsaClientProperties(baseUrl = URI("file:/tmp/msa")) }
        assertFailsWith<IllegalArgumentException> { MsaClientProperties(baseUrl = URI("/relative")) }
        assertFailsWith<IllegalArgumentException> { MsaClientProperties(connectTimeout = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { MsaClientProperties(readTimeout = Duration.ofMillis(-1)) }
    }

    private fun withServer(body: String, test: (MsaAccountClient) -> Unit) {
        val headers = java.util.concurrent.ConcurrentHashMap<String, String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/accounts/demo-0001/balance") { exchange ->
            headers["user_id"] = exchange.requestHeaders.getFirst("user_id") ?: ""
            headers["device_id"] = exchange.requestHeaders.getFirst("device_id") ?: ""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val properties = MsaClientProperties(baseUrl = URI("http://127.0.0.1:${server.address.port}"))
            val client = MsaAccountClient(MsaClientConfiguration().msaAccountRestClient(properties))
            test(client)
            assertEquals("user-1", headers["user_id"])
            assertEquals("device-1", headers["device_id"])
        } finally {
            server.stop(0)
        }
    }
}
