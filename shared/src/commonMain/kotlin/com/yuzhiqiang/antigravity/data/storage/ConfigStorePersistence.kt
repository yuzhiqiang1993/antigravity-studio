package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 读写 canonical config.v2.json。
 *
 * Json 保持默认的未知字段拒绝行为；所有解析、校验失败都以无效配置返回，
 * 而文件读取和写入失败保持为 I/O 错误，由 ConfigStore 决定是否重置。
 */
internal class ConfigStorePersistence {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun decodeConfig(file: File, label: String): Result<AppConfig> {
        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return Result.failure(IllegalStateException("$label 读取失败：${error.message ?: "未知错误"}", error))
        }
        return try {
            val raw = json.parseToJsonElement(content).jsonObject
            val schemaVersion = raw["schema_version"]?.jsonPrimitive?.intOrNull
            require(schemaVersion == AppConfig.CURRENT_SCHEMA_VERSION) {
                "不支持的配置 schema_version：${schemaVersion ?: "缺失"}"
            }
            val normalized = ConfigStoreNormalizer.normalize(
                json.decodeFromJsonElement(AppConfig.serializer(), raw)
            )
            ConfigStoreValidator.validate(normalized)
            Result.success(normalized)
        } catch (error: Exception) {
            Result.failure(InvalidConfigException("$label 内容无效：${error.message ?: "未知错误"}", error))
        }
    }

    fun writeConfigFile(file: File, config: AppConfig): Result<Unit> {
        return try {
            val content = json.encodeToString(
                AppConfig.serializer(),
                ConfigStoreNormalizer.normalize(config)
            )
            AtomicFileWriter.writeText(
                target = file,
                content = content,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
                disallowSymlinks = true
            ).getOrThrow()
            if (!file.exists()) {
                return Result.failure(IllegalStateException("配置文件原子替换后未找到：${file.absolutePath}"))
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("配置文件写入失败：${error.message ?: "未知错误"}", error))
        }
    }
}

internal class InvalidConfigException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
