package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * macOS Keychain 安全凭据注入器。
 * 将当前激活账号的 OAuth Token 以 Go-keyring 标准格式写入 macOS 系统的通用密码链中，
 * 供最新版本 Antigravity App (>= 2.0.0) 原生透明读取。
 */
object MacKeychainInjector {

    private val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
    private val keyringJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Keychain 条目存在性及原始值快照；凭据内容不对调用方暴露。
     */
    class Snapshot internal constructor(
        internal val existed: Boolean,
        internal val keyringValue: String?
    )

    private class SecurityCommandResult(
        val exitCode: Int? = null,
        val output: String? = null,
        val isTimedOut: Boolean = false,
        val exception: Exception? = null
    )

    /**
     * 将账号凭据注入 macOS Keychain
     */
    fun inject(account: AccountInfo, appInstallationPath: String? = null): Boolean {
        return inject(account.tokens, appInstallationPath)
    }

    /**
     * 将 OAuthTokens 格式化并注入 macOS Keychain
     */
    fun inject(tokens: OAuthTokens, appInstallationPath: String? = null): Boolean {
        if (!isMac) {
            return false
        }
        if (tokens.refreshToken.isBlank()) {
            return false
        }

        try {
            val expiryIso = formatIso8601(tokens.expiryTimestamp * 1000L)
            val jsonPayload = buildJsonObject {
                putJsonObject("token") {
                    put("access_token", tokens.accessToken)
                    put("token_type", "Bearer")
                    put("refresh_token", tokens.refreshToken)
                    put("expiry", expiryIso)
                }
                put("auth_method", "consumer")
            }.toString()

            val base64Payload = Base64.getEncoder().encodeToString(jsonPayload.toByteArray(Charsets.UTF_8))
            val fullKeyringValue = "go-keyring-base64:$base64Payload"

            // -U 原子更新旧项；失败时由 Keychain 保留原凭据，且只授权 App 正式可执行文件。
            return updateKeychainValue(fullKeyringValue, appInstallationPath)
        } catch (exception: Exception) {
            System.err.println("更新 macOS Keychain 凭据失败")
            exception.printStackTrace(System.err)
            return false
        }
    }

    /**
     * 在用户明确发起的交互操作中，从 Antigravity Keychain 条目匹配 Studio 账号。
     *
     * Keychain 读取可能触发 macOS 授权提示。读取命令失败时返回 [Result.failure]；条目不存在、格式无法识别或
     * Refresh Token 不属于 [knownAccounts] 时返回 [Result.success]，其值为 `null`。凭据内容只在本方法内部处理，
     * 不会写入日志或返回给调用方。
     */
    fun readMatchingAccount(knownAccounts: List<AccountInfo>): Result<AccountInfo?> {
        return readKeychainValue().fold(
            onSuccess = { keyringValue ->
                val refreshToken = keyringValue?.let(::decodeRefreshToken)
                val matchedAccount = refreshToken?.let { token ->
                    knownAccounts.firstOrNull { account ->
                        account.tokens.refreshToken.isNotBlank() && account.tokens.refreshToken == token
                    }
                }
                Result.success(matchedAccount)
            },
            onFailure = { exception -> Result.failure(exception) }
        )
    }

    /**
     * 捕获当前 Keychain 条目；条目不存在属于成功快照，读取失败返回 Result.failure。
     */
    fun capture(): Result<Snapshot> {
        return readKeychainValue().fold(
            onSuccess = { keyringValue ->
                Result.success(
                    if (keyringValue == null) {
                        Snapshot(existed = false, keyringValue = null)
                    } else {
                        Snapshot(existed = true, keyringValue = keyringValue)
                    }
                )
            },
            onFailure = { exception -> Result.failure(exception) }
        )
    }

    private fun readKeychainValue(): Result<String?> {
        if (!isMac) {
            return Result.failure(UnsupportedOperationException("仅 macOS 支持 Keychain 读取"))
        }

        val result = runSecurityCommand(
            listOf(
                "/usr/bin/security",
                "find-generic-password",
                "-s", KEYCHAIN_SERVICE,
                "-a", KEYCHAIN_ACCOUNT,
                "-w"
            ),
            captureOutput = true
        )
        result.exception?.let { exception ->
            return Result.failure(IOException("读取 macOS Keychain 条目失败", exception))
        }
        if (result.isTimedOut) {
            return Result.failure(IOException("读取 macOS Keychain 条目超时"))
        }

        return when (result.exitCode) {
            0 -> Result.success(removeTrailingLineEnding(result.output.orEmpty()))
            ITEM_NOT_FOUND_EXIT_CODE -> Result.success(null)
            else -> Result.failure(
                IOException("读取 macOS Keychain 条目失败: exitCode=${result.exitCode}")
            )
        }
    }

