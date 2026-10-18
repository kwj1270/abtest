package com.official.woozi.abtest.msa

import com.official.woozi.abtoggle.redis.RedisScenarioConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@Import(RedisScenarioConfiguration::class)
@SpringBootApplication
class MsaServerApplication

fun main(args: Array<String>) {
    runApplication<MsaServerApplication>(*args)
}
