package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 系统级凭据注入器（针对各操作系统安全存储的凭据注入）。
 *
 * 在 macOS 上，Antigravity App 内置的 language_server 通过 Go 的 `zalando/go-keyring`
 * 将 OAuth 凭据持久化在 macOS Keychain（钥匙串）中，Service 名为 "gemini"，Account 名为 "antigravity"。
 *
 * 如果切号时只修改磁盘上的 JSON 文件，而未同步更新 Keychain，App 重启后 language_server
 * 依然会优先从 Keychain 读取旧账号凭据，导致切号无法生效。
 */
object SystemCredentialInjector {

    private const val TAG = "Auth/Keychain"
    private val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    /**
     * 将目标账号的凭据注入到系统级安全存储中。
     */
    fun inject(account: AccountInfo): Result<Unit> {
        return runCatching {
            if (isMac) {
                injectMacKeychain(account)
            } else {
                AppLog.d(TAG) { "当前平台无需独立 Keychain 注入" }
            }
        }
    }

    /**
     * macOS 钥匙串注入：
     * 执行 `security add-generic-password -U -s gemini -a antigravity -w <payload>`
     * payload 格式为 `go-keyring-base64:<base64-json>`
     */
    private fun injectMacKeychain(account: AccountInfo) {
        val expiryIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(account.tokens.expiryTimestamp * 1000L))

        val tokenJson = buildJsonObject {
            putJsonObject("token") {
                put("access_token", account.tokens.accessToken)
                put("token_type", "Bearer")
                put("refresh_token", account.tokens.refreshToken)
                put("expiry", expiryIso)
            }
            put("auth_method", "consumer")
        }.toString()

        val base64Payload = Base64.getEncoder().encodeToString(tokenJson.toByteArray(Charsets.UTF_8))
        val keychainSecret = "go-keyring-base64:$base64Payload"

        val process = ProcessBuilder(
            "/usr/bin/security",
            "add-generic-password",
            "-U",
            "-s", "gemini",
            "-a", "antigravity",
            "-w", keychainSecret
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val errorText = process.errorStream.bufferedReader().readText()
            AppLog.e(TAG) { "macOS Keychain 注入失败: exitCode=$exitCode, err=$errorText" }
            throw IllegalStateException("macOS Keychain 注入失败 (code=$exitCode): $errorText")
        }

        AppLog.i(TAG) { "已成功将账号 ${AppLog.maskEmail(account.email)} 凭据写入 macOS Keychain (service=gemini, account=antigravity)" }
    }
}
