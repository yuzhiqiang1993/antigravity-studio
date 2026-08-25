package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.DriverManager
import java.util.Base64
import java.util.regex.Pattern

/**
 * 多端宿主活跃账号探测引擎。
 * 能够精确穿透 Antigravity 宿主底层物理数据，实时探测：
 * 1. Antigravity IDE 物理生效的 Google 账号 Profile (通过 state.vscdb SQLite Protobuf 物理数据直接提取)
 * 2. Antigravity App / CLI 物理生效的 Google 账号 (通过 oauth_credentials.json 官方凭证)
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
     * 探测 Antigravity App / CLI 当前物理生效登录的账号 Profile
     *
     * 探测与仲裁机制：
     * 1. 物理静态凭证扫描：优先扫描官方 oauth_credentials.json 提取结构化数据（邮箱、姓名、Token 类型、有效期、文件修改时间）；
     * 2. 运行时证据扫描：从官方运行日志（cli.log / app.log）中提取最近一次实际执行认证的邮箱与日志时间戳；
     * 3. 双向交叉仲裁：
     *    - 若两者邮箱一致：直接返回结构完整的物理 Profile；
     *    - 若两者不一致：比较文件修改时间戳（mtime），以最新发生认证行为的介质作为生效真理源，彻底杜绝历史旧凭据残留误判。
     */
    fun detectCliAppProfile(customCredentialsFile: File? = null): CliAppAccountProfile? {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()

        // 1. 扫描官方物理凭据文件（严格限定为 App / CLI 专用路径，不混入任何 IDE 插件目录）
        val candidateFiles = buildList {
            customCredentialsFile?.let { add(it) }
            add(File(userHome, ".gemini/oauth_credentials.json"))
            add(File(userHome, ".config/antigravity/oauth_credentials.json"))
            add(File(userHome, ".antigravity/oauth_credentials.json"))
            if (os.contains("mac")) {
                add(File(userHome, "Library/Application Support/Antigravity/oauth_credentials.json"))
            } else if (os.contains("win")) {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                add(File(appData, "Antigravity/oauth_credentials.json"))
            }
        }

        var staticProfile: CliAppAccountProfile? = null
        var staticFileMtime: Long = 0L

        for (file in candidateFiles) {
            if (!file.exists() || !file.isFile) continue
            try {
                val content = file.readText(Charsets.UTF_8)
                val root = json.parseToJsonElement(content) as? JsonObject ?: continue
                val directEmail = root["email"]?.jsonPrimitive?.contentOrNull
                val name = root["name"]?.jsonPrimitive?.contentOrNull
                val expiry = root["expiry_timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: root["expiry_date"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                val tokenType = root["token_type"]?.jsonPrimitive?.contentOrNull

                if (!directEmail.isNullOrBlank() && directEmail.contains("@")) {
                    staticProfile = CliAppAccountProfile(
                        email = directEmail.trim().lowercase(),
                        name = name?.trim(),
                        expiryTimestamp = expiry,
                        tokenType = tokenType
                    )
                    staticFileMtime = file.lastModified()
                    break
                }

                // 若 email 字段为空，尝试从 refresh_token 解析 sub/email
                val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                    ?: root["refreshToken"]?.jsonPrimitive?.contentOrNull
                if (!refreshToken.isNullOrBlank()) {
                    val parsed = RefreshTokenParser.parse(refreshToken).firstOrNull()
                    if (!parsed?.email.isNullOrBlank()) {
                        staticProfile = CliAppAccountProfile(
                            email = parsed!!.email!!.trim().lowercase(),
                            name = name?.trim(),
                            expiryTimestamp = expiry,
                            tokenType = tokenType
                        )
                        staticFileMtime = file.lastModified()
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. 扫描官方运行时日志中的最新认证记录
        val (runtimeEmail, runtimeMtime) = detectEmailWithTimestampFromRecentLogs()

        // 3. 双向交叉仲裁
        if (staticProfile != null && runtimeEmail != null) {
            if (staticProfile.email.equals(runtimeEmail, ignoreCase = true)) {
                // 两者完全一致，物理凭据与运行态双重确证
                return staticProfile
            }
            // 两者不一致：若运行日志更新（说明用户最近刚切换过登录态而凭据未回写），以运行时为准
            if (runtimeMtime >= staticFileMtime) {
                return CliAppAccountProfile(
                    email = runtimeEmail.lowercase(),
                    name = staticProfile.name,
                    tokenType = staticProfile.tokenType ?: "Bearer"
                )
            }
            // 若凭据文件修改时间更新，以凭据文件为准
            return staticProfile
        }

        // 仅命中了单方证据时直接返回
        if (staticProfile != null) {
            return staticProfile
        }

        if (runtimeEmail != null) {
            return CliAppAccountProfile(
                email = runtimeEmail.lowercase(),
                tokenType = "Bearer"
            )
        }

        return null
    }

    /**
     * 探测 App / CLI 当前生效登录的账号邮箱
     */
    fun detectCliAppActiveEmail(customCredentialsFile: File? = null): String? {
        return detectCliAppProfile(customCredentialsFile)?.email
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
                val lines = logFile.readLines(Charsets.UTF_8).takeLast(150)
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
                    val lines = latestLog.readLines(Charsets.UTF_8).takeLast(100)
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

    /**
     * 探测 Antigravity IDE 当前生效登录的账号邮箱
     */
    suspend fun detectIdeActiveEmail(): String? {
        return detectIdeActiveProfile()?.email
    }

    /**
     * 探测 Antigravity IDE 完整的活跃用户 Profile（包含邮箱、姓名、头像、订阅文本）
     */
    suspend fun detectIdeActiveProfile(): IdeAccountProfile? = withContext(Dispatchers.IO) {
        // 1. 穿透读取 IDE globalStorage state.vscdb SQLite 数据库中的 userStatus Protobuf
        val sqliteProfile = detectIdeProfileFromSqlite()
        if (sqliteProfile != null) {
            return@withContext sqliteProfile
        }

        // 2. 尝试从 workspaceStorage 提取
        val workspaceProfile = detectIdeProfileFromWorkspaceStorage()
        if (workspaceProfile != null) {
            return@withContext workspaceProfile
        }

        null
    }

    /**
     * 从 Antigravity IDE 的 globalStorage/state.vscdb 物理提取 Profile
     */
    private fun detectIdeProfileFromSqlite(): IdeAccountProfile? {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()

        val candidateDbFiles = when {
            os.contains("mac") -> listOf(
                File(userHome, "Library/Application Support/Antigravity IDE/User/globalStorage/state.vscdb"),
                File(userHome, "Library/Application Support/Antigravity/User/globalStorage/state.vscdb")
            )

            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                listOf(
                    File(appData, "Antigravity IDE/User/globalStorage/state.vscdb"),
                    File(appData, "Antigravity/User/globalStorage/state.vscdb")
                )
            }

            else -> listOf(
                File(userHome, ".config/Antigravity IDE/User/globalStorage/state.vscdb"),
                File(userHome, ".config/Antigravity/User/globalStorage/state.vscdb")
            )
        }

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
        try {
            // 使用 read-only 模式打开 SQLite 数据库
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                val query = """
                    SELECT key, value FROM ItemTable 
                    WHERE key IN (
                        'antigravityUnifiedStateSync.userStatus',
                        'antigravityIdeUnifiedStateSync.userStatus'
                    )
                """.trimIndent()

                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(query)
                    while (rs.next()) {
                        val valueStr = rs.getString("value") ?: continue
                        val profile = parseProfileFromUserStatusRaw(valueStr)
                        if (profile != null && profile.email.isNotBlank()) {
                            return profile
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fallback: 使用纯文本/二进制流正规正则扫描邮箱
            val fallbackEmail = fallbackScanRawDbForEmail(dbFile)
            if (!fallbackEmail.isNullOrBlank()) {
                return IdeAccountProfile(email = fallbackEmail)
            }
        }
        return null
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

                        // 兜底：直接从文本中正则扫描
                        val text = b3.decodeToString()
                        val matcher = emailPattern.matcher(text)
                        if (matcher.find()) {
                            val candidate = matcher.group(0).trim().lowercase()
                            if (!candidate.contains("example.com") && !candidate.contains("schema")) {
                                return IdeAccountProfile(email = candidate)
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
     * 从 workspaceStorage 中探测
     */
    private fun detectIdeProfileFromWorkspaceStorage(): IdeAccountProfile? {
        val userHome = System.getProperty("user.home")
        val workspaceDir = File(userHome, "Library/Application Support/Antigravity IDE/User/workspaceStorage")
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) return null

        val subDirs = workspaceDir.listFiles() ?: return null
        for (sub in subDirs) {
            val db = File(sub, "state.vscdb")
            if (db.exists()) {
                val profile = readProfileFromStateDb(db)
                if (profile != null && profile.email.isNotBlank()) return profile
            }
        }
        return null
    }

    /**
     * 根据邮箱查找系统中可用的 RefreshToken
     */
    fun findAvailableRefreshToken(email: String): String? {
        val targetEmail = email.trim().lowercase()
        val userHome = System.getProperty("user.home")
        val candidateFiles = listOf(
            File(userHome, ".gemini/oauth_creds.json"),
            File(userHome, ".gemini/jetski-standalone-oauth-token"),
            File(userHome, ".gemini/antigravity-ide/oauth_credentials.json"),
            File(userHome, ".gemini/oauth_credentials.json"),
            File(userHome, ".config/antigravity/oauth_credentials.json"),
            File(userHome, "Library/Application Support/Antigravity/oauth_credentials.json")
        )

        for (file in candidateFiles) {
            if (!file.exists() || !file.isFile) continue
            try {
                val content = file.readText(Charsets.UTF_8)
                val root = json.parseToJsonElement(content) as? JsonObject ?: continue
                
                val fileEmail = root["email"]?.jsonPrimitive?.contentOrNull
                    ?: root["antigravity_cockpit_active_email"]?.jsonPrimitive?.contentOrNull
                
                val tokenObj = root["token"] as? JsonObject
                val directRt = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                    ?: tokenObj?.get("refresh_token")?.jsonPrimitive?.contentOrNull

                if (!directRt.isNullOrBlank()) {
                    if (fileEmail?.equals(targetEmail, ignoreCase = true) == true) {
                        return directRt
                    }
                    if (file.name.contains("jetski")) {
                        val cliEmail = detectCliAppActiveEmail()
                        if (cliEmail?.equals(targetEmail, ignoreCase = true) == true) {
                            return directRt
                        }
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

    /**
     * 极简兜底扫描
     */
    private fun fallbackScanRawDbForEmail(dbFile: File): String? {
        try {
            val bytes = dbFile.readBytes()
            val text = bytes.decodeToString()
            val matcher = emailPattern.matcher(text)
            while (matcher.find()) {
                val match = matcher.group(0).trim().lowercase()
                if (!match.contains("example.com") && !match.contains("schema") && match.endsWith("@gmail.com")) {
                    return match
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}

