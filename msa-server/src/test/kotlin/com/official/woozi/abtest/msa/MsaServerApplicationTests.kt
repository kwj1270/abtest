package com.official.woozi.abtest.msa

import com.official.woozi.abtoggle.redis.RedisScenarioAssignmentStore
import com.official.woozi.abtoggle.scenario.ScenarioAssignmentStore
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MsaServerApplicationTests {
    @Autowired
    private lateinit var scenarioAssignmentStore: ScenarioAssignmentStore


    @Test
    fun contextLoads() {
        assertIs<RedisScenarioAssignmentStore>(scenarioAssignmentStore)
    }
}
