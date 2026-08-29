package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

class ConfigStore(
    private val customRootDir: File? = null
) {
    private val persistence = ConfigStorePersistence()

    private val canonicalConfigFile: File by lazy { AppDataPaths.configFile(customRootDir) }

    private val configuredPathError: String? by lazy {
        AppDataPaths.configPathError().takeUnless { customRootDir != null }
    }


    val configFile: File
        get() = canonicalConfigFile


    private val _configState = MutableStateFlow(AppConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    /** 配置加载、解码或校验失败时的可诊断信息；成功加载或保存后恢复为空。 */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        _configState.value = loadConfig()
    }

    val currentConfig: AppConfig
        get() = _configState.value

    fun loadConfig(): AppConfig {
        _loadError.value = null
        configuredPathError?.let { error ->
            recordLoadFailure(IllegalArgumentException(error))
            return _configState.value
        }
        val configPath = configFile.toPath()
        if (Files.exists(configPath, NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(configPath)) {
                return resetInvalidConfig(InvalidConfigException("配置文件不能是符号链接"))
            }
            AtomicFileWriter.setOwnerOnlyPermissions(configFile)
                .getOrElse { error ->
                    recordLoadFailure(error)
                    return _configState.value
                }
            return decodeConfig(configFile, "Studio 配置").getOrElse { error ->
                if (error is InvalidConfigException) {
                    resetInvalidConfig(error)
                } else {
                    recordLoadFailure(error)
                    _configState.value
                }
            }
        }

        val defaultConfig = normalizeConfig(AppConfig())
        writeConfigFile(defaultConfig).onFailure { error -> recordLoadFailure(error) }
        return defaultConfig
    }


    private fun decodeConfig(file: File, label: String): Result<AppConfig> {
        return persistence.decodeConfig(file, label)
    }

    private fun resetInvalidConfig(error: Throwable): AppConfig {
        val defaultConfig = normalizeConfig(AppConfig())
        val resetResult = runCatching {
            Files.deleteIfExists(configFile.toPath())
            writeConfigFile(defaultConfig).getOrThrow()
        }
        val message = if (resetResult.isSuccess) {
            "配置无效，已清除并重置：${error.message ?: "未知错误"}"
        } else {
            "配置无效且重置失败：${resetResult.exceptionOrNull()?.message ?: "未知错误"}"
        }
        recordLoadFailure(IllegalStateException(message, resetResult.exceptionOrNull() ?: error))
        return defaultConfig
    }

    private fun validateConfig(config: AppConfig) {
        ConfigStoreValidator.validate(config)
    }

    private fun writeConfigFile(config: AppConfig): Result<Unit> {
        return persistence.writeConfigFile(configFile, config)
    }

    private fun recordLoadFailure(error: Throwable?) {
        _loadError.value = error?.message ?: "配置加载失败：未知错误"
    }

    @Synchronized
    fun saveConfig(newConfig: AppConfig) {
        val normalized = normalizeConfig(newConfig)
        validateConfig(normalized)
        writeConfigFile(normalized).getOrElse { error ->
            _loadError.value = error.message ?: "配置保存失败：未知错误"
            throw error
        }
        _configState.value = normalized
        _loadError.value = null
        OfficialCatalogProbe.clearRawOfficialCatalog()
    }

    @Synchronized
    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        val updated = transform(currentConfig)
        saveConfig(updated)
    }

    private fun normalizeConfig(config: AppConfig): AppConfig {
        return ConfigStoreNormalizer.normalize(config)
    }
}
