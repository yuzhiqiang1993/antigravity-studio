package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.RandomAccessFile
import java.sql.DriverManager
import java.util.Base64
import java.util.regex.Pattern

/**
 * 多端宿主活跃账号探测引擎。
 * 探测 Antigravity 宿主的账号信息。
 * IDE 使用 state.vscdb 中的官方 userStatus；App 与 CLI 共用外部 OAuth 凭据文件，
 * App 在 macOS 仅使用运行中 language_server 的 RPC 做运行态确认。
 * 共享文件凭据和 App 运行态是同一认证链路的不同观察面，不能互相当成两套账号系统。
 *
 * 注：完全基于 Antigravity 官方物理底层数据源，不依赖任何第三方插件或扩展。
 */
object HostAccountDetector {

    data class IdeAccountProfile(
        val email: String,
        val name: String? = null,
        val avatarUrl: String? = null,
        val tierText: String? = null
    )

    data class CliAppAccountProfile(
        val email: String,
        val name: String? = null,
        val expiryTimestamp: Long? = null,
        val tokenType: String? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val emailPattern = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")

    /**
     * 探测 App 与 CLI 共用的外部账号 Profile。
     *
     * 外部 OAuth 凭据只有一个共享来源；运行日志仅在共享文件不存在时作为兼容发现信息，
     * 不覆盖共享文件中的账号，也不把 App 的 jetski/运行态状态当成另一套账号凭据。
     * 此方法不代表 macOS App 的运行态；macOS App 的运行态确认必须走 [detectAppActiveProfile]。
     */
    fun detectSharedExternalProfile(customCredentialsFile: File? = null): CliAppAccountProfile? {
        val credentialsFile = customCredentialsFile ?: resolveSharedExternalCredentialsFile()
        val staticProfile = detectStaticCliAppProfile(credentialsFile)?.first
        if (staticProfile != null || customCredentialsFile != null) {
            return staticProfile
        }

        // 若官方标准路径不存在，尝试从 App 专属凭据文件补充读取
        val appCredFile = resolvePlatformAppCredentialsFile()
        if (appCredFile.isFile) {
            val appProfile = readCliAppProfile(appCredFile)
            if (appProfile != null) {
                return appProfile
            }
        }

        val runtimeEmail = detectEmailWithTimestampFromRecentLogs().first
        return runtimeEmail?.let { email ->
            CliAppAccountProfile(
                email = email.lowercase(),
                tokenType = "Bearer"
            )
        }
    }

    fun resolveSharedExternalCredentialsFile(): File {
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        if (!customDataDir.isNullOrBlank()) {
            return File(customDataDir, "oauth_credentials.json")
        }
        return File(System.getProperty("user.home"), ".gemini/oauth_creds.json")
    }

    /**
     * 解析当前操作系统下 Antigravity App 的 OAuth 凭据文件路径。
     */
    fun resolvePlatformAppCredentialsFile(): File {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/Antigravity/oauth_credentials.json")
            }
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                File(appData, "Antigravity/oauth_credentials.json")
            }
            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?: "$userHome/.config"
                File(configHome, "antigravity/oauth_credentials.json")
            }
        }
    }

    /**
     * 严格探测 App 与 CLI 共用的共享凭据文件中的账号邮箱。
     */
    fun detectSharedExternalActiveEmail(customCredentialsFile: File? = null): String? {
        return detectSharedExternalProfile(customCredentialsFile)?.email
    }

    private fun detectStaticCliAppProfile(customCredentialsFile: File?): Pair<CliAppAccountProfile, Long>? {
        val candidates = resolveCliCredentialFiles(customCredentialsFile)
        for (file in candidates) {
            if (file.isFile) {
                val profile = readCliAppProfile(file)
                if (profile != null && profile.email.isNotBlank()) {
                    return profile to file.lastModified()
                }
            }
        }
        return null
    }

    private fun resolveCliCredentialFiles(customCredentialsFile: File?): List<File> {
        if (customCredentialsFile != null) {
            return listOf(customCredentialsFile)
        }

        val userHome = System.getProperty("user.home")
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        if (!customDataDir.isNullOrBlank()) {
            return listOf(
                File(customDataDir, "oauth_credentials.json"),
                File(customDataDir, "oauth_creds.json")
            )
        }
        return listOf(
            File(userHome, ".gemini/oauth_creds.json"),
            resolvePlatformAppCredentialsFile(),
            File(userHome, ".gemini/oauth_credentials.json")
        ).distinct()
    }

    private fun readCliAppProfile(file: File): CliAppAccountProfile? {
        return try {
            val root = json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject ?: return null
            val tokenObj = root["token"] as? JsonObject
            val directEmail = root["email"]?.jsonPrimitive?.contentOrNull
                ?: root["user_email"]?.jsonPrimitive?.contentOrNull
                ?: root["antigravity_cockpit_active_email"]?.jsonPrimitive?.contentOrNull
            val name = root["name"]?.jsonPrimitive?.contentOrNull
            val expiryTimestamp = resolveExpiryTimestamp(root)
            val tokenType = root["token_type"]?.jsonPrimitive?.contentOrNull
            val email = directEmail?.takeIf { value -> value.contains("@") }
                ?: parseEmailFromRefreshToken(root)
                ?: tokenObj?.get("id_token")?.jsonPrimitive?.contentOrNull?.let(::parseEmailFromJwt)
                ?: tokenObj?.get("access_token")?.jsonPrimitive?.contentOrNull?.let(::parseEmailFromJwt)
                ?: return null

            CliAppAccountProfile(
                email = email.trim().lowercase(),
                name = name?.trim(),
                expiryTimestamp = expiryTimestamp,
                tokenType = tokenType
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEmailFromJwt(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = parts[1]
                val padded = when (payload.length % 4) {
                    2 -> "$payload=="
                    3 -> "$payload="
                    else -> payload
                }.replace('-', '+').replace('_', '/')
                val decoded = Base64.getDecoder().decode(padded).toString(Charsets.UTF_8)
                val obj = json.parseToJsonElement(decoded) as? JsonObject
                obj?.get("email")?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("@") }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEmailFromRefreshToken(root: JsonObject): String? {
        val tokenObj = root["token"] as? JsonObject
        val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: root["refreshToken"]?.jsonPrimitive?.contentOrNull
            ?: tokenObj?.get("refresh_token")?.jsonPrimitive?.contentOrNull
            ?: tokenObj?.get("refreshToken")?.jsonPrimitive?.contentOrNull
            ?: return null
        return RefreshTokenParser.parse(refreshToken).firstOrNull()?.email
    }

    private fun resolveExpiryTimestamp(root: JsonObject): Long? {
        val rawTimestamp = root["expiry_timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: root["expiry_date"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return null
        return if (rawTimestamp >= EPOCH_MILLIS_THRESHOLD) {
            rawTimestamp / MILLIS_PER_SECOND
        } else {
            rawTimestamp
        }
    }

    /**
     * 从官方日志文件中实时提取最新登录生效的邮箱及其物理时间戳（涵盖 App 与 CLI 运行产物）
     */
    private fun detectEmailWithTimestampFromRecentLogs(): Pair<String?, Long> {
        try {
            val userHome = System.getProperty("user.home")
            val candidateLogs = listOf(
                File(userHome, ".gemini/antigravity-cli/cli.log"),
                File(userHome, ".gemini/antigravity/app.log"),
                File(userHome, ".gemini/antigravity/server.log")
            )

            var bestEmail: String? = null
            var latestMtime: Long = 0L

            for (logFile in candidateLogs) {
                if (!logFile.exists() || !logFile.isFile) continue
                val lines = readLastLogLines(logFile, 150)
                for (line in lines.reversed()) {
                    if (line.contains("authenticated successfully as", ignoreCase = true) ||
                        line.contains("applyAuthResult: email=", ignoreCase = true) ||
                        line.contains("OAuth: authenticated successfully as", ignoreCase = true)
                    ) {
                        val matcher = emailPattern.matcher(line)
                        if (matcher.find()) {
                            bestEmail = matcher.group(0).trim().lowercase()
                            latestMtime = logFile.lastModified()
                            return Pair(bestEmail, latestMtime)
                        }
                    }
                }
            }

            // 亦扫描 ~/.gemini/antigravity-cli/log/ 目录下的最新日志
            val logDir = File(userHome, ".gemini/antigravity-cli/log")
            if (logDir.exists() && logDir.isDirectory) {
                val latestLog = logDir.listFiles { f -> f.name.endsWith(".log") }
                    ?.maxByOrNull { it.lastModified() }
                if (latestLog != null) {
                    val lines = readLastLogLines(latestLog, 100)
                    for (line in lines.reversed()) {
                        if (line.contains("authenticated successfully as", ignoreCase = true) ||
                            line.contains("applyAuthResult: email=", ignoreCase = true)
                        ) {
                            val matcher = emailPattern.matcher(line)
                            if (matcher.find()) {
                                return Pair(matcher.group(0).trim().lowercase(), latestLog.lastModified())
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Pair(null, 0L)
    }

    private fun readLastLogLines(file: File, maxLines: Int): List<String> {
        return RandomAccessFile(file, "r").use { randomAccessFile ->
            val length = randomAccessFile.length()
            val start = (length - MAX_LOG_TAIL_BYTES).coerceAtLeast(0L)
            randomAccessFile.seek(start)
            val bytes = ByteArray((length - start).toInt())
            randomAccessFile.readFully(bytes)
            bytes.toString(Charsets.UTF_8)
                .lineSequence()
                .toList()
                .takeLast(maxLines)
        }
    }

    /**
     * 探测 Antigravity IDE 当前生效登录的账号邮箱
     */
    suspend fun detectIdeActiveEmail(): String? {
        return detectIdeActiveProfile()?.email
    }

    /**
     * 探测 Antigravity App & CLI 共享认证通道生效的 Profile。
     *
     * 优先级：运行态 API → 凭据文件回退（仅 App 在运行但 API 失败时）。
     * App 未运行时返回 null，UI 不显示账号 tag。
     */
    suspend fun detectAppCliActiveProfile(): IdeAccountProfile? = withContext(Dispatchers.IO) {
        val appRunning = AppHostManager.isRunning()

        if (appRunning) {
            // 运行态：优先使用 RuntimeAppAccountProbe
            val runtimeProfile = RuntimeAppAccountProbe.detectProfile().getOrNull()
            if (runtimeProfile != null) {
                return@withContext runtimeProfile
            }
            // 运行态 API 失败 → 回退到凭据文件
            val sharedProfile = detectSharedExternalProfile()
            if (sharedProfile != null) {
                return@withContext IdeAccountProfile(
                    email = sharedProfile.email,
                    name = sharedProfile.name
                )
            }
        }

        // App 未运行：不显示 tag
        null
    }

    /**
     * 探测 Antigravity App & CLI 共享认证通道生效的邮箱。
     */
    suspend fun detectAppCliActiveEmail(): String? {
        return detectAppCliActiveProfile()?.email
    }

    /**
     * 探测 Antigravity App 当前生效登录的账号 Profile（对齐 App & CLI 共享认证实体）。
     */
    suspend fun detectAppActiveProfile(): IdeAccountProfile? = detectAppCliActiveProfile()

    /**
     * 探测 Antigravity App 当前生效登录的账号邮箱（对齐 App & CLI 共享认证实体）。
     */
    suspend fun detectAppActiveEmail(): String? = detectAppCliActiveEmail()

    /**
     * 探测 Antigravity IDE 完整的活跃用户 Profile（包含邮箱、姓名、头像、订阅文本）。
     *
     * 优先级：运行态 API → SQLite 回退（仅 IDE 在运行但 API 失败时）。
     * IDE 未运行时返回 null，UI 不显示账号 tag。
     */
    suspend fun detectIdeActiveProfile(): IdeAccountProfile? = withContext(Dispatchers.IO) {
        val ideRunning = IdeHostManager.isRunning()

        if (ideRunning) {
            // 运行态：优先使用 RuntimeIdeAccountProbe
            val runtimeProfile = RuntimeIdeAccountProbe.detectProfile().getOrNull()
            if (runtimeProfile != null) {
                return@withContext runtimeProfile
            }
            // 运行态 API 失败 → 回退到 SQLite
            return@withContext detectProfileFromCanonicalStateDb(StateDbInjector.TargetHost.IDE)
        }

        // IDE 未运行：不显示 tag
        null
    }

    /**
     * 仅从与注入器一致的 canonical globalStorage/state.vscdb 提取 Profile。
     */
    private fun detectProfileFromCanonicalStateDb(targetHost: StateDbInjector.TargetHost): IdeAccountProfile? {
        val candidateDbFiles = StateDbInjector.resolveCandidateDbFiles(targetHost)

        for (dbFile in candidateDbFiles) {
            if (!dbFile.exists() || !dbFile.isFile) continue
            val profile = readProfileFromStateDb(dbFile)
            if (profile != null) {
                return profile
            }
        }
        return null
    }

    /**
     * 从单个 state.vscdb 文件中读取并递归解码 Protobuf 提取完整 Profile
     */
    private fun readProfileFromStateDb(dbFile: File): IdeAccountProfile? {
        return try {
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.prepareStatement(
                    "SELECT value FROM ItemTable WHERE key = ? LIMIT 1"
                ).use { statement ->
                    statement.setString(1, USER_STATUS_STATE_KEY)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("value")?.let { raw ->
                                parseProfileFromUserStatusRaw(raw)
                            }
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 精确解析 userStatus raw string (Level 1~4 Protobuf + Base64 嵌套物理结构解码)
     */
    fun parseProfileFromUserStatusRaw(raw: String): IdeAccountProfile? {
        try {
            val topBytes = Base64.getDecoder().decode(raw.trim())
            val l1Fields = ProtobufExtractor.readFields(topBytes)

            for (f1 in l1Fields) {
                val b1 = f1.bytesValue ?: continue
                val l2Fields = ProtobufExtractor.readFields(b1)

                // 检查 Level 2 的 Field 2 (Length-delimited)
                for (f2 in l2Fields) {
                    val b2 = f2.bytesValue ?: continue
                    val l3Fields = ProtobufExtractor.readFields(b2)

                    for (f3 in l3Fields) {
                        val b3 = f3.bytesValue ?: continue

                        // Level 3 的 Field 1 是 Base64 编码的内部 UserStatus Protobuf 字节
                        val innerBytes = try {
                            Base64.getDecoder().decode(b3)
                        } catch (_: Exception) {
                            null
                        }

                        if (innerBytes != null && innerBytes.isNotEmpty()) {
                            val l4Fields = ProtobufExtractor.readFields(innerBytes)
                            var foundEmail: String? = null
                            var foundName: String? = null
                            var foundAvatar: String? = null
                            var foundTier: String? = null

                            for (f4 in l4Fields) {
                                when (f4.fieldNumber) {
                                    7 -> { // Email
                                        val str = f4.bytesValue?.decodeToString()?.trim()?.lowercase()
                                        if (!str.isNullOrBlank() && str.contains("@")) {
                                            foundEmail = str
                                        }
                                    }

                                    3 -> { // Name
                                        foundName = f4.bytesValue?.decodeToString()?.trim()
                                    }

                                    36 -> { // Tier
                                        foundTier = f4.bytesValue?.decodeToString()?.trim()
                                    }

                                    38 -> { // Avatar URL
                                        foundAvatar = f4.bytesValue?.decodeToString()?.trim()
                                    }
                                }
                            }

                            if (!foundEmail.isNullOrBlank()) {
                                return IdeAccountProfile(
                                    email = foundEmail,
                                    name = foundName,
                                    avatarUrl = foundAvatar,
                                    tierText = foundTier
                                )
                            }
                        }

                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * 保持向后兼容的邮箱提取接口
     */
    fun parseEmailFromUserStatusRaw(raw: String): String? {
        return parseProfileFromUserStatusRaw(raw)?.email
    }

    /**
     * 根据邮箱查找系统中可用的 RefreshToken
     */
    fun findAvailableRefreshToken(email: String): String? {
        val targetEmail = email.trim().lowercase()
        val candidateFiles = resolveCliCredentialFiles(customCredentialsFile = null)

        for (file in candidateFiles) {
            if (!file.exists() || !file.isFile) continue
            try {
                val content = file.readText(Charsets.UTF_8)
                val root = json.parseToJsonElement(content) as? JsonObject ?: continue

                val fileEmail = root["email"]?.jsonPrimitive?.contentOrNull
                    ?: root["user_email"]?.jsonPrimitive?.contentOrNull
                    ?: root["antigravity_cockpit_active_email"]?.jsonPrimitive?.contentOrNull

                val tokenObj = root["token"] as? JsonObject
                val directRt = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                    ?: tokenObj?.get("refresh_token")?.jsonPrimitive?.contentOrNull

                if (!directRt.isNullOrBlank()) {
                    if (fileEmail?.equals(targetEmail, ignoreCase = true) == true) {
                        return directRt
                    }
                    val parsed = RefreshTokenParser.parse(directRt).firstOrNull()
                    if (parsed?.email?.equals(targetEmail, ignoreCase = true) == true) {
                        return directRt
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private const val USER_STATUS_STATE_KEY = "antigravityUnifiedStateSync.userStatus"
    private const val MILLIS_PER_SECOND = 1_000L
    private const val EPOCH_MILLIS_THRESHOLD = 10_000_000_000L
    private const val MAX_LOG_TAIL_BYTES = 256 * 1024L

    private fun isMacOs(): Boolean {
        return System.getProperty("os.name", "").lowercase().contains("mac")
    }
}
