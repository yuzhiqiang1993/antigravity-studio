package com.yuzhiqiang.antigravity.host.ownership

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.host.macos.MacHostManager
import com.yuzhiqiang.antigravity.host.windows.WindowsHostManager
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * 统一维护宿主接入的 ownership receipt。
 *
 * App 与 CLI 共享 `CLOUD_CODE_URL`，IDE 使用独立 settings receipt。所有停用操作都先
 * 校验当前值仍是 Studio 写入的值，避免覆盖用户或其他工具在接管期间产生的外部修改。
 */
object HostOwnershipStore {
    private const val RECEIPT_SCHEMA_VERSION = 1
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
        @SerialName("schema_version") val schemaVersion: Int,
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
        @SerialName("schema_version") val schemaVersion: Int,
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
        val candidateFiles =
            (com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getCandidateSettingsFiles() + listOf(settingsFile)).distinct()
        for (file in candidateFiles) {
            if (!file.exists()) continue
            val content = readText(file).getOrElse { error -> return Result.failure(error) }
            val updated = removeIdeEndpoint(content)
            if (updated != null && updated != content) {
                writeTextAtomically(file, updated).getOrElse { error -> return Result.failure(error) }
            }
        }
        return removeIdeReceipts()
    }

    /** 强制重置共享环境变量至官方模式（彻底清除 launchctl/注册表环境变量与 receipt）。 */
    fun forceResetEnvironment(): Result<Unit> {
        if (!unsetEnvironmentEndpoint()) {
            return Result.failure(IllegalStateException("清除 $ENVIRONMENT_KEY 失败"))
        }
        return removeEnvironmentReceipts()
    }

    /** 强制重置所有宿主接入状态至干净的官方模式。 */
    fun forceResetAll(): Result<Unit> {
        forceResetEnvironment().getOrElse { error -> return Result.failure(error) }
        return forceResetIde(com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getSettingsFile())
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
        val previousReceipt = readEnvironmentReceipt().getOrElse { error -> return Result.failure(error) }
        if (previousReceipt != null) {
            val updatedReceipt = previousReceipt.withOwner(owner, enabled = false)
            if (!updatedReceipt.hasNoOwner()) {
                // 仍有其他 owner 处于开启状态，保存更新后的 receipt 并保留当前环境
                return writeReceipt(environmentReceiptFile(), updatedReceipt)
            }
        }
        val original = previousReceipt?.originalEndpoint?.trim()
        val shouldRestoreOriginal = !original.isNullOrBlank() &&
                !isLocalEndpoint(original) &&
                !original.contains("127.0.0.1") &&
                !original.contains("localhost")
        val restoreResult = if (shouldRestoreOriginal) {
            original?.let(::setEnvironmentEndpoint) ?: false
        } else {
            unsetEnvironmentEndpoint()
        }
        if (!restoreResult) {
            return Result.failure(IllegalStateException("恢复 $ENVIRONMENT_KEY 原始值失败"))
        }
        return removeEnvironmentReceipts()
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
            schemaVersion = RECEIPT_SCHEMA_VERSION,
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
        val previousReceipt = readIdeReceipt().getOrElse { error -> return Result.failure(error) }
        val candidateFiles =
            (com.yuzhiqiang.antigravity.host.ide.IdeHostManager.getCandidateSettingsFiles() + listOf(settingsFile)).distinct()
        for (file in candidateFiles) {
            if (!file.exists()) continue
            val content = readText(file).getOrElse { error -> return Result.failure(error) }
            val settingsPath = file.absoluteFile.normalize().path
            if (previousReceipt?.settingsPath == settingsPath &&
                !previousReceipt.originalContent.contains("127.0.0.1") &&
                extractIdeEndpoint(previousReceipt.originalContent) == null
            ) {
                writeTextAtomically(file, previousReceipt.originalContent)
                    .getOrElse { error -> return Result.failure(error) }
                continue
            }
            val updated = removeIdeEndpoint(content)
            if (updated != null && updated != content) {
                writeTextAtomically(file, updated).getOrElse { error -> return Result.failure(error) }
            }
        }
        return removeIdeReceipts()
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
                schemaVersion = RECEIPT_SCHEMA_VERSION,
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

    private fun environmentReceiptFile(): File =
        AppDataPaths.resolve(AppDataPaths.ENVIRONMENT_RECEIPT_FILE_NAME)

    private fun ideReceiptFile(): File =
        AppDataPaths.resolve(AppDataPaths.IDE_RECEIPT_FILE_NAME)

    private fun readEnvironmentReceipt(): Result<EnvironmentReceipt?> {
        return readReceipt(environmentReceiptFile())
    }

    private fun readIdeReceipt(): Result<IdeReceipt?> {
        return readReceipt(ideReceiptFile())
    }

    private inline fun <reified T> readReceipt(file: File): Result<T?> {
        val path = file.toPath()
        if (!Files.exists(path, NOFOLLOW_LINKS)) {
            return Result.success(null)
        }
        if (Files.isSymbolicLink(path)) {
            return removeReceipt(file).map { null }
        }
        AtomicFileWriter.setOwnerOnlyPermissions(file).getOrElse { error ->
            return Result.failure(error)
        }
        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return Result.failure(IllegalStateException("接入 receipt 读取失败：${error.message ?: "未知错误"}", error))
        }
        return try {
            val raw = json.parseToJsonElement(content).jsonObject
            val schemaVersion = raw["schema_version"]?.jsonPrimitive?.intOrNull
            require(schemaVersion == RECEIPT_SCHEMA_VERSION) {
                "不支持的 receipt schema_version：${schemaVersion ?: "缺失"}"
            }
            Result.success(json.decodeFromJsonElement(serializer<T>(), raw))
        } catch (_: Exception) {
            removeReceipt(file).map { null }
        }
    }

    private inline fun <reified T> writeReceipt(file: File, receipt: T): Result<Unit> {
        return AtomicFileWriter.writeText(
            target = file,
            content = json.encodeToString(receipt),
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true
        ).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                Result.failure(IllegalStateException("接入 receipt 写入失败：${error.message ?: "未知错误"}", error))
            }
        )
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

    private fun removeIdeReceipts(): Result<Unit> = removeReceipts(listOf(ideReceiptFile()))

    private fun removeEnvironmentReceipts(): Result<Unit> =
        removeReceipts(listOf(environmentReceiptFile()))

    private fun removeReceipts(files: List<File>): Result<Unit> {
        files.forEach { file ->
            removeReceipt(file).getOrElse { error -> return Result.failure(error) }
        }
        return Result.success(Unit)
    }

    private fun removeReceipt(file: File): Result<Unit> {
        return try {
            Files.deleteIfExists(file.toPath())
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
        return AtomicFileWriter.writeText(
            target = file,
            content = content,
            permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
            disallowSymlinks = true
        ).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                Result.failure(IllegalStateException("写入宿主配置失败：${error.message ?: "未知错误"}", error))
            }
        )
    }

    private val IDE_ENDPOINT_REGEX = Regex(
        "\\\"$IDE_SETTING_KEY\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
    )
    private val IDE_LINE_REGEX = Regex(
        "(?m)^[ \\t]*\\\"$IDE_SETTING_KEY\\\"\\s*:\\s*\\\"[^\\\"]*\\\"\\s*,?[ \\t]*\\r?\\n?"
    )
}
