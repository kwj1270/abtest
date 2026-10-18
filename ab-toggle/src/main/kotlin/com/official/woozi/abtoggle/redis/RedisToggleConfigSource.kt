package com.official.woozi.abtoggle.redis

import com.official.woozi.abtoggle.config.ToggleConfig
import com.official.woozi.abtoggle.config.ToggleConfigSource
import com.official.woozi.abtoggle.config.ToggleKey
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.logging.Logger

/** 설정은 만료 없이 저장하며 캐시 없이 읽는다. 누락/유효하지 않은 비율은 0%다. */
class RedisToggleConfigSource(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String = "ab-toggle:config",
) : ToggleConfigSource {
    private val logger = Logger.getLogger(javaClass.name)

    init {
        require(keyPrefix.isNotBlank()) { "keyPrefix must not be blank" }
    }

    override fun find(key: ToggleKey): Result<ToggleConfig?> = try {
        val raw = redis.opsForValue().get(redisKey(key))
        val percent = raw?.toIntOrNull()?.takeIf { it in 0..100 }
        if (raw != null && percent == null) {
            logger.warning { "Invalid toggle ratio; using 0%: toggleKey=${key.value}" }
        }
        Result.success(ToggleConfig(key, percent ?: 0))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun save(config: ToggleConfig): Result<ToggleConfig> = try {
        redis.opsForValue().set(redisKey(config.key), config.newPercent.toString())
        Result.success(config)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun redisKey(key: ToggleKey): String = "$keyPrefix:${key.value}:new-percent"
}
