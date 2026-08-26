package com.yuzhiqiang.antigravity.services.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Proxy
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 通过 Antigravity IDE 正在运行的 language_server 读取当前运行态账号。
 *
 * 该探针只接受能够从进程命令行证明属于 IDE 的 language_server（`--app_data_dir antigravity-ide`
 * 或安装路径包含 `Antigravity IDE`），不会把独立 App、CLI 或 Studio 的语言服务误当成 IDE。
 * 所有网络请求固定发往 127.0.0.1，只在显式探测期间读取进程参数，不读取钥匙串。
 *
 * IDE 可能同时运行多个 language_server 实例（每个工作区一个），所有实例共享同一个
 * 登录账号，因此只要有任意一个实例返回有效邮箱即视为探测成功。
 */
object RuntimeIdeAccountProbe {

    private data class LanguageServerCandidate(
        val pid: Long,
        val csrfToken: String,
        val port: Int
    )

    private data class ProcessOutput(
        val text: String,
        val exitCode: Int
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val emailPattern = Pattern.compile(
        "^[a-zA-Z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@" +
                "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
                "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+${'$'}"
    )

    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val USER_STATUS_PATH =
        "/exa.language_server_pb.LanguageServerService/GetUserStatus"
    private const val PROCESS_TIMEOUT_MS = 2_000L
    private const val HTTP_CONNECT_TIMEOUT_MS = 1_000
    private const val HTTP_READ_TIMEOUT_MS = 2_000
    private const val REQUEST_BODY =
        "{\"metadata\":{\"ideName\":\"antigravity\",\"extensionName\":\"antigravity\",\"locale\":\"en\"}}"

    /**
     * 探测当前运行中的 Antigravity IDE 账号。
     *
     * 返回语义：没有 IDE 进程或没有可连接候选时返回成功的 null；发现 IDE
     * language_server 但响应中的邮箱无效、或所有候选请求均失败时返回 failure，
     * 让上层避免把静态 SQLite 凭据冒充成 IDE 运行态。
     */
    suspend fun detectProfile(): Result<HostAccountDetector.IdeAccountProfile?> =
        withContext(Dispatchers.IO) {
            val discovery = discoverCandidates()
            if (discovery.isFailure) {
                val error = discovery.exceptionOrNull()
                    ?: IllegalStateException("探测 Antigravity IDE 进程失败")
                return@withContext Result.failure(error)
            }

            val candidates = discovery.getOrNull().orEmpty()
            if (candidates.isEmpty()) {
                return@withContext Result.success(null)
            }

            var lastFailure: Exception? = null
            var hasInvalidResponse = false
            for (candidate in candidates) {
                try {
                    val profile = requestUserStatus(candidate)
                    if (profile != null) {
                        return@withContext Result.success(profile)
                    }
                    hasInvalidResponse = true
                } catch (exception: Exception) {
                    lastFailure = exception
                }
            }

            when {
                lastFailure != null -> Result.failure(lastFailure)
                hasInvalidResponse -> Result.failure(IOException("本地 IDE 账号响应未包含有效邮箱"))
                else -> Result.success(null)
            }
        }

    // ========== 进程发现 ==========

    private fun discoverCandidates(): Result<List<LanguageServerCandidate>> {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return when {
            osName.contains("windows") -> discoverWindowsCandidates()
            osName.contains("mac") || osName.contains("linux") -> discoverUnixCandidates(osName)
            else -> Result.success(emptyList())
        }
    }

    private fun discoverUnixCandidates(osName: String): Result<List<LanguageServerCandidate>> {
        val output = try {
            executeProcess(listOf("/bin/ps", "-axo", "pid=,command="), PROCESS_TIMEOUT_MS)
        } catch (exception: Exception) {
            return Result.failure(exception)
        }
        if (output.exitCode != 0) {
            return Result.failure(IOException("系统进程查询失败"))
        }

        val candidates = mutableListOf<LanguageServerCandidate>()
        for (line in output.text.lineSequence()) {
            val parsed = parseProcessLine(line) ?: continue
            if (!isAntigravityIdeCommand(parsed.command, osName)) continue

            val csrfToken = extractFlagValue(parsed.command, "--csrf_token")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            val commandPort = extractFlagValue(parsed.command, "--https_server_port")
                ?.toIntOrNull()
                ?: 0
            val ports = if (commandPort in 1..MAX_PORT) {
                listOf(commandPort)
            } else {
                getUnixListeningPorts(parsed.pid)
            }
            addCandidates(candidates, parsed.pid, csrfToken, ports)
        }
        return Result.success(candidates)
    }

    private fun discoverWindowsCandidates(): Result<List<LanguageServerCandidate>> {
        val scripts = listOf(
            "Get-CimInstance Win32_Process -Filter \"Name LIKE '%language_server%'\" | " +
                    "ForEach-Object { \$_.ProcessId.ToString() + ' ' + \$_.CommandLine }",
            "Get-WmiObject Win32_Process -Filter \"Name LIKE '%language_server%'\" | " +
                    "ForEach-Object { \$_.ProcessId.ToString() + ' ' + \$_.CommandLine }"
        )
        var output: ProcessOutput? = null
        var lastFailure: Exception? = null
        for (script in scripts) {
            try {
                val result = executeProcess(
                    listOf(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-Command",
                        script
                    ),
                    PROCESS_TIMEOUT_MS
                )
                if (result.exitCode == 0) {
                    output = result
                    break
                }
                lastFailure = IOException("Windows 进程查询失败")
            } catch (exception: Exception) {
                lastFailure = exception
            }
        }
        if (output == null) {
            return Result.failure(lastFailure ?: IOException("Windows 进程查询失败"))
        }

        val candidates = mutableListOf<LanguageServerCandidate>()
        for (line in output.text.lineSequence()) {
            val parsed = parseProcessLine(line) ?: continue
            if (!isAntigravityIdeCommand(parsed.command, "windows")) continue

            val csrfToken = extractFlagValue(parsed.command, "--csrf_token")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            val commandPort = extractFlagValue(parsed.command, "--https_server_port")
                ?.toIntOrNull()
                ?: 0
            val ports = if (commandPort in 1..MAX_PORT) {
                listOf(commandPort)
            } else {
                getWindowsListeningPorts(parsed.pid)
            }
            addCandidates(candidates, parsed.pid, csrfToken, ports)
        }
        return Result.success(candidates)
    }

    // ========== 进程匹配 ==========

    private data class ParsedProcessLine(
        val pid: Long,
        val command: String
    )

    private fun parseProcessLine(line: String): ParsedProcessLine? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val separator = trimmed.indexOfFirst { it.isWhitespace() }
        if (separator <= 0) return null
        val pid = trimmed.substring(0, separator).toLongOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val command = trimmed.substring(separator).trim()
        if (!command.contains("language_server", ignoreCase = true)) return null
        return ParsedProcessLine(pid, command)
    }