    private fun decodeRefreshToken(keyringValue: String): String? {
        val encodedPayload = keyringValue
            .removePrefix(KEYRING_PREFIX)
            .takeIf { keyringValue.startsWith(KEYRING_PREFIX) }
            ?.takeIf { it.isNotBlank() && it.length <= MAX_KEYRING_VALUE_LENGTH }
            ?: return null
        val decodedPayload = try {
            Base64.getDecoder().decode(encodedPayload)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decodedPayload.size > MAX_KEYRING_PAYLOAD_BYTES) {
            return null
        }

        return try {
            val root = keyringJson.parseToJsonElement(decodedPayload.toString(Charsets.UTF_8)) as? JsonObject
                ?: return null
            val tokenObject = root["token"] as? JsonObject ?: root
            tokenObject["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { token -> token.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 恢复原 Keychain 条目；原条目不存在时仅删除本次切号创建的同标识条目。
     */
    fun restore(snapshot: Snapshot, appInstallationPath: String? = null): Boolean {
        if (!isMac) {
            return false
        }
        if (snapshot.existed) {
            val originalValue = snapshot.keyringValue ?: return false
            return updateKeychainValue(originalValue, appInstallationPath)
        }

        val result = runSecurityCommand(
            listOf(
                "/usr/bin/security",
                "delete-generic-password",
                "-s", "gemini",
                "-a", "antigravity"
            )
        )
        return !result.isTimedOut && result.exception == null &&
                (result.exitCode == 0 || result.exitCode == ITEM_NOT_FOUND_EXIT_CODE)
    }

    private fun updateKeychainValue(value: String, appInstallationPath: String?): Boolean {
        val trustedAppExecutables = resolveTrustedAppExecutables(appInstallationPath)
        if (trustedAppExecutables.isEmpty()) {
            return false
        }
        val result = runSecurityCommand(
            buildSecurityCommand(trustedAppExecutables),
            password = value
        )
        return !result.isTimedOut && result.exception == null && result.exitCode == 0
    }

    private fun resolveTrustedAppExecutables(appInstallationPath: String?): List<File> {
        val userHome = System.getProperty("user.home")
        val standardAppBundles = listOf(
            File("/Applications/Antigravity.app"),
            File(userHome, "Applications/Antigravity.app")
        )
        val configuredAppBundle = resolveConfiguredAppBundle(appInstallationPath)
        val appBundles = standardAppBundles + listOfNotNull(configuredAppBundle)
        return appBundles
            .flatMap { appBundle ->
                listOf(
                    File(appBundle, APP_EXECUTABLE_RELATIVE_PATH),
                    File(appBundle, LANGUAGE_SERVER_EXECUTABLE_RELATIVE_PATH)
                )
            }
            .filter { candidate -> candidate.isFile && candidate.canExecute() }
            .distinctBy { candidate -> candidate.absolutePath }
    }

    private fun resolveConfiguredAppBundle(appInstallationPath: String?): File? {
        val configuredPath = appInstallationPath?.trim()?.takeIf { path -> path.isNotEmpty() } ?: return null
        val configuredFile = File(configuredPath)
        val appBundle = when {
            configuredFile.name.endsWith(".app") -> configuredFile
            configuredFile.name == "Antigravity" && configuredFile.parentFile?.name == "MacOS" -> {
                configuredFile.parentFile?.parentFile?.parentFile
            }
            else -> null
        }
        return appBundle?.takeIf { candidate -> candidate.isDirectory }
    }

    private fun buildSecurityCommand(trustedAppExecutables: List<File>): List<String> {
        val command = mutableListOf(
            "/usr/bin/security",
            "add-generic-password",
            "-s", "gemini",
            "-a", "antigravity",
            "-U"
        )
        for (executable in trustedAppExecutables) {
            command.add("-T")
            command.add(executable.absolutePath)
        }
        command.add("-w")
        return command
    }

    private fun runSecurityCommand(
        command: List<String>,
        password: String? = null,
        captureOutput: Boolean = false
    ): SecurityCommandResult {
        var process: Process? = null
        return try {
            val outputRedirect = if (captureOutput) {
                ProcessBuilder.Redirect.PIPE
            } else {
                ProcessBuilder.Redirect.DISCARD
            }
            process = ProcessBuilder(command)
                .redirectOutput(outputRedirect)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            if (password != null) {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(password)
                    writer.newLine()
                }
            }

            val isCompleted = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!isCompleted) {
                process.destroyForcibly()
                SecurityCommandResult(isTimedOut = true)
            } else {
                val output = if (captureOutput) {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                } else {
                    null
                }
                SecurityCommandResult(exitCode = process.exitValue(), output = output)
            }
        } catch (exception: Exception) {
            process?.destroyForcibly()
            SecurityCommandResult(exception = exception)
        }
    }

    private fun removeTrailingLineEnding(value: String): String {
        return when {
            value.endsWith("\r\n") -> value.dropLast(2)
            value.endsWith("\n") -> value.dropLast(1)
            else -> value
        }
    }

    private fun formatIso8601(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private const val APP_EXECUTABLE_RELATIVE_PATH = "Contents/MacOS/Antigravity"
    private const val LANGUAGE_SERVER_EXECUTABLE_RELATIVE_PATH = "Contents/Resources/bin/language_server"
    private const val KEYCHAIN_SERVICE = "gemini"
    private const val KEYCHAIN_ACCOUNT = "antigravity"
    private const val KEYRING_PREFIX = "go-keyring-base64:"
    private const val MAX_KEYRING_VALUE_LENGTH = 256 * 1024
    private const val MAX_KEYRING_PAYLOAD_BYTES = 128 * 1024
    private const val COMMAND_TIMEOUT_SECONDS = 3L
    private const val ITEM_NOT_FOUND_EXIT_CODE = 44
}
