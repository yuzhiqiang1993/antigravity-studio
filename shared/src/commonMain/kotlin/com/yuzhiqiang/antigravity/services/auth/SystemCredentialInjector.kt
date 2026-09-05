package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal enum class SystemCredentialBackend {
    MACOS_KEYCHAIN,
    WINDOWS_CREDENTIAL_MANAGER,
    NONE
}

internal sealed interface SystemCredentialSnapshot : AutoCloseable {
    val backend: SystemCredentialBackend

    data object NoOp : SystemCredentialSnapshot {
        override val backend: SystemCredentialBackend = SystemCredentialBackend.NONE
        override fun close() = Unit
    }

    class Absent(
        override val backend: SystemCredentialBackend
    ) : SystemCredentialSnapshot {
        override fun close() = Unit
        override fun toString(): String = "SystemCredentialSnapshot.Absent(backend=$backend)"
    }

    class Present(
        override val backend: SystemCredentialBackend,
        internal val secret: ByteArray,
        internal val userName: String = CREDENTIAL_ACCOUNT,
        internal val persist: Int = WINDOWS_PERSIST_LOCAL_MACHINE,
        internal val targetName: String = CREDENTIAL_TARGET_KEYRING
    ) : SystemCredentialSnapshot {
        override fun close() {
            secret.fill(0)
        }

        override fun toString(): String {
            return "SystemCredentialSnapshot.Present(backend=$backend, secret=<redacted>)"
        }
    }

    companion object {
        internal const val CREDENTIAL_SERVICE = "gemini"
        internal const val CREDENTIAL_ACCOUNT = "antigravity"
        internal const val CREDENTIAL_TARGET_KEYRING = "gemini:antigravity"
        internal const val WINDOWS_PERSIST_LOCAL_MACHINE = 2
    }
}

internal interface SystemCredentialStore {
    fun capture(): Result<SystemCredentialSnapshot>
    fun inject(account: AccountInfo): Result<Unit>
    fun restore(snapshot: SystemCredentialSnapshot): Result<Unit>
}

/**
 * App language server 使用的系统级安全凭据存储。
 *
 * macOS 对应 Keychain generic password；Windows 对应 Credential Manager generic credential；
 * 其他平台不需要独立系统凭据。所有错误只暴露操作和退出码，不携带 secret 或命令输出。
 */
internal object SystemCredentialInjector : SystemCredentialStore {

    private const val TAG = "Auth/Keychain"
    private const val CREDENTIAL_SERVICE = "gemini"
    private const val CREDENTIAL_ACCOUNT = "antigravity"
    private const val CREDENTIAL_TARGET_KEYRING = "gemini:antigravity"
    private const val COMMAND_TIMEOUT_MILLIS = 10_000L
    private const val MAC_ITEM_NOT_FOUND_EXIT_CODE = 44
    private const val WINDOWS_CREDENTIAL_NOT_FOUND_EXIT_CODE = 3
    private const val ERROR_NOT_FOUND = 1168

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isMac = osName.contains("mac") || osName.contains("darwin")
    private val isWindows = !isMac && osName.contains("win")

    override fun capture(): Result<SystemCredentialSnapshot> {
        return runCatching {
            when {
                isMac -> captureMacKeychain()
                isWindows -> captureWindowsCredentialManager()
                else -> SystemCredentialSnapshot.NoOp
            }
        }
    }

    override fun inject(account: AccountInfo): Result<Unit> {
        return runCatching {
            when {
                isMac -> withCredentialPayload(account) { secret ->
                    writeMacKeychain(secret)
                }

                isWindows -> withCredentialPayload(account) { secret ->
                    writeWindowsCredential(
                        secret = secret,
                        userName = CREDENTIAL_ACCOUNT,
                        persist = SystemCredentialSnapshot.WINDOWS_PERSIST_LOCAL_MACHINE,
                        targetName = CREDENTIAL_TARGET_KEYRING
                    )
                }

                else -> AppLog.d(TAG) { "当前平台无需独立系统凭据注入" }
            }
            AppLog.i(TAG) {
                "已写入系统凭据: backend=${currentBackend()}, account=${AppLog.maskEmail(account.email)}"
            }
        }
    }

