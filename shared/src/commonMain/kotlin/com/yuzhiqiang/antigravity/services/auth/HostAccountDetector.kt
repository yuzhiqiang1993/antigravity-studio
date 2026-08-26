package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
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
 * IDE 使用 state.vscdb 中的官方 userStatus；App 在 macOS 优先使用运行中 language_server 的 RPC，
 * 运行态不可用时仅在显式授权后读取 Keychain。文件凭据和日志只能作为非 macOS 的兼容回退，不能冒充 macOS App 的运行态。
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
     * 探测 App / CLI 的账号 Profile。
     *
     * 探测与仲裁机制（仅用于 CLI/非 macOS 兼容路径）：
     * 1. 存在 jetski 独立凭据时，用 Refresh Token 与 Studio 已导入账号做精确匹配；
     * 2. 独立凭据存在但无法归属时返回未知，禁止使用旧文件或配额缓存猜测账号；
     * 3. 不存在独立凭据时，才使用官方静态凭据与最近认证日志进行兼容探测。
     *
     * 此方法不代表 macOS App 的运行态；macOS App 的权威探测必须走 [detectAppActiveProfile]。
     */
    fun detectCliAppProfile(
        customCredentialsFile: File? = null,
        knownAccounts: List<AccountInfo> = emptyList()
    ): CliAppAccountProfile? {
        val staticSnapshot = detectStaticCliAppProfile(customCredentialsFile)
        val staticProfile = staticSnapshot?.first
        val staticFileMtime = staticSnapshot?.second ?: 0L
        if (customCredentialsFile != null) {
            return staticProfile
        }

        val activeCredentialFile = resolveActiveCredentialFile()
        if (activeCredentialFile != null) {
            return resolveProfileFromActiveCredential(activeCredentialFile, knownAccounts)
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

    private fun resolveActiveCredentialFile(): File? {
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        val dataDir = customDataDir
            ?.takeIf { path -> path.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.home"), ".gemini")
        return File(dataDir, "jetski-standalone-oauth-token")
            .takeIf { file -> file.isFile }
    }

    private fun resolveProfileFromActiveCredential(
        credentialFile: File,
        knownAccounts: List<AccountInfo>
    ): CliAppAccountProfile? {
        return try {
            val root = json.parseToJsonElement(credentialFile.readText(Charsets.UTF_8)) as? JsonObject
                ?: return null
            val tokenObj = root["token"] as? JsonObject ?: root
            val refreshToken = tokenObj["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?: tokenObj["refreshToken"]?.jsonPrimitive?.contentOrNull
            val idToken = tokenObj["id_token"]?.jsonPrimitive?.contentOrNull
            val matchedAccount = refreshToken
                ?.takeIf { token -> token.isNotBlank() }
                ?.let { token ->
                    knownAccounts.firstOrNull { account ->
                        account.tokens.refreshToken.isNotBlank() && account.tokens.refreshToken == token
                    }
                }
            if (matchedAccount != null) {
                return CliAppAccountProfile(
                    email = matchedAccount.email.lowercase(),
                    name = matchedAccount.profile.name,
                    expiryTimestamp = matchedAccount.tokens.expiryTimestamp,
                    tokenType = tokenObj["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
                )
            }

            val email = idToken?.let(::parseEmailFromJwt) ?: return null
            CliAppAccountProfile(
                email = email.lowercase(),
                expiryTimestamp = resolveExpiryTimestamp(tokenObj),
                tokenType = tokenObj["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 严格探测 App / CLI 当前凭据文件中的账号邮箱。
     * 运行日志只作为宽松诊断证据，不参与切号成功确认。
     */
    fun detectCliAppActiveEmail(customCredentialsFile: File? = null): String? {
        return detectStaticCliAppProfile(customCredentialsFile)?.first?.email
    }

    private fun detectStaticCliAppProfile(customCredentialsFile: File?): Pair<CliAppAccountProfile, Long>? {
        val latestFile = resolveCliCredentialFiles(customCredentialsFile)
            .filter { file -> file.isFile }
            .maxByOrNull { file -> file.lastModified() }
            ?: return null
        return readCliAppProfile(latestFile)?.let { profile -> profile to latestFile.lastModified() }
    }

    private fun resolveCliCredentialFiles(customCredentialsFile: File?): List<File> {
        if (customCredentialsFile != null) {
            return listOf(customCredentialsFile)
        }

        val userHome = System.getProperty("user.home")
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        if (!customDataDir.isNullOrBlank()) {
            return listOf(File(customDataDir, "oauth_credentials.json"))
        }
        return listOf(
            File(userHome, "Library/Application Support/Antigravity/oauth_credentials.json"),
            File(userHome, ".gemini/oauth_creds.json"),
            File(userHome, ".gemini/oauth_credentials.json"),
            File(System.getenv("APPDATA") ?: "$userHome/AppData/Roaming", "Antigravity/oauth_credentials.json"),
            File(
                System.getenv("XDG_CONFIG_HOME") ?: File(userHome, ".config").absolutePath,
                "antigravity/oauth_credentials.json"
            )
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
     * 探测 Antigravity App 当前生效登录的账号 Profile。
     *
     * App 运行时优先读取本地 language_server 的官方状态接口；仅当调用方显式允许且运行态不可用时，
     * macOS 才读取 Keychain 中的下次启动凭据。发现 App 进程但 RPC 无法确认账号时，不回退到 CLI 静态凭据。
     */
    suspend fun detectAppActiveProfile(
        knownAccounts: List<AccountInfo> = emptyList(),
        allowKeychainAccess: Boolean = false
    ): IdeAccountProfile? = withContext(Dispatchers.IO) {
        val macOs = isMacOs()
        val runtimeResult = RuntimeAppAccountProbe.detectProfile()
        val runtimeProfile = runtimeResult.getOrNull()
        if (runtimeProfile != null) {
            return@withContext runtimeProfile
        }

        // 进程已发现但运行态接口失败/响应无效时，禁止用 CLI 或旧状态文件猜测 App 账号。
        if (runtimeResult.isFailure && !macOs) {
            return@withContext null
        }

        if (macOs) {
            if (!allowKeychainAccess) {
                return@withContext null
            }
            return@withContext MacKeychainInjector.readMatchingAccount(knownAccounts)
                .getOrNull()
                ?.let { account ->
                    IdeAccountProfile(
                        email = account.email,
                        name = account.profile.name
                    )
                }
        }

        val cliAppProfile = detectCliAppProfile(knownAccounts = knownAccounts)
        if (cliAppProfile != null) {
            return@withContext IdeAccountProfile(
                email = cliAppProfile.email,
                name = cliAppProfile.name
            )
        }
        detectProfileFromCanonicalStateDb(StateDbInjector.TargetHost.APP)
    }

    /**
     * 探测 Antigravity App 当前生效登录的账号邮箱
     */
    suspend fun detectAppActiveEmail(
        knownAccounts: List<AccountInfo> = emptyList(),
        allowKeychainAccess: Boolean = false
    ): String? {
        return detectAppActiveProfile(knownAccounts, allowKeychainAccess)?.email
    }

    /**
     * 探测 Antigravity IDE 完整的活跃用户 Profile（包含邮箱、姓名、头像、订阅文本）
     */
    suspend fun detectIdeActiveProfile(): IdeAccountProfile? = withContext(Dispatchers.IO) {
        detectProfileFromCanonicalStateDb(StateDbInjector.TargetHost.IDE)
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
