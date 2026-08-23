package com.yuzhiqiang.antigravity.host.ownership

import com.yuzhiqiang.antigravity.host.macos.MacHostManager
import com.yuzhiqiang.antigravity.host.windows.WindowsHostManager
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 统一维护宿主接入的 ownership receipt。
 *
 * App 与 CLI 共享 `CLOUD_CODE_URL`，IDE 使用独立 settings receipt。所有停用操作都先
 * 校验当前值仍是 Studio 写入的值，避免覆盖用户或其他工具在接管期间产生的外部修改。
 */
object HostOwnershipStore {
    private const val RECEIPT_SCHEMA_VERSION = 1
    private const val ENVIRONMENT_RECEIPT_FILE = "environment-ownership.json"
    private const val IDE_RECEIPT_FILE = "ide-settings-ownership.json"
    private const val ENVIRONMENT_KEY = "CLOUD_CODE_URL"
    private const val IDE_SETTING_KEY = "jetski.cloudCodeUrl"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    @Serializable
    enum class EnvironmentOwner {
        APP,
        CLI
    }

    @Serializable
    private data class EnvironmentReceipt(
        val schemaVersion: Int = RECEIPT_SCHEMA_VERSION,
        val managedEndpoint: String,
        val originalEndpoint: String? = null,
        val appOwner: Boolean = false,
        val cliOwner: Boolean = false
    ) {
        fun hasOwner(owner: EnvironmentOwner): Boolean {
            return when (owner) {
                EnvironmentOwner.APP -> appOwner
                EnvironmentOwner.CLI -> cliOwner
            }
        }

        fun withOwner(owner: EnvironmentOwner, enabled: Boolean): EnvironmentReceipt {
            return when (owner) {
                EnvironmentOwner.APP -> copy(appOwner = enabled)
                EnvironmentOwner.CLI -> copy(cliOwner = enabled)
            }
        }

        fun hasNoOwner(): Boolean {
            return !appOwner && !cliOwner
        }
    }

    @Serializable
    private data class IdeReceipt(
        val schemaVersion: Int = RECEIPT_SCHEMA_VERSION,
        val settingsPath: String,
        val managedEndpoint: String,
        val originalContent: String
    )

    data class IntegrationInspectResult(
        val state: com.yuzhiqiang.antigravity.host.model.ClientIntegrationState,
        val configuredEndpoint: String?,
        val endpointMatches: Boolean,
        val canDisable: Boolean
    )

    /** 详细探测指定 IDE settings 的代理集成状态与端点。 */
    fun inspectIdeIntegration(settingsFile: File, proxyPort: Int): IntegrationInspectResult {
        val target = localEndpoint(proxyPort)
        val content = readText(settingsFile).getOrNull()
            ?: return IntegrationInspectResult(
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL,
                null,
                false,
                false
            )
        val endpoint = extractIdeEndpoint(content)
            ?: return IntegrationInspectResult(
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL,
                null,
                false,
                false
            )
        val matches = endpoint == target
        val receipt = readIdeReceipt().getOrNull()
        val isManaged = receipt?.managedEndpoint == endpoint &&
                receipt.settingsPath == settingsFile.absoluteFile.normalize().path
        val state = when {
            matches && isManaged -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED
            matches -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.EXTERNAL
            else -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH
        }
        return IntegrationInspectResult(
            state = state,
            configuredEndpoint = endpoint,
            endpointMatches = matches,
            canDisable = true
        )
    }

    /** 详细探测系统共享环境变量的代理集成状态与端点。 */
    fun inspectEnvironmentIntegration(owner: EnvironmentOwner, proxyPort: Int): IntegrationInspectResult {
        val target = localEndpoint(proxyPort)
        val endpoint = readEnvironmentEndpoint().getOrNull()
            ?: return IntegrationInspectResult(
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL,
                null,
                false,
                false
            )
        val matches = endpoint == target
        val receipt = readEnvironmentReceipt().getOrNull()
        val isManaged = receipt?.managedEndpoint == endpoint && receipt.hasOwner(owner)
        val state = when {
            matches && isManaged -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED
            matches -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.EXTERNAL
            else -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH
        }
        return IntegrationInspectResult(
            state = state,
            configuredEndpoint = endpoint,
            endpointMatches = matches,
            canDisable = true
        )
    }