    override fun restore(snapshot: SystemCredentialSnapshot): Result<Unit> {
        return runCatching {
            when (snapshot) {
                SystemCredentialSnapshot.NoOp -> Unit
                is SystemCredentialSnapshot.Absent -> {
                    requireBackend(snapshot.backend)
                    when (snapshot.backend) {
                        SystemCredentialBackend.MACOS_KEYCHAIN -> deleteMacKeychain()
                        SystemCredentialBackend.WINDOWS_CREDENTIAL_MANAGER -> deleteWindowsCredential()
                        SystemCredentialBackend.NONE -> Unit
                    }
                }

                is SystemCredentialSnapshot.Present -> {
                    requireBackend(snapshot.backend)
                    when (snapshot.backend) {
                        SystemCredentialBackend.MACOS_KEYCHAIN -> writeMacKeychain(snapshot.secret)
                        SystemCredentialBackend.WINDOWS_CREDENTIAL_MANAGER -> writeWindowsCredential(
                            secret = snapshot.secret,
                            userName = snapshot.userName,
                            persist = snapshot.persist,
                            targetName = snapshot.targetName
                        )

                        SystemCredentialBackend.NONE -> Unit
                    }
                }
            }
            AppLog.i(TAG) { "已恢复系统凭据: backend=${snapshot.backend}" }
        }
    }

    private fun currentBackend(): SystemCredentialBackend = when {
        isMac -> SystemCredentialBackend.MACOS_KEYCHAIN
        isWindows -> SystemCredentialBackend.WINDOWS_CREDENTIAL_MANAGER
        else -> SystemCredentialBackend.NONE
    }

    private fun requireBackend(snapshotBackend: SystemCredentialBackend) {
        val actualBackend = currentBackend()
        check(snapshotBackend == actualBackend || snapshotBackend == SystemCredentialBackend.NONE) {
            "系统凭据快照平台不匹配: snapshot=$snapshotBackend, actual=$actualBackend"
        }
    }

    private fun captureMacKeychain(): SystemCredentialSnapshot {
        val result = runProcess(
            listOf(
                "/usr/bin/security",
                "find-generic-password",
                "-s", CREDENTIAL_SERVICE,
                "-a", CREDENTIAL_ACCOUNT,
                "-w"
            )
        )
        return when (result.exitCode) {
            0 -> {
                val secret = result.output.removeSingleLineEnding()
                result.output.fill(0)
                SystemCredentialSnapshot.Present(
                    backend = SystemCredentialBackend.MACOS_KEYCHAIN,
                    secret = secret
                )
            }

            MAC_ITEM_NOT_FOUND_EXIT_CODE -> SystemCredentialSnapshot.Absent(
                SystemCredentialBackend.MACOS_KEYCHAIN
            )

            else -> error("macOS Keychain 凭据捕获失败: exitCode=${result.exitCode}")
        }
    }

    private fun writeMacKeychain(secret: ByteArray) {
        val input = secret + byteArrayOf('\n'.code.toByte())
        try {
            val result = runProcess(
                command = listOf(
                    "/usr/bin/security",
                    "add-generic-password",
                    "-U",
                    "-s", CREDENTIAL_SERVICE,
                    "-a", CREDENTIAL_ACCOUNT,
                    "-w"
                ),
                standardInput = input
            )
            check(result.exitCode == 0) {
                "macOS Keychain 凭据写入失败: exitCode=${result.exitCode}"
            }
        } finally {
            input.fill(0)
        }
    }

    private fun deleteMacKeychain() {
        val result = runProcess(
            listOf(
                "/usr/bin/security",
                "delete-generic-password",
                "-s", CREDENTIAL_SERVICE,
                "-a", CREDENTIAL_ACCOUNT
            )
        )
        check(result.exitCode == 0 || result.exitCode == MAC_ITEM_NOT_FOUND_EXIT_CODE) {
            "macOS Keychain 凭据删除失败: exitCode=${result.exitCode}"
        }
    }

