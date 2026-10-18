package com.official.woozi.abtest.msa.account.api.exception

import com.official.woozi.abtest.msa.account.domain.exception.AccountError
import com.official.woozi.abtest.msa.account.domain.exception.AccountException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice(basePackages = ["com.official.woozi.abtest.msa"])
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AccountException::class)
    fun accountError(e: AccountException): ResponseEntity<ApiError> {
        val status = when (e.code) {
            AccountError.INVALID_AMOUNT, AccountError.BALANCE_LIMIT_EXCEEDED -> HttpStatus.BAD_REQUEST
            AccountError.ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND
            AccountError.INSUFFICIENT_BALANCE -> HttpStatus.CONFLICT
        }
        return ResponseEntity.status(status).body(ApiError(e.code.name, requireNotNull(e.message)))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(): ResponseEntity<ApiError> = ResponseEntity.badRequest()
        .body(ApiError("INVALID_REQUEST", "Invalid request parameters"))

    @ExceptionHandler(Exception::class)
    fun unexpectedError(e: Exception): ResponseEntity<ApiError> {
        log.error("Unexpected request failure", e)
        return ResponseEntity.internalServerError().body(ApiError("INTERNAL_ERROR", "Internal server error"))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val fields = if (ex is MethodArgumentNotValidException) ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "Invalid value") } else emptyMap()
        val error = ApiError(
            if (statusCode.is4xxClientError) "INVALID_REQUEST" else "INTERNAL_ERROR",
            HttpStatus.resolve(statusCode.value())?.reasonPhrase ?: "Request failed",
            fields,
        )
        return super.handleExceptionInternal(ex, error, headers, statusCode, request)
    }
}
