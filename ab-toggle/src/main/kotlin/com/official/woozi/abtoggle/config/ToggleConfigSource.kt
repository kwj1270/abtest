package com.official.woozi.abtoggle.config

fun interface ToggleConfigSource {
    fun find(key: ToggleKey): Result<ToggleConfig?>
}

class StaticToggleConfigSource(configs: Collection<ToggleConfig>) : ToggleConfigSource {
    private val configsByKey: Map<String, ToggleConfig> = configs.associateBy { it.key.value }

    init {
        require(configsByKey.size == configs.size) { "duplicate toggle keys: ${configs.map { it.key.value }}" }
    }

    override fun find(key: ToggleKey): Result<ToggleConfig?> = Result.success(configsByKey[key.value])
}
