package com.official.woozi.abtest.legacy.web

import com.official.woozi.abtest.legacy.account.api.AccountRoutingFacade
import com.official.woozi.abtest.legacy.account.api.AccountController
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test

class ApiExceptionHandlerTests {
    private val identity = RequestIdentity("user-1", "device-1")
    private val service = mock(AccountRoutingFacade::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(AccountController(service))
        .setControllerAdvice(ApiExceptionHandler()).build()

    @Test
    fun `예상하지 못한 오류는 잔액 부족과 구분하고 내부 메시지를 노출하지 않는다`() {
        doThrow(IllegalStateException("internal database credentials")).`when`(service).balance("demo-0001", identity)
        mvc.perform(get("/accounts/demo-0001/balance").header("user_id", "user-1").header("device_id", "device-1"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("Internal server error"))
    }

    @Test
    fun `MSA 서버 오류의 본문을 노출하지 않고 게이트웨이 오류로 반환한다`() {
        val upstream = org.springframework.web.client.HttpServerErrorException.create(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Unavailable",
            org.springframework.http.HttpHeaders.EMPTY,
            "internal database credentials".toByteArray(),
            Charsets.UTF_8,
        )
        doThrow(upstream).`when`(service).balance("demo-0001", identity)
        mvc.perform(get("/accounts/demo-0001/balance").header("user_id", "user-1").header("device_id", "device-1"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("MSA_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("MSA request failed; outcome may be unknown"))
    }
}
