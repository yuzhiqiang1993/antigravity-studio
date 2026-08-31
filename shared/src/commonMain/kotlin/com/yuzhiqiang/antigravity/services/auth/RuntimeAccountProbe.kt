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
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
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
 * 运行态账号探测引擎，封装与 language_server 通信的底层进程发现、端口解析与 HTTP/SSL 请求。
 */
internal object RuntimeAccountProbe {

    internal fun interface ProcessMatcher {
        fun matches(command: String, osName: String): Boolean
    }

    internal data class TargetConfig(
        val displayName: String,
        val processMatcher: ProcessMatcher
    )

    private data class LanguageServerCandidate(
        val pid: Long,
        val csrfToken: String,
        val port: Int
    )

    internal data class LanguageServerEndpoint(
        val port: Int,
        val csrfToken: String
    )

    private data class ProcessOutput(
        val text: String,
        val exitCode: Int
    )

    private data class ParsedProcessLine(
        val pid: Long,
        val command: String
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val emailPattern = Pattern.compile(
        "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@" +
                "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
                "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])+)+$"
    )

    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val USER_STATUS_PATH =
        "/exa.language_server_pb.LanguageServerService/GetUserStatus"
    private const val PROCESS_TIMEOUT_MS = 6_000L
    private const val HTTP_CONNECT_TIMEOUT_MS = 1_000
    private const val HTTP_READ_TIMEOUT_MS = 2_000
    private const val REQUEST_BODY =
        "{\"metadata\":{\"ideName\":\"antigravity\",\"extensionName\":\"antigravity\",\"locale\":\"en\"}}"
    private const val MAX_PORT = 65_535
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

    // 用量分页单页可能接近官方 LS 的 8 MB 限制，不能复用账号响应的 4 MB 限制。
    private const val MAX_USAGE_RESPONSE_BYTES = 16 * 1024 * 1024
    private const val PROCESS_READER_JOIN_TIMEOUT_MS = 2_000L

    /**
     * 探测指定目标配置的运行态账号。
     */
    suspend fun detectProfile(target: TargetConfig): Result<HostAccountDetector.IdeAccountProfile?> =
        withContext(Dispatchers.IO) {
            val discovery = discoverCandidates(target.processMatcher)
            if (discovery.isFailure) {
                val error = discovery.exceptionOrNull()
                    ?: IllegalStateException("探测 ${target.displayName} 进程失败")
                return@withContext Result.failure(error)
            }

            val candidates = discovery.getOrNull().orEmpty()
            if (candidates.isEmpty()) {
                return@withContext Result.success(null)
            }

            var lastFailure: Throwable? = null
            var hasInvalidResponse = false
            for (candidate in candidates) {
                try {
                    val profile = requestUserStatus(target.displayName, candidate)
                    if (profile != null) {
                        return@withContext Result.success(profile)
                    }
                    hasInvalidResponse = true
                } catch (exception: Throwable) {
                    lastFailure = exception
                }
            }

            when {
                lastFailure != null -> Result.failure(lastFailure)
                hasInvalidResponse -> Result.failure(IOException("本地 ${target.displayName} 账号响应未包含有效邮箱"))
                else -> Result.success(null)
            }
        }

    // ========== 进程发现 ==========

    /** 用量回退使用全部可验证的本机 Language Server，不依赖某一宿主的账号探针。 */
    internal fun discoverLanguageServerEndpoints(): Result<List<LanguageServerEndpoint>> =
        discoverCandidates { _, _ -> true }.map { candidates ->
            candidates
                .map { LanguageServerEndpoint(it.port, it.csrfToken) }
                .distinctBy { "${it.port}:${it.csrfToken}" }
        }

    private fun discoverCandidates(matcher: ProcessMatcher): Result<List<LanguageServerCandidate>> {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return when {
            osName.contains("windows") -> discoverWindowsCandidates(matcher)
            osName.contains("mac") || osName.contains("linux") -> discoverUnixCandidates(osName, matcher)
            else -> Result.success(emptyList())
        }
    }

    private fun discoverUnixCandidates(osName: String, matcher: ProcessMatcher): Result<List<LanguageServerCandidate>> {
        val psCommands = listOf(
            listOf("/bin/ps", "-axo", "pid=,command="),
            listOf("ps", "-axo", "pid=,command=")
        )
        var output: ProcessOutput? = null
        var lastError: Throwable? = null
        for (cmd in psCommands) {
            try {
                val res = executeProcess(cmd, PROCESS_TIMEOUT_MS)
                if (res.exitCode == 0) {
                    output = res
                    break
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (output == null || output.exitCode != 0) {
            return Result.failure(lastError ?: IOException("系统进程查询失败"))
        }

        val candidates = mutableListOf<LanguageServerCandidate>()
        for (line in output.text.lineSequence()) {
            val parsed = parseProcessLine(line) ?: continue
            if (!matcher.matches(parsed.command, osName)) continue

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

    private fun discoverWindowsCandidates(matcher: ProcessMatcher): Result<List<LanguageServerCandidate>> {
        val script = "\$ProgressPreference = 'SilentlyContinue'; " +
                "Get-CimInstance Win32_Process | " +
                "Where-Object { \$_.Name -like '*language_server*' } | " +
                "ForEach-Object { \"\$(\$_.ProcessId) \$(\$_.CommandLine)\" }"
        val encodedScript = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

        val output = try {
            executeProcess(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    encodedScript
                ),
                PROCESS_TIMEOUT_MS
            )
        } catch (exception: Exception) {
            return Result.failure(exception)
        }
        if (output.exitCode != 0) {
            return Result.failure(IOException("Windows 进程查询失败"))
        }

        val candidates = mutableListOf<LanguageServerCandidate>()
        for (line in output.text.lineSequence()) {
            val parsed = parseProcessLine(line) ?: continue
            if (!matcher.matches(parsed.command, "windows")) continue

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

    // ========== 进程解析 ==========

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

    internal fun extractFlagValue(command: String, flag: String): String? {
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
        val port = Regex(":(\\d+)(?:->|$)").find(line)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        return port.takeIf { it in 1..MAX_PORT }
    }

    private fun getWindowsListeningPorts(pid: Long): List<Int> {
        val netstat = try {
            executeProcess(listOf("netstat", "-ano", "-p", "tcp"), 3_000L)
        } catch (_: Exception) {
            null
        }
        if (netstat != null && netstat.exitCode == 0) {
            val ports = netstat.text.lineSequence()
                .mapNotNull { line -> parseNetstatPort(line, pid) }
                .distinct()
                .toList()
            if (ports.isNotEmpty()) return ports
        }

        val script = "\$ProgressPreference = 'SilentlyContinue'; " +
                "Get-NetTCPConnection -OwningProcess $pid -State Listen | " +
                "Select-Object -ExpandProperty LocalPort"
        val encodedScript = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val powershell = try {
            executeProcess(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    encodedScript
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

        return emptyList()
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

    // ========== HTTP 请求 ==========

    /**
     * 向本机 Language Server 发送 ConnectRPC 风格 JSON 请求。
     * 官方版本可能在同一端口上使用 HTTP 或自签 HTTPS，因此按 HTTPS → HTTP 回退。
     */
    internal fun requestLanguageServerJson(
        endpoint: LanguageServerEndpoint,
        method: String,
        body: String,
        timeoutMs: Int = 6_000
    ): String {
        require(endpoint.port in 1..MAX_PORT) { "非法 Language Server 端口" }
        require(endpoint.csrfToken.isNotBlank()) { "缺少 Language Server CSRF token" }
        val path = "/exa.language_server_pb.LanguageServerService/$method"
        var lastError: Throwable? = null
        for (protocol in listOf("https", "http")) {
            try {
                return requestLanguageServerJsonWithProtocol(endpoint, protocol, path, body, timeoutMs)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IOException("Language Server 请求失败: ${lastError?.message ?: "未知错误"}", lastError)
    }

    private fun requestLanguageServerJsonWithProtocol(
        endpoint: LanguageServerEndpoint,
        protocol: String,
        path: String,
        body: String,
        timeoutMs: Int
    ): String {
        val connection = URL("$protocol://$LOOPBACK_HOST:${endpoint.port}$path")
            .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = loopbackTrustAllSslSocketFactory
            connection.hostnameVerifier = HostnameVerifier { hostname, _ -> hostname == LOOPBACK_HOST }
        }
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Connect-Protocol-Version", "1")
        connection.setRequestProperty("X-Codeium-Csrf-Token", endpoint.csrfToken)

        return try {
            connection.outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = input?.use { it.readNBytes(MAX_RESPONSE_BYTES + 1) } ?: ByteArray(0)
            if (bytes.size > MAX_USAGE_RESPONSE_BYTES) throw IOException("Language Server 响应超过大小限制")
            val responseBody = bytes.toString(Charsets.UTF_8)
            if (status !in 200..299) {
                throw IOException("$path HTTP $status: ${responseBody.take(200)}")
            }
            responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun requestUserStatus(
        targetDisplayName: String,
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
                throw IOException("本地 $targetDisplayName 语言服务返回 HTTP $responseCode")
            }
            val body = connection.inputStream.use { inputStream ->
                val bytes = inputStream.readNBytes(MAX_RESPONSE_BYTES + 1)
                if (bytes.size > MAX_RESPONSE_BYTES) {
                    throw IOException("本地 $targetDisplayName 账号响应超过大小限制")
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
        } catch (_: Throwable) {
            null
        } ?: return null

        val userStatus = (root["userStatus"] ?: root["user_status"]) as? JsonObject ?: return null
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
        } catch (exception: Throwable) {
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

            override fun checkClientTrusted(
                chain: Array<X509Certificate>?,
                authType: String?
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<X509Certificate>?,
                authType: String?
            ) = Unit
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }.socketFactory
    }

    private val loopbackTrustAllSslSocketFactory: SSLSocketFactory by lazy {
        createLoopbackTrustAllSslSocketFactory()
    }
}