    /** 强制重置 IDE 代理配置至官方直连（无视 receipt 记录，直接剔除配置并清理 receipt）。 */
    fun forceResetIde(settingsFile: File): Result<Unit> {
        removeReceipt(ideReceiptFile())
        removeReceipt(integrationRoot().resolve("ide-receipt.json"))
        removeReceipt(integrationRoot().resolve("ide-setting-ownership.json"))
        removeReceipt(integrationRoot().resolve("ide-settings-ownership.json"))
        val candidateFiles = (com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getCandidateSettingsFiles() + listOf(settingsFile)).distinct()
        for (file in candidateFiles) {
            if (!file.exists()) continue
            val content = readText(file).getOrNull() ?: continue
            val updated = removeIdeEndpoint(content)
            if (updated != null && updated != content) {
                writeTextAtomically(file, updated)
            }
        }
        return Result.success(Unit)
    }

    /** 强制重置共享环境变量至官方模式（彻底清除 launchctl/注册表环境变量与 receipt）。 */
    fun forceResetEnvironment(): Result<Unit> {
        removeReceipt(environmentReceiptFile())
        removeReceipt(integrationRoot().resolve("environment-receipt.json"))
        removeReceipt(integrationRoot().resolve("environment-ownership.json"))
        unsetEnvironmentEndpoint()
        return Result.success(Unit)
    }

    /** 强制重置所有宿主接入状态至干净的官方模式。 */
    fun forceResetAll(): Result<Unit> {
        forceResetEnvironment()
        forceResetIde(com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getSettingsFile())
        return Result.success(Unit)
    }

    /** 判断指定代理端口当前是否已经写入宿主环境变量。 */
    fun isEnvironmentConfigured(proxyPort: Int): Boolean {
        val endpoint = readEnvironmentEndpoint().getOrNull()
        return endpoint == localEndpoint(proxyPort)
    }

    /** 只有 receipt 仍记录当前入口 owner 时，才视为 Studio 正在托管接入。 */
    fun isEnvironmentConfigured(owner: EnvironmentOwner, proxyPort: Int): Boolean {
        val endpoint = readEnvironmentEndpoint().getOrNull() ?: return false
        if (endpoint != localEndpoint(proxyPort)) return false
        val receipt = readEnvironmentReceipt().getOrNull() ?: return false
        return receipt.managedEndpoint == endpoint && receipt.hasOwner(owner)
    }

    /** 判断指定 IDE settings 当前是否已经写入代理地址。 */
    fun isIdeConfigured(settingsFile: File, proxyPort: Int): Boolean {
        val content = readText(settingsFile).getOrNull() ?: return false
        val endpoint = extractIdeEndpoint(content) ?: return false
        if (endpoint != localEndpoint(proxyPort)) return false
        val receipt = readIdeReceipt().getOrNull() ?: return false
        return receipt.settingsPath == settingsFile.absoluteFile.normalize().path &&
                receipt.managedEndpoint == endpoint
    }

    /** 以 App owner 身份接管共享环境变量。 */
    fun enableEnvironment(owner: EnvironmentOwner, proxyPort: Int): Result<Unit> {
        return enableEnvironment(owner, localEndpoint(proxyPort))
    }

   /** 以指定 owner 身份接管共享环境变量。 */
   fun enableEnvironment(owner: EnvironmentOwner, endpoint: String): Result<Unit> {
       if (!isLocalEndpoint(endpoint)) {
           return Result.failure(IllegalArgumentException("宿主代理地址必须是本机回环地址"))
       }
       val currentEndpoint = readEnvironmentEndpoint().getOrElse { error ->
           return Result.failure(error)
       }
       val previousReceipt = readEnvironmentReceipt().getOrElse { error ->
           return Result.failure(error)
       }
        val originalEndpoint = if (currentEndpoint != null && !isLocalEndpoint(currentEndpoint)) {
            currentEndpoint
        } else {
            previousReceipt?.originalEndpoint?.takeIf { !isLocalEndpoint(it) }
        }
        val receipt = EnvironmentReceipt(
            schemaVersion = RECEIPT_SCHEMA_VERSION,
            managedEndpoint = endpoint,
            originalEndpoint = originalEndpoint,
            appOwner = owner == EnvironmentOwner.APP || previousReceipt?.appOwner == true,
            cliOwner = owner == EnvironmentOwner.CLI || previousReceipt?.cliOwner == true
        )
       val writeReceiptResult = writeReceipt(environmentReceiptFile(), receipt)
       if (writeReceiptResult.isFailure) {
           return writeReceiptResult
       }
       if (setEnvironmentEndpoint(endpoint)) {
           return Result.success(Unit)
       }
       restoreEnvironmentReceipt(previousReceipt)
       return Result.failure(IllegalStateException("写入 $ENVIRONMENT_KEY 失败"))
   }

