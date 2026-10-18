package com.official.woozi.abtest.msa.web

import com.official.woozi.abtest.msa.account.application.AccountService
import com.official.woozi.abtest.msa.account.api.AccountController
import com.official.woozi.abtest.msa.account.api.exception.ApiExceptionHandler
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test

class ApiExceptionHandlerTests {
    private val service = mock(AccountService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(AccountController(service))
        .setControllerAdvice(ApiExceptionHandler()).build()

    @Test
    fun `예상하지 못한 오류는 잔액 부족과 구분하고 내부 메시지를 노출하지 않는다`() {
        doThrow(IllegalStateException("internal database credentials")).`when`(service).balance("demo-0001")
        mvc.perform(get("/accounts/demo-0001/balance"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("Internal server error"))
    }
}