    /** 只按可证明的 IDE 安装路径或 --app_data_dir antigravity-ide 识别。 */
    private fun isAntigravityIdeCommand(command: String, osName: String): Boolean {
        val normalized = command.lowercase(Locale.ROOT).replace('\\', '/')
        // 排除独立 App 和 Studio 的进程
        if (appCommandMarkers.any(normalized::contains)) return false

        val appDataDir = extractFlagValue(normalized, "--app_data_dir")
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }

        // 通过 --app_data_dir 精准识别
        if (appDataDir == "antigravity-ide") return true

        // 通过安装路径识别
        return when {
            osName.contains("mac") -> {
                normalized.contains("/antigravity ide.app/") ||
                        normalized.contains("/antigravity-ide.app/")
            }
            osName.contains("windows") -> {
                normalized.contains("/antigravity ide/") ||
                        normalized.contains("/antigravity-ide/")
            }
            osName.contains("linux") -> {
                normalized.contains("/antigravity-ide/") ||
                        normalized.contains("/antigravity ide/")
            }
            else -> false
        }
    }

    // ========== 端口发现 ==========

    private fun addCandidates(
        candidates: MutableList<LanguageServerCandidate>,
        pid: Long,
        csrfToken: String,
        ports: List<Int>
    ) {
        for (port in ports) {
            if (port !in 1..MAX_PORT) continue
            if (candidates.none { it.pid == pid && it.port == port }) {
                candidates += LanguageServerCandidate(pid, csrfToken, port)
            }
        }
    }

    private fun getUnixListeningPorts(pid: Long): List<Int> {
        val commands = listOf(
            listOf("/usr/sbin/lsof", "-nP", "-a", "-p", pid.toString(), "-iTCP", "-sTCP:LISTEN", "-Fn"),
            listOf("lsof", "-nP", "-a", "-p", pid.toString(), "-iTCP", "-sTCP:LISTEN", "-Fn")
        )
        for (command in commands) {
            val output = try {
                executeProcess(command, PROCESS_TIMEOUT_MS)
            } catch (_: Exception) {
                continue
            }
            if (output.exitCode != 0) continue
            val ports = output.text.lineSequence()
                .mapNotNull(::parseLsofPort)
                .distinct()
                .toList()
            if (ports.isNotEmpty()) return ports
        }
        return emptyList()
    }

    private fun parseLsofPort(line: String): Int? {
        if (!line.startsWith("n")) return null
        val port = Regex(":(\\d+)(?:->|${'$'})").find(line)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        return port.takeIf { it in 1..MAX_PORT }
    }

    private fun getWindowsListeningPorts(pid: Long): List<Int> {
        val powershell = try {
            executeProcess(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    "Get-NetTCPConnection -OwningProcess $pid -State Listen | " +
                            "Select-Object -ExpandProperty LocalPort"
                ),
                PROCESS_TIMEOUT_MS
            )
        } catch (_: Exception) {
            null
        }
        if (powershell != null && powershell.exitCode == 0) {
            val ports = parseNumericPorts(powershell.text)
            if (ports.isNotEmpty()) return ports
        }

        val netstat = try {
            executeProcess(listOf("netstat", "-ano", "-p", "tcp"), PROCESS_TIMEOUT_MS)
        } catch (_: Exception) {
            return emptyList()
        }
        if (netstat.exitCode != 0) return emptyList()
        return netstat.text.lineSequence()
            .mapNotNull { line -> parseNetstatPort(line, pid) }
            .distinct()
            .toList()
    }

    private fun parseNumericPorts(text: String): List<Int> {
        return text.lineSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..MAX_PORT }
            .distinct()
            .toList()
    }

    private fun parseNetstatPort(line: String, pid: Long): Int? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5 || !parts[0].equals("TCP", ignoreCase = true)) return null
        if (!parts[3].equals("LISTENING", ignoreCase = true)) return null
        if (parts.last().toLongOrNull() != pid) return null
        return parts[1].substringAfterLast(':').toIntOrNull()
            ?.takeIf { it in 1..MAX_PORT }
    }

    private fun extractFlagValue(command: String, flag: String): String? {
        val parts = command.split(Regex("\\s+"))
        for (index in parts.indices) {
            val part = parts[index]
            if (part == flag && index + 1 < parts.size) {
                return parts[index + 1].trim('"', '\'')
            }
            if (part.startsWith("$flag=")) {
                return part.removePrefix("$flag=").trim('"', '\'')
            }
        }
        return null
    }

    // ========== HTTP 请求 ==========

    private fun requestUserStatus(
        candidate: LanguageServerCandidate
    ): HostAccountDetector.IdeAccountProfile? {
        val url = URL("https://$LOOPBACK_HOST:${candidate.port}$USER_STATUS_PATH")
        require(url.host == LOOPBACK_HOST) { "仅允许访问本机回环地址" }

        val connection = (url.openConnection(Proxy.NO_PROXY) as HttpsURLConnection).apply {
            sslSocketFactory = loopbackTrustAllSslSocketFactory
            hostnameVerifier = HostnameVerifier { hostname, _ -> hostname == LOOPBACK_HOST }
            requestMethod = "POST"
            connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            readTimeout = HTTP_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connect-Protocol-Version", "1")
            setRequestProperty("X-Codeium-Csrf-Token", candidate.csrfToken)
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(REQUEST_BODY.toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("本地 IDE 语言服务返回 HTTP $responseCode")
            }
            val body = connection.inputStream.use { inputStream ->
                val bytes = inputStream.readNBytes(MAX_RESPONSE_BYTES + 1)
                if (bytes.size > MAX_RESPONSE_BYTES) {
                    throw IOException("本地 IDE 账号响应超过大小限制")
                }
                bytes.toString(Charsets.UTF_8)
            }
            parseUserStatus(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUserStatus(body: String): HostAccountDetector.IdeAccountProfile? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null

        val userStatus = root["userStatus"] as? JsonObject ?: return null
        val email = userStatus["email"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { emailPattern.matcher(it).matches() }
            ?: return null
        val name = userStatus["name"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return HostAccountDetector.IdeAccountProfile(
            email = email.lowercase(Locale.ROOT),
            name = name
        )
    }

    // ========== 进程执行 ==========

    private fun executeProcess(command: List<String>, timeoutMs: Long): ProcessOutput {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val outputReader = Thread {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                output.append(reader.readText())
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outputReader.join(PROCESS_READER_JOIN_TIMEOUT_MS)
                throw IOException("系统进程查询超时")
            }
            outputReader.join(PROCESS_READER_JOIN_TIMEOUT_MS)
            if (outputReader.isAlive) {
                throw IOException("读取系统进程输出超时")
            }
            return ProcessOutput(output.toString(), process.exitValue())
        } catch (exception: Exception) {
            process.destroyForcibly()
            outputReader.interrupt()
            outputReader.join(PROCESS_READER_JOIN_TIMEOUT_MS)
            throw exception
        }
    }

    // ========== SSL ==========

    private fun createLoopbackTrustAllSslSocketFactory(): SSLSocketFactory {
        val trustManagers = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }.socketFactory
    }

    private val loopbackTrustAllSslSocketFactory: SSLSocketFactory by lazy {
        createLoopbackTrustAllSslSocketFactory()
    }

    // ========== 常量 ==========

    /** 排除独立 App 的进程 */
    private val appCommandMarkers = listOf(
        "/antigravity.app/",
        "--standalone"
    )

    private const val MAX_PORT = 65_535
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val PROCESS_READER_JOIN_TIMEOUT_MS = 500L
}