  /** 释放指定 owner；仅最后一个 owner 停用时恢复接管前的环境值。 */
  fun disableEnvironment(owner: EnvironmentOwner): Result<Unit> {
      val previousReceipt = readEnvironmentReceipt().getOrElse { null }
      if (previousReceipt != null) {
          val updatedReceipt = previousReceipt.withOwner(owner, enabled = false)
          if (!updatedReceipt.hasNoOwner()) {
              // 仍有其他 owner 处于开启状态，保存更新后的 receipt 并保留当前环境
              writeReceipt(environmentReceiptFile(), updatedReceipt)
              return Result.success(Unit)
          }
      }
      // 没有任何 owner 了，彻底清理所有相关 receipt
      removeReceipt(environmentReceiptFile())
      removeReceipt(integrationRoot().resolve("environment-receipt.json"))
      removeReceipt(integrationRoot().resolve("environment-ownership.json"))
      val original = previousReceipt?.originalEndpoint?.trim()
      val shouldRestoreOriginal = !original.isNullOrBlank() &&
              !isLocalEndpoint(original) &&
              !original.contains("127.0.0.1") &&
              !original.contains("localhost")
      val restoreResult = if (shouldRestoreOriginal) {
          setEnvironmentEndpoint(original!!)
      } else {
         unsetEnvironmentEndpoint()
     }
     if (!restoreResult) {
         return Result.failure(IllegalStateException("恢复 $ENVIRONMENT_KEY 原始值失败"))
     }
     return Result.success(Unit)
  }

   /** 接管 IDE settings，并保留接管前的完整文件内容。 */
    fun enableIde(settingsFile: File, proxyPort: Int): Result<Unit> {
        val endpoint = localEndpoint(proxyPort)
        val currentContent = readText(settingsFile).getOrElse { error ->
            if (error is java.io.FileNotFoundException) {
                "{}\n"
            } else {
                return Result.failure(error)
            }
        }
        val currentEndpoint = extractIdeEndpoint(currentContent)
        val previousReceipt = readIdeReceipt().getOrElse { error ->
            return Result.failure(error)
        }
        val settingsPath = settingsFile.absoluteFile.normalize().path
        val originalContent = if (
            previousReceipt?.settingsPath == settingsPath &&
            currentEndpoint == previousReceipt.managedEndpoint
        ) {
            previousReceipt.originalContent
        } else {
            currentContent
        }
        val receipt = IdeReceipt(
            settingsPath = settingsPath,
            managedEndpoint = endpoint,
            originalContent = originalContent
        )
        val writeReceiptResult = writeReceipt(ideReceiptFile(), receipt)
        if (writeReceiptResult.isFailure) {
            return writeReceiptResult
        }
        val updatedContent = updateIdeEndpoint(currentContent, endpoint)
            ?: run {
                restoreIdeReceipt(previousReceipt)
                return Result.failure(IllegalStateException("无法安全修改 IDE JSONC 配置"))
            }
        val writeSettingsResult = writeTextAtomically(settingsFile, updatedContent)
        if (writeSettingsResult.isSuccess) {
            return Result.success(Unit)
        }
        restoreIdeReceipt(previousReceipt)
        return writeSettingsResult
    }

  /** 释放 IDE 接入；优先恢复 receipt 保存的完整原始内容。 */
  fun disableIde(settingsFile: File, proxyPort: Int): Result<Unit> {
      val previousReceipt = readIdeReceipt().getOrElse { null }
      removeReceipt(ideReceiptFile())
      removeReceipt(integrationRoot().resolve("ide-receipt.json"))
      removeReceipt(integrationRoot().resolve("ide-setting-ownership.json"))
      removeReceipt(integrationRoot().resolve("ide-settings-ownership.json"))
      val candidateFiles = (com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getCandidateSettingsFiles() + listOf(settingsFile)).distinct()
      for (file in candidateFiles) {
          if (!file.exists()) continue
          val content = readText(file).getOrNull() ?: continue
          val settingsPath = file.absoluteFile.normalize().path
          if (previousReceipt?.settingsPath == settingsPath &&
              !previousReceipt.originalContent.contains("127.0.0.1") &&
              extractIdeEndpoint(previousReceipt.originalContent) == null
          ) {
              writeTextAtomically(file, previousReceipt.originalContent)
              continue
          }
          val updated = removeIdeEndpoint(content)
          if (updated != null && updated != content) {
              writeTextAtomically(file, updated)
          }
      }
      return Result.success(Unit)
  }

