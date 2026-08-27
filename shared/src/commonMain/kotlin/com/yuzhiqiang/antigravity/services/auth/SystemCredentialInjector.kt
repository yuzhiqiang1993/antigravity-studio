package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 系统级凭据注入器（针对各操作系统安全存储的凭据注入）。
 *
 * 在 macOS 上，Antigravity App 内置的 language_server 通过 Go 的 `zalando/go-keyring`
 * 将 OAuth 凭据持久化在 macOS Keychain（钥匙串）中，Service 名为 "gemini"，Account 名为 "antigravity"。
 *
 * 在 Windows 上，`go-keyring` 使用 Windows Credential Manager（wincred），
 * 以 CRED_TYPE_GENERIC 类型存储，TargetName 为 "gemini"，UserName 为 "antigravity"，
 * CredentialBlob 为 UTF-8 编码的 `go-keyring-base64:<base64-json>` 字符串。
 *
 * 如果切号时只修改磁盘上的 JSON 文件，而未同步更新系统凭据存储，App 重启后 language_server
 * 依然会优先从系统凭据存储读取旧账号凭据，导致切号无法生效。
 */
object SystemCredentialInjector {

    private const val TAG = "Auth/Keychain"
    private const val CREDENTIAL_SERVICE = "gemini"
    private const val CREDENTIAL_ACCOUNT = "antigravity"
    private const val COMMAND_TIMEOUT_MILLIS = 10_000L

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isMac = osName.contains("mac")
    private val isWindows = osName.contains("win")

    /**
     * 将目标账号的凭据注入到系统级安全存储中。
     */
    fun inject(account: AccountInfo): Result<Unit> {
        return runCatching {
            when {
                isMac -> injectMacKeychain(account)
                isWindows -> injectWindowsCredentialManager(account)
                else -> AppLog.d(TAG) { "当前平台 (Linux) 无需独立系统凭据注入" }
            }
        }
    }

    /**
     * 构建对齐 Go go-keyring 格式的凭据有效载荷。
     * 格式：`go-keyring-base64:<base64-json>`
     */
    private fun buildCredentialPayload(account: AccountInfo): String {
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
        return "go-keyring-base64:$base64Payload"
    }

    /**
     * macOS 钥匙串注入：
     * 执行 `security add-generic-password -U -s gemini -a antigravity -w <payload>`
     */
    private fun injectMacKeychain(account: AccountInfo) {
        val keychainSecret = buildCredentialPayload(account)

        val process = ProcessBuilder(
            "/usr/bin/security",
            "add-generic-password",
            "-U",
            "-s", CREDENTIAL_SERVICE,
            "-a", CREDENTIAL_ACCOUNT,
            "-w", keychainSecret
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val errorText = process.errorStream.bufferedReader().readText()
            AppLog.e(TAG) { "macOS Keychain 注入失败: exitCode=$exitCode, err=$errorText" }
            throw IllegalStateException("macOS Keychain 注入失败 (code=$exitCode): $errorText")
        }

        AppLog.i(TAG) { "已成功将账号 ${AppLog.maskEmail(account.email)} 凭据写入 macOS Keychain (service=$CREDENTIAL_SERVICE, account=$CREDENTIAL_ACCOUNT)" }
    }

