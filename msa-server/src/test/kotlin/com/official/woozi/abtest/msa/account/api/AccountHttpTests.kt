package com.official.woozi.abtest.msa.account.api

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountHttpTests {
    @Value("\${local.server.port}") private var port: Int = 0
    private val client = HttpClient.newHttpClient()

    @Test
    fun `account endpoints and error responses`() {
        assertEquals(200, get("/accounts/demo-0001/balance").statusCode())
        val deposit = post("/accounts/demo-0001/deposits", "{\"amount\":10000}")
        assertEquals(200, deposit.statusCode())
        assertTrue(deposit.body().contains("\"amount\":10000"))
        val withdrawal = post("/accounts/demo-0001/withdrawals", "{\"amount\":3000}")
        assertEquals(200, withdrawal.statusCode())
        assertTrue(withdrawal.body().contains("\"amount\":7000"))
        assertEquals(409, post("/accounts/demo-0001/withdrawals", "{\"amount\":7001}").statusCode())
        val invalidAmount = post("/accounts/demo-0001/deposits", "{\"amount\":-1}")
        assertEquals(400, invalidAmount.statusCode())
        assertTrue(invalidAmount.body().contains("INVALID_REQUEST"))
        assertTrue(invalidAmount.body().contains("fieldErrors"))
        assertEquals(400, post("/accounts/demo-0001/deposits", "{}").statusCode())
        val missing = get("/accounts/missing/balance")
        assertEquals(404, missing.statusCode())
        assertTrue(missing.body().contains("ACCOUNT_NOT_FOUND"))
        assertTrue(get("/accounts/demo-0001/balance").body().contains("\"amount\":7000"))
    }

    private fun get(path: String) = client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun post(path: String, body: String) = client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