    private fun buildEnvironmentReceipt(
        previousReceipt: EnvironmentReceipt?,
        currentEndpoint: String?,
        owner: EnvironmentOwner,
        targetEndpoint: String
    ): EnvironmentReceipt {
        val canRetainOwnership = previousReceipt != null &&
                currentEndpoint == previousReceipt.managedEndpoint
        val base = if (canRetainOwnership) {
            previousReceipt.copy(managedEndpoint = targetEndpoint)
        } else {
            EnvironmentReceipt(
                managedEndpoint = targetEndpoint,
                originalEndpoint = currentEndpoint
            )
        }
        return base.withOwner(owner, enabled = true)
    }

    private fun localEndpoint(proxyPort: Int): String {
        return "http://127.0.0.1:$proxyPort"
    }

    private fun isLocalEndpoint(endpoint: String?): Boolean {
        if (endpoint.isNullOrBlank()) {
            return false
        }
        return try {
            val uri = URI(endpoint)
            val host = uri.host?.lowercase()
            uri.scheme.equals("http", ignoreCase = true) &&
                    host in setOf("127.0.0.1", "localhost", "::1") &&
                    uri.port in 1..65535
        } catch (_: Exception) {
            false
        }
    }

    private fun readEnvironmentEndpoint(): Result<String?> {
        return Result.success(
            if (isWindows()) {
                WindowsHostManager.getEnvironmentUrl()
            } else {
                MacHostManager.getEnvironmentUrl()
            }
        )
    }

    private fun setEnvironmentEndpoint(endpoint: String): Boolean {
        return if (isWindows()) {
            WindowsHostManager.setEnvironmentUrl(endpoint)
        } else {
            MacHostManager.setEnvironmentUrl(endpoint)
        }
    }