    /**
     * Windows Credential Manager 注入：
     * 通过 PowerShell + P/Invoke advapi32.dll CredWriteW 以原始 UTF-8 字节写入，
     * 对齐 Go `go-keyring` 使用 `wincred` 包的存储格式（CRED_TYPE_GENERIC）。
     *
     * 不使用 `cmdkey` 的原因：`cmdkey` 将密码按 UTF-16 存储，而 `go-keyring` 按 UTF-8 读取，
     * 编码不匹配会导致 language_server 读到乱码。
     */
    private fun injectWindowsCredentialManager(account: AccountInfo) {
        val credentialSecret = buildCredentialPayload(account)

        // 将凭据有效载荷写入临时文件，避免通过命令行参数泄露敏感信息
        val secretFile = File.createTempFile("agy-cred-", ".tmp")
        val scriptFile = File.createTempFile("agy-wincred-", ".ps1")
        try {
            secretFile.writeBytes(credentialSecret.toByteArray(Charsets.UTF_8))
            scriptFile.writeText(
                buildWinCredScript(secretFile.absolutePath),
                Charsets.UTF_8
            )

            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", scriptFile.absolutePath
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                throw IllegalStateException("Windows Credential Manager 注入超时")
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                AppLog.e(TAG) { "Windows Credential Manager 注入失败: exitCode=$exitCode, output=$output" }
                throw IllegalStateException("Windows Credential Manager 注入失败 (code=$exitCode): $output")
            }

            AppLog.i(TAG) { "已成功将账号 ${AppLog.maskEmail(account.email)} 凭据写入 Windows Credential Manager (target=$CREDENTIAL_SERVICE, user=$CREDENTIAL_ACCOUNT)" }
        } finally {
            secretFile.delete()
            scriptFile.delete()
        }
    }

    /**
     * 生成 PowerShell 脚本，通过 P/Invoke 调用 advapi32.dll CredWriteW
     * 以原始 UTF-8 字节写入 CRED_TYPE_GENERIC 凭据。
     */
    private fun buildWinCredScript(secretFilePath: String): String {
        val escapedPath = secretFilePath.replace("'", "''")
        return buildString {
            appendLine("\$ErrorActionPreference = 'Stop'")
            appendLine("\$blob = [System.IO.File]::ReadAllBytes('$escapedPath')")
            appendLine()
            // PowerShell here-string (@"..."@) 内 C# 双引号为字面量，无需转义
            appendLine("Add-Type -TypeDefinition @\"")
            appendLine("using System;")
            appendLine("using System.Runtime.InteropServices;")
            appendLine("public class WinCredHelper {")
            appendLine("    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]")
            appendLine("    public struct CREDENTIAL {")
            appendLine("        public int Flags;")
            appendLine("        public int Type;")
            appendLine("        public string TargetName;")
            appendLine("        public string Comment;")
            appendLine("        public long LastWritten;")
            appendLine("        public int CredentialBlobSize;")
            appendLine("        public IntPtr CredentialBlob;")
            appendLine("        public int Persist;")
            appendLine("        public int AttributeCount;")
            appendLine("        public IntPtr Attributes;")
            appendLine("        public string TargetAlias;")
            appendLine("        public string UserName;")
            appendLine("    }")
            appendLine("    [DllImport(\"advapi32.dll\", CharSet = CharSet.Unicode, SetLastError = true)]")
            appendLine("    public static extern bool CredWrite(ref CREDENTIAL cred, int flags);")
            appendLine("    public static void WriteGeneric(string target, string user, byte[] secret) {")
            appendLine("        var c = new CREDENTIAL();")
            appendLine("        c.Type = 1;")  // CRED_TYPE_GENERIC
            appendLine("        c.TargetName = target;")
            appendLine("        c.UserName = user;")
            appendLine("        c.Persist = 2;")  // CRED_PERSIST_LOCAL_MACHINE
            appendLine("        c.CredentialBlobSize = secret.Length;")
            appendLine("        c.CredentialBlob = Marshal.AllocHGlobal(secret.Length);")
            appendLine("        try {")
            appendLine("            Marshal.Copy(secret, 0, c.CredentialBlob, secret.Length);")
            appendLine("            if (!CredWrite(ref c, 0)) {")
            appendLine("                throw new Exception(\"CredWrite failed: error \" + Marshal.GetLastWin32Error());")
            appendLine("            }")
            appendLine("        } finally {")
            appendLine("            Marshal.FreeHGlobal(c.CredentialBlob);")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            // PowerShell here-string 闭合标记必须在行首
            appendLine("\"@")
            appendLine()
            appendLine("[WinCredHelper]::WriteGeneric('$CREDENTIAL_SERVICE', '$CREDENTIAL_ACCOUNT', \$blob)")
        }
    }
}
