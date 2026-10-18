package com.official.woozi.abtest.legacy.web

import com.official.woozi.abtest.legacy.account.domain.AccountError
import com.official.woozi.abtest.legacy.account.domain.AccountException
import com.official.woozi.abtoggle.exception.ScenarioAssignmentNotFoundException
import org.springframework.dao.DataAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
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

@RestControllerAdvice(basePackages = ["com.official.woozi.abtest.legacy"])
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

    @ExceptionHandler(ScenarioAssignmentNotFoundException::class)
    fun missingScenario(): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError("SCENARIO_NOT_FOUND", "Scenario assignment not found or expired"))

    @ExceptionHandler(DataAccessException::class)
    fun unavailableStore(e: DataAccessException): ResponseEntity<ApiError> {
        log.error("Scenario storage request failed", e)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiError("SCENARIO_STORAGE_UNAVAILABLE", "Scenario storage unavailable"))
    }

    @ExceptionHandler(RestClientResponseException::class)
    fun msaResponse(e: RestClientResponseException): ResponseEntity<ApiError> {
        if (e.statusCode.value() in setOf(400, 404, 409)) {
            val error = try { e.getResponseBodyAs(ApiError::class.java) } catch (_: Exception) { null }
            if (error != null) return ResponseEntity.status(e.statusCode).body(error)
        }
        log.warn("MSA response rejected: status={}", e.statusCode.value())
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError("MSA_UNAVAILABLE", "MSA request failed; outcome may be unknown"))
    }

    @ExceptionHandler(RestClientException::class)
    fun msaUnavailable(e: RestClientException): ResponseEntity<ApiError> {
        log.warn("MSA request failed; no retry or fallback", e)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError("MSA_UNAVAILABLE", "MSA request failed; outcome may be unknown"))
    }

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