    private fun unsetEnvironmentEndpoint(): Boolean {
        return if (isWindows()) {
            WindowsHostManager.unsetEnvironmentUrl()
        } else {
            MacHostManager.unsetEnvironmentUrl()
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase().contains("win")
    }

    private fun extractIdeEndpoint(content: String): String? {
        return IDE_ENDPOINT_REGEX.find(content)?.groupValues?.getOrNull(1)
    }

    private fun updateIdeEndpoint(content: String, endpoint: String): String? {
        val existing = IDE_ENDPOINT_REGEX.find(content)
        if (existing != null) {
            val valueGroup = existing.groups[1] ?: return null
            return content.replaceRange(valueGroup.range, endpoint)
        }
        val closingBrace = content.lastIndexOf('}')
        if (closingBrace < 0) {
            return null
        }
        val prefix = content.substring(0, closingBrace)
        val suffix = content.substring(closingBrace)
        val trimmedPrefix = prefix.trimEnd()
        val separator = if (trimmedPrefix.endsWith('{') || trimmedPrefix.endsWith(',')) {
            "\n"
        } else {
            ",\n"
        }
        return prefix + separator + "  \"$IDE_SETTING_KEY\": \"$endpoint\"\n" + suffix
    }

    private fun removeIdeEndpoint(content: String): String? {
        val lineMatch = IDE_LINE_REGEX.find(content)
        var updated = if (lineMatch != null) {
            content.removeRange(lineMatch.range)
        } else {
            val inlineMatch = IDE_ENDPOINT_REGEX.find(content)
            if (inlineMatch != null) {
                content.removeRange(inlineMatch.range)
            } else {
                content
            }
        }
        val trailingCommaRegex = Regex(""",(\s*\})""")
        updated = trailingCommaRegex.replace(updated, "$1")
        val leadingCommaRegex = Regex("""(\{\s*),""")
        updated = leadingCommaRegex.replace(updated, "$1")
        return updated
    }

    private fun environmentReceiptFile(): File {
        return integrationRoot().resolve(ENVIRONMENT_RECEIPT_FILE)
    }

    private fun ideReceiptFile(): File {
        return integrationRoot().resolve(IDE_RECEIPT_FILE)
    }

    private fun integrationRoot(): File {
        val configuredPath = (System.getenv("ANTIGRAVITY_STUDIO_CONFIG_PATH")
            ?: System.getenv("AGY_STUDIO_CONFIG_PATH"))
            ?.trim()
            ?.takeIf { path -> path.isNotEmpty() }
            ?.let(::File)
            ?.takeIf(File::isAbsolute)
        if (configuredPath != null) {
            return configuredPath.parentFile ?: configuredPath
        }
        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name", "").lowercase()
        return when {
            osName.contains("mac") -> File(userHome, "Library/Application Support/Antigravity Studio")
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData, "Antigravity Studio")
            }

            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: File(userHome, ".config").absolutePath
                File(configHome, "Antigravity Studio")
            }
        }
    }

    private fun readEnvironmentReceipt(): Result<EnvironmentReceipt?> {
        return readReceipt(environmentReceiptFile())
    }

    private fun readIdeReceipt(): Result<IdeReceipt?> {
        return readReceipt(ideReceiptFile())
    }

    private inline fun <reified T> readReceipt(file: File): Result<T?> {
        if (!file.exists()) {
            return Result.success(null)
        }
        return try {
            val receipt = json.decodeFromString<T>(file.readText(Charsets.UTF_8))
            Result.success(receipt)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("接入 receipt 解析失败：${error.message ?: "内容无效"}", error))
        }
    }

    private inline fun <reified T> writeReceipt(file: File, receipt: T): Result<Unit> {
        return try {
            writeTextAtomically(file, json.encodeToString(receipt))
        } catch (error: Exception) {
            Result.failure(IllegalStateException("接入 receipt 写入失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun restoreEnvironmentReceipt(previousReceipt: EnvironmentReceipt?): Result<Unit> {
        return if (previousReceipt == null) {
            removeReceipt(environmentReceiptFile())
        } else {
            writeReceipt(environmentReceiptFile(), previousReceipt)
        }
    }

    private fun restoreIdeReceipt(previousReceipt: IdeReceipt?): Result<Unit> {
        return if (previousReceipt == null) {
            removeReceipt(ideReceiptFile())
        } else {
            writeReceipt(ideReceiptFile(), previousReceipt)
        }
    }

    private fun removeReceipt(file: File): Result<Unit> {
        return try {
            if (file.exists()) {
                Files.delete(file.toPath())
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("接入 receipt 删除失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun readText(file: File): Result<String> {
        return try {
            if (!file.exists()) {
                Result.failure(java.io.FileNotFoundException(file.absolutePath))
            } else {
                Result.success(file.readText(Charsets.UTF_8))
            }
        } catch (error: Exception) {
            Result.failure(IllegalStateException("读取宿主配置失败：${error.message ?: "未知错误"}", error))
        }
    }

    private fun writeTextAtomically(file: File, content: String): Result<Unit> {
        return try {
            if (Files.isSymbolicLink(file.toPath())) {
                return Result.failure(IllegalStateException("宿主配置目标不能是符号链接：${file.absolutePath}"))
            }
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return Result.failure(IllegalStateException("无法创建宿主配置目录：${parent.absolutePath}"))
            }
            val originalPermissions = runCatching {
                if (file.exists() && !Files.isSymbolicLink(file.toPath())) {
                    Files.getPosixFilePermissions(file.toPath())
                } else {
                    null
                }
            }.getOrNull()
            val temp = File.createTempFile("${file.name}-", ".tmp", parent)
            try {
                temp.writeText(content, Charsets.UTF_8)
                val moved = try {
                    try {
                        Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING)
                    }
                    true
                } catch (_: Exception) {
                    // Windows 下当目标文件被 IDE/进程占用时，Files.move 可能会报错；回退为直接写入
                    false
                }
                if (!moved) {
                    file.writeText(content, Charsets.UTF_8)
                }
                originalPermissions?.let { permissions: Set<PosixFilePermission> ->
                    runCatching { Files.setPosixFilePermissions(file.toPath(), permissions) }
                }
            } finally {
                if (temp.exists()) {
                    temp.delete()
                }
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException("写入宿主配置失败：${error.message ?: "未知错误"}", error))
        }
    }

    private val IDE_ENDPOINT_REGEX = Regex(
        "\\\"$IDE_SETTING_KEY\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
    )
    private val IDE_LINE_REGEX = Regex(
        "(?m)^[ \\t]*\\\"$IDE_SETTING_KEY\\\"\\s*:\\s*\\\"[^\\\"]*\\\"\\s*,?[ \\t]*\\r?\\n?"
    )
}

