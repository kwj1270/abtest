package com.official.woozi.abtest.legacy.toggle.admin

import com.official.woozi.abtest.legacy.web.ApiError
import com.official.woozi.abtoggle.config.BankToggle
import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.redis.RedisToggleConfigSource
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import java.math.BigDecimal
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException

@Controller
@RequestMapping("/admin")
class ToggleAdminController(private val configs: RedisToggleConfigSource) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/toggles")
    fun page(): String = "forward:/admin/toggles/index.html"

    @ResponseBody
    @GetMapping("/api/toggles")
    fun list(): List<ToggleView> = BankToggle.entries.map { toggle ->
        view(toggle, configs.find(toggle).getOrThrow()?.newPercent ?: 0)
    }

    @ResponseBody
    @PutMapping("/api/toggles/{key}")
    fun update(@PathVariable key: String, @Valid @RequestBody request: UpdateRatioRequest): ToggleView {
        val toggle = BankToggle.entries.firstOrNull { it.value == key }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown toggle")
        val config = ToggleConfig(toggle, requireNotNull(request.newPercent).intValueExact())
        val saved = configs.save(config).getOrThrow()
        log.info("Toggle ratio updated: key={}, newPercent={}", toggle.value, saved.newPercent)
        return view(toggle, saved.newPercent)
    }

    @ExceptionHandler(DataAccessException::class)
    @ResponseBody
    fun unavailable(e: DataAccessException): ResponseEntity<ApiError> {
        log.error("Toggle administration storage failed", e)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiError("TOGGLE_STORAGE_UNAVAILABLE", "Redis에 연결할 수 없습니다. 저장 상태를 확인한 뒤 다시 시도해 주세요."))
    }

    private fun view(toggle: BankToggle, percent: Int): ToggleView {
        val name = when (toggle) {
            BankToggle.ACCOUNT_OPERATIONS -> "계좌 입출금·조회"
            BankToggle.TRANSFER -> "이체"
            BankToggle.BALANCE_LOOKUP -> "잔액 조회"
        }
        return ToggleView(toggle.value, name, percent, 100 - percent, toggle == BankToggle.ACCOUNT_OPERATIONS)
    }

    data class UpdateRatioRequest(
        @field:NotNull @field:Min(0) @field:Max(100) @field:Digits(integer = 3, fraction = 0)
        val newPercent: BigDecimal?,
    )

    data class ToggleView(
        val key: String,
        val name: String,
        val newPercent: Int,
        val legacyPercent: Int,
        val connected: Boolean,
    )
}