    private fun captureWindowsCredentialManager(): SystemCredentialSnapshot {
        val scriptFile = createSecureTempFile("agy-wincred-capture-", ".ps1")
        return try {
            writePowerShellScript(scriptFile, buildWinCredCaptureScript())
            val result = runPowerShell(scriptFile)
            when (result.exitCode) {
                0 -> try {
                    parseWindowsCredentialSnapshot(result.output)
                } finally {
                    result.output.fill(0)
                }

                WINDOWS_CREDENTIAL_NOT_FOUND_EXIT_CODE -> SystemCredentialSnapshot.Absent(
                    SystemCredentialBackend.WINDOWS_CREDENTIAL_MANAGER
                )

                else -> error("Windows Credential Manager 凭据捕获失败: exitCode=${result.exitCode}")
            }
        } finally {
            scriptFile.delete()
        }
    }

    private fun parseWindowsCredentialSnapshot(output: ByteArray): SystemCredentialSnapshot.Present {
        val line = output.toString(Charsets.UTF_8).lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("PRESENT|") }
            ?: error("Windows Credential Manager 捕获结果格式错误")
        val parts = line.split('|', limit = 5)
        check(parts.size >= 4) { "Windows Credential Manager 捕获结果字段缺失" }
        val persist = parts[1].toIntOrNull()
            ?: error("Windows Credential Manager persist 字段无效")
        val userName = Base64.getDecoder().decode(parts[2]).toString(Charsets.UTF_8)
        val secret = Base64.getDecoder().decode(parts[3])
        val targetName = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4] else CREDENTIAL_TARGET_KEYRING
        return SystemCredentialSnapshot.Present(
            backend = SystemCredentialBackend.WINDOWS_CREDENTIAL_MANAGER,
            secret = secret,
            userName = userName,
            persist = persist,
            targetName = targetName
        )
    }

    private fun writeWindowsCredential(
        secret: ByteArray,
        userName: String,
        persist: Int,
        targetName: String = CREDENTIAL_TARGET_KEYRING
    ) {
        val secretFile = createSecureTempFile("agy-cred-", ".tmp")
        val scriptFile = createSecureTempFile("agy-wincred-write-", ".ps1")
        try {
            secretFile.writeBytes(secret)
            writePowerShellScript(
                scriptFile,
                buildWinCredWriteScript(secretFile.absolutePath, userName, persist, targetName)
            )
            val result = runPowerShell(scriptFile)
            check(result.exitCode == 0) {
                "Windows Credential Manager 凭据写入失败: exitCode=${result.exitCode}"
            }
        } finally {
            secretFile.writeBytes(ByteArray(secretFile.length().toInt()))
            secretFile.delete()
            scriptFile.delete()
        }
    }

    private fun deleteWindowsCredential(targetName: String = CREDENTIAL_TARGET_KEYRING) {
        val scriptFile = createSecureTempFile("agy-wincred-delete-", ".ps1")
        try {
            writePowerShellScript(scriptFile, buildWinCredDeleteScript(targetName))
            val result = runPowerShell(scriptFile)
            check(result.exitCode == 0 || result.exitCode == WINDOWS_CREDENTIAL_NOT_FOUND_EXIT_CODE) {
                "Windows Credential Manager 凭据删除失败: exitCode=${result.exitCode}"
            }
        } finally {
            scriptFile.delete()
        }
    }

    private fun writePowerShellScript(file: File, content: String) {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        file.writeBytes(utf8Bom + content.toByteArray(Charsets.UTF_8))
    }

    private fun runPowerShell(scriptFile: File): ProcessExecutionResult {
        return runProcess(
            listOf(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.absolutePath
            )
        )
    }

    private fun createSecureTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix).apply {
            if (!isWindows) {
                setReadable(false, false)
                setWritable(false, false)
                setExecutable(false, false)
                setReadable(true, true)
                setWritable(true, true)
            }
        }
    }

    private inline fun <T> withCredentialPayload(account: AccountInfo, block: (ByteArray) -> T): T {
        val secret = buildCredentialPayload(account).toByteArray(Charsets.UTF_8)
        return try {
            block(secret)
        } finally {
            secret.fill(0)
        }
    }

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

        if (isWindows) {
            return tokenJson
        }

        val base64Payload = Base64.getEncoder().encodeToString(tokenJson.toByteArray(Charsets.UTF_8))
        return "go-keyring-base64:$base64Payload"
    }

    private fun buildWinCredCaptureScript(): String {
        return buildString {
            appendLine("\$ErrorActionPreference = 'Stop'")
            appendLine("Add-Type -TypeDefinition @\"")
            appendLine("using System;")
            appendLine("using System.Runtime.InteropServices;")
            appendLine("public class WinCredCapture {")
            appendLine("    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]")
            appendLine("    public struct CREDENTIAL {")
            appendLine("        public int Flags; public int Type; public string TargetName; public string Comment;")
            appendLine("        public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob;")
            appendLine("        public int Persist; public int AttributeCount; public IntPtr Attributes;")
            appendLine("        public string TargetAlias; public string UserName;")
            appendLine("    }")
            appendLine("    [DllImport(\"advapi32.dll\", CharSet = CharSet.Unicode, SetLastError = true)]")
            appendLine("    public static extern bool CredRead(string target, int type, int flags, out IntPtr credential);")
            appendLine("    [DllImport(\"advapi32.dll\")] public static extern void CredFree(IntPtr credential);")
            appendLine("}")
            appendLine("\"@")
            appendLine("\$pointer = [IntPtr]::Zero")
            appendLine("if (-not [WinCredCapture]::CredRead('$CREDENTIAL_TARGET_KEYRING', 1, 0, [ref]\$pointer)) {")
            appendLine("    if (-not [WinCredCapture]::CredRead('$CREDENTIAL_SERVICE', 1, 0, [ref]\$pointer)) {")
            appendLine("        if ([Runtime.InteropServices.Marshal]::GetLastWin32Error() -eq 1168) { exit 3 }")
            appendLine("        exit 4")
            appendLine("    }")
            appendLine("}")
            appendLine("try {")
            appendLine("    \$credential = [Runtime.InteropServices.Marshal]::PtrToStructure(\$pointer, [type][WinCredCapture+CREDENTIAL])")
            appendLine("    if (\$credential.Flags -ne 0 -or \$credential.AttributeCount -ne 0 -or \$credential.Comment -or \$credential.TargetAlias) { exit 5 }")
            appendLine("    \$blob = New-Object byte[] \$credential.CredentialBlobSize")
            appendLine("    if (\$blob.Length -gt 0) { [Runtime.InteropServices.Marshal]::Copy(\$credential.CredentialBlob, \$blob, 0, \$blob.Length) }")
            appendLine("    \$user = [Text.Encoding]::UTF8.GetBytes([string]\$credential.UserName)")
            appendLine("    \$target = \$credential.TargetName")
            appendLine("    Write-Output ('PRESENT|' + \$credential.Persist + '|' + [Convert]::ToBase64String(\$user) + '|' + [Convert]::ToBase64String(\$blob) + '|' + \$target)")
            appendLine("} finally { [WinCredCapture]::CredFree(\$pointer) }")
        }
    }

    private fun buildWinCredWriteScript(
        secretFilePath: String,
        userName: String,
        persist: Int,
        targetName: String = CREDENTIAL_TARGET_KEYRING
    ): String {
        val escapedPath = secretFilePath.replace("'", "''")
        val escapedUser = userName.replace("'", "''")
        val escapedTarget = targetName.replace("'", "''")
        return buildString {
            appendLine("\$ErrorActionPreference = 'Stop'")
            appendLine("\$blob = [System.IO.File]::ReadAllBytes('$escapedPath')")
            appendLine("Add-Type -TypeDefinition @\"")
            appendLine("using System;")
            appendLine("using System.Runtime.InteropServices;")
            appendLine("public class WinCredWriter {")
            appendLine("    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]")
            appendLine("    public struct CREDENTIAL {")
            appendLine("        public int Flags; public int Type; public string TargetName; public string Comment;")
            appendLine("        public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob;")
            appendLine("        public int Persist; public int AttributeCount; public IntPtr Attributes;")
            appendLine("        public string TargetAlias; public string UserName;")
            appendLine("    }")
            appendLine("    [DllImport(\"advapi32.dll\", CharSet = CharSet.Unicode, SetLastError = true)]")
            appendLine("    public static extern bool CredWrite(ref CREDENTIAL cred, int flags);")
            appendLine("}")
            appendLine("\"@")
            appendLine("\$credential = New-Object WinCredWriter+CREDENTIAL")
            appendLine("\$credential.Type = 1")
            appendLine("\$credential.TargetName = '$escapedTarget'")
            appendLine("\$credential.UserName = '$escapedUser'")
            appendLine("\$credential.Persist = $persist")
            appendLine("\$credential.CredentialBlobSize = \$blob.Length")
            appendLine("\$credential.CredentialBlob = [Runtime.InteropServices.Marshal]::AllocHGlobal(\$blob.Length)")
            appendLine("try {")
            appendLine("    if (\$blob.Length -gt 0) { [Runtime.InteropServices.Marshal]::Copy(\$blob, 0, \$credential.CredentialBlob, \$blob.Length) }")
            appendLine("    if (-not [WinCredWriter]::CredWrite([ref]\$credential, 0)) { exit 4 }")
            appendLine("} finally { [Runtime.InteropServices.Marshal]::FreeHGlobal(\$credential.CredentialBlob) }")
        }
    }

    private fun buildWinCredDeleteScript(targetName: String = CREDENTIAL_TARGET_KEYRING): String {
        val escapedTarget = targetName.replace("'", "''")
        return buildString {
            appendLine("Add-Type -TypeDefinition @\"")
            appendLine("using System.Runtime.InteropServices;")
            appendLine("public class WinCredDelete {")
            appendLine("    [DllImport(\"advapi32.dll\", CharSet = CharSet.Unicode, SetLastError = true)]")
            appendLine("    public static extern bool CredDelete(string target, int type, int flags);")
            appendLine("}")
            appendLine("\"@")
            appendLine("[WinCredDelete]::CredDelete('$CREDENTIAL_SERVICE', 1, 0) | Out-Null")
            appendLine("if (-not [WinCredDelete]::CredDelete('$escapedTarget', 1, 0)) {")
            appendLine("    if ([Runtime.InteropServices.Marshal]::GetLastWin32Error() -eq 1168) { exit 3 }")
            appendLine("    exit 4")
            appendLine("}")
        }
    }

    private fun runProcess(
        command: List<String>,
        standardInput: ByteArray? = null
    ): ProcessExecutionResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = ByteArrayOutputStream()
        var outputError: Throwable? = null
        val reader = thread(name = "system-credential-output", isDaemon = true) {
            try {
                process.inputStream.use { input -> input.copyTo(output) }
            } catch (error: Throwable) {
                outputError = error
            }
        }

        try {
            process.outputStream.use { stream ->
                if (standardInput != null) {
                    stream.write(standardInput)
                    stream.flush()
                }
            }
            val exited = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroy()
                if (!process.waitFor(250L, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(1_000L, TimeUnit.MILLISECONDS)
                }
                error("系统凭据命令执行超时")
            }
            reader.join(1_000L)
            check(!reader.isAlive) { "系统凭据命令输出读取超时" }
            outputError?.let { throw IllegalStateException("系统凭据命令输出读取失败", it) }
            return ProcessExecutionResult(process.exitValue(), output.toByteArray())
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun ByteArray.removeSingleLineEnding(): ByteArray {
        var end = size
        if (end > 0 && this[end - 1] == '\n'.code.toByte()) end--
        if (end > 0 && this[end - 1] == '\r'.code.toByte()) end--
        return copyOf(end)
    }

    private data class ProcessExecutionResult(
        val exitCode: Int,
        val output: ByteArray
    )
}
