package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class ConfigStore(
    private val customRootDir: File? = null
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val canonicalConfigFile: File by lazy { resolveCanonicalConfigFile() }

    private val configuredPathError: String? by lazy {
        val configured = System.getenv("AGY_BYOK_CONFIG_PATH")?.trim().orEmpty()
        if (configured.isNotEmpty() && !File(configured).isAbsolute) {
            "AGY_BYOK_CONFIG_PATH 必须是绝对路径"
        } else {
            null
        }
    }

    private val rootDir: File
        get() = canonicalConfigFile.parentFile ?: File(System.getProperty("user.home"))

    val configFile: File
        get() = canonicalConfigFile

    /** 旧 Studio 文件只用于一次性安全迁移，迁移前保留备份。 */
    private val studioLegacyConfigFile: File by lazy {
        File(System.getProperty("user.home"), ".antigravity-studio/config.json")
    }

    private val _configState = MutableStateFlow(AppConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    /** 配置加载、解码或导入失败时的可诊断信息；成功加载或保存后恢复为空。 */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        _configState.value = loadConfig()
    }

    val currentConfig: AppConfig
        get() = _configState.value

    fun loadConfig(): AppConfig {
        _loadError.value = null
        configuredPathError?.let {
            recordLoadFailure(IllegalArgumentException(it))
            return _configState.value
        }
        if (configFile.exists()) {
            val parsed = decodeConfig(configFile, "Studio 配置")
            if (parsed.isFailure) {
                recordLoadFailure(parsed.exceptionOrNull())
                return _configState.value
            }
            val config = parsed.getOrThrow()
            return config
        }

        if (studioLegacyConfigFile.exists() && studioLegacyConfigFile.absoluteFile != configFile.absoluteFile) {
            val parsed = decodeConfig(studioLegacyConfigFile, "旧 Studio 配置")
            if (parsed.isFailure) {
                recordLoadFailure(parsed.exceptionOrNull())
                return _configState.value
            }
            val imported = parsed.getOrThrow()
            backupLegacyConfig(studioLegacyConfigFile).onFailure { recordLoadFailure(it) }
            writeConfigFile(imported).onFailure { recordLoadFailure(it) }
            return imported
        }

        val defaultConfig = AppConfig()
        writeConfigFile(defaultConfig).onFailure { recordLoadFailure(it) }
        return defaultConfig
    }

    private fun resolveCanonicalConfigFile(): File {
        customRootDir?.let { return File(it, "config.v1.json") }
        val configuredPath = System.getenv("AGY_BYOK_CONFIG_PATH")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf { it.isAbsolute }
        if (configuredPath != null) return configuredPath

        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> File(userHome, "Library/Application Support/AGY BYOK/config.v1.json")
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData, "AGY BYOK/config.v1.json")
            }
            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, ".config").absolutePath
                File(configHome, "AGY BYOK/config.v1.json")
            }
        }
    }

    private fun backupLegacyConfig(file: File): Result<Unit> {
        return try {
            val backup = File(file.parentFile, "${file.name}.migrated.bak")
            val target = if (!backup.exists()) {
                backup
            } else {
                File(file.parentFile, "${file.name}.migrated-${System.currentTimeMillis()}.bak")
            }
            Files.copy(file.toPath(), target.toPath())
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("旧 Studio 配置备份失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun decodeConfig(file: File, label: String): Result<AppConfig> {
        return try {
            val content = file.readText(Charsets.UTF_8)
            Result.success(json.decodeFromString<AppConfig>(content))
        } catch (error: Exception) {
            Result.failure(IllegalStateException("$label 加载失败：${error.message ?: "内容无效"}", error))
        }
    }

    private fun writeConfigFile(config: AppConfig): Result<Unit> {
        var tempFile: File? = null
        return try {
            if (!rootDir.exists() && !rootDir.mkdirs()) {
                return Result.failure(IllegalStateException("无法创建配置目录：${rootDir.absolutePath}"))
            }
            val content = json.encodeToString(AppConfig.serializer(), config)
            val createdTempFile = File.createTempFile("config-", ".tmp", rootDir)
            tempFile = createdTempFile
            createdTempFile.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    createdTempFile.toPath(),
                    configFile.toPath(),
                    ATOMIC_MOVE,
                    REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(createdTempFile.toPath(), configFile.toPath(), REPLACE_EXISTING)
            }
            if (!configFile.exists()) {
                return Result.failure(IllegalStateException("配置文件原子替换后未找到：${configFile.absolutePath}"))
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("配置文件写入失败：${error.message ?: "未知错误"}", error))
        } finally {
            tempFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun recordLoadFailure(error: Throwable?) {
        _loadError.value = error?.message ?: "配置加载失败：未知错误"
    }

    @Synchronized
    fun saveConfig(newConfig: AppConfig) {
        writeConfigFile(newConfig).getOrElse { error ->
            _loadError.value = error.message ?: "配置保存失败：未知错误"
            throw error
        }
        _configState.value = newConfig
        _loadError.value = null
    }

    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        val updated = transform(currentConfig)
        saveConfig(updated)
    }
}
