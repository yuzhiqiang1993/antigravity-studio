package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
data class AccountStoreData(
    val version: Int = 1,
    val accounts: List<AccountInfo> = emptyList(),
    val activeAccountId: String? = null
)

/**
 * 账号与凭据持久化存储器。
 * 支持多账号安全持久化存储，并双向兼容官方 OAuth 凭据文件。
 */
class AccountStore(
    private val customRootDir: File? = null
) {
    /**
     * 官方 OAuth 凭据文件在切换前的物理快照。
     *
     * @property file 快照对应的官方凭据文件
     * @property existed 捕获快照时文件是否存在
     * @property originalBytes 捕获到的原始字节
     */
    internal data class OfficialCredentialsSnapshot(
        val file: File,
        val existed: Boolean,
        val originalBytes: ByteArray
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val mutex = Mutex()

    private val rootDir: File by lazy {
        customRootDir ?: resolveDefaultRootDir()
    }

    private val accountsFile: File by lazy {
        File(rootDir, "accounts.v1.json")
    }

    private val _accountsState = MutableStateFlow<List<AccountInfo>>(emptyList())
    val accountsState: StateFlow<List<AccountInfo>> = _accountsState.asStateFlow()

    private val _activeAccountState = MutableStateFlow<AccountInfo?>(null)
    val activeAccountState: StateFlow<AccountInfo?> = _activeAccountState.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        loadAccounts()
    }

    fun currentAccounts(): List<AccountInfo> = _accountsState.value

    fun currentActiveAccount(): AccountInfo? = _activeAccountState.value

    /**
     * 加载本地账号列表。
     * 若 accounts.v1.json 不存在但系统已有官方 OAuth 凭据，自动导入为初始激活账号。
     */
    fun loadAccounts(): Result<List<AccountInfo>> {
        try {
            if (!accountsFile.exists()) {
                val officialAccount = importOfficialCredentials()
                if (officialAccount != null) {
                    val initialList = listOf(officialAccount)
                    saveAccountsInternal(initialList, officialAccount.id)
                    _accountsState.value = initialList
                    _activeAccountState.value = officialAccount
                    _loadError.value = null
                    return Result.success(initialList)
                }
                _accountsState.value = emptyList()
                _activeAccountState.value = null
                _loadError.value = null
                return Result.success(emptyList())
            }

            val text = accountsFile.readText(Charsets.UTF_8)
            val data = json.decodeFromString<AccountStoreData>(text)
            val accounts = data.accounts
            val active = accounts.firstOrNull { it.id == data.activeAccountId || it.isActive }
                ?: accounts.firstOrNull()

            val normalizedAccounts = accounts.map { acc ->
                acc.copy(isActive = acc.id == active?.id)
            }

            _accountsState.value = normalizedAccounts
            _activeAccountState.value = active
            _loadError.value = null
            return Result.success(normalizedAccounts)
        } catch (e: Exception) {
            val errorMsg = "加载账号配置失败: ${e.message ?: "未知错误"}"
            _loadError.value = errorMsg
            return Result.failure(IllegalStateException(errorMsg, e))
        }
    }

    /**
     * 保存或更新单个账号
     */
    suspend fun upsertAccount(account: AccountInfo): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == account.id || it.email.equals(account.email, ignoreCase = true) }
            val isFirstAccount = current.isEmpty()
            val makeActive = account.isActive || isFirstAccount || (_activeAccountState.value == null)

            val updatedAccount = account.copy(isActive = makeActive)
            if (existingIndex >= 0) {
                current[existingIndex] = updatedAccount
            } else {
                current.add(updatedAccount)
            }

            val finalAccounts = if (makeActive) {
                current.map { it.copy(isActive = it.id == updatedAccount.id) }
            } else {
                current
            }

            val active = finalAccounts.firstOrNull { it.isActive }
            saveAccountsInternal(finalAccounts, active?.id)
            _accountsState.value = finalAccounts
            _activeAccountState.value = active

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 设为当前激活账号
     */
    suspend fun setActiveAccount(idOrEmail: String): Result<AccountInfo> = mutex.withLock {
        try {
            val current = _accountsState.value
            val target = current.firstOrNull { it.id == idOrEmail || it.email.equals(idOrEmail, ignoreCase = true) }
                ?: return Result.failure(IllegalArgumentException("未找到指定账号: $idOrEmail"))

            val updatedAccounts = current.map { acc ->
                acc.copy(isActive = acc.id == target.id)
            }
            val active = target.copy(isActive = true)

            saveAccountsInternal(updatedAccounts, active.id)
            _accountsState.value = updatedAccounts
            _activeAccountState.value = active

            Result.success(active)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 移除指定账号
     */
    suspend fun removeAccount(idOrEmail: String): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val removed = current.removeAll { it.id == idOrEmail || it.email.equals(idOrEmail, ignoreCase = true) }
            if (!removed) {
                return Result.success(Unit)
            }

            val currentActive = _activeAccountState.value
            val wasActiveRemoved = currentActive == null || currentActive.id == idOrEmail || currentActive.email.equals(idOrEmail, ignoreCase = true)
            val newActive = if (wasActiveRemoved) current.firstOrNull() else currentActive

            val updatedAccounts = current.map { acc ->
                acc.copy(isActive = acc.id == newActive?.id)
            }

            saveAccountsInternal(updatedAccounts, newActive?.id)
            _accountsState.value = updatedAccounts
            _activeAccountState.value = newActive

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新账号凭据与元数据
     */
    suspend fun updateTokens(
        email: String,
        tokens: OAuthTokens,
        name: String? = null,
        avatarUrl: String? = null
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val index = current.indexOfFirst { it.email.equals(email, ignoreCase = true) }
            if (index < 0) {
                return Result.failure(IllegalArgumentException("账号不存在: $email"))
            }

            val old = current[index]
            val updatedProfile = old.profile.copy(
                name = name ?: old.profile.name,
                avatarUrl = avatarUrl ?: old.profile.avatarUrl
            )
            val updatedAccount = old.copy(
                profile = updatedProfile,
                tokens = tokens,
                status = AccountStatus.ACTIVE,
                lastRefreshedAt = System.currentTimeMillis(),
                lastErrorMessage = null
            )
            current[index] = updatedAccount

            val active = current.firstOrNull { it.isActive }
            saveAccountsInternal(current, active?.id)
            _accountsState.value = current
            if (updatedAccount.isActive) {
                _activeAccountState.value = updatedAccount
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新账号自定义备注/昵称
     */
    suspend fun updateAccountNote(id: String, note: String?): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return Result.failure(IllegalArgumentException("未找到账号: $id"))

            current[index] = current[index].copy(customNote = note?.trim()?.takeIf { it.isNotEmpty() })
            saveAccountsInternal(current, _activeAccountState.value?.id)
            _accountsState.value = current
            if (current[index].isActive) {
                _activeAccountState.value = current[index]
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 切换账号置顶状态
     */
    suspend fun togglePinAccount(id: String): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return Result.failure(IllegalArgumentException("未找到账号: $id"))

            val old = current[index]
            current[index] = old.copy(isPinned = !old.isPinned)
            saveAccountsInternal(current, _activeAccountState.value?.id)
            _accountsState.value = current
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 清理所有过期或错误的非激活账号
     */
    suspend fun cleanInvalidAccounts(): Result<Int> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val active = _activeAccountState.value
            val initialSize = current.size

            current.removeAll { acc ->
                acc.id != active?.id && (acc.status == AccountStatus.ERROR || acc.tokens.isExpired())
            }

            val removedCount = initialSize - current.size
            if (removedCount > 0) {
                saveAccountsInternal(current, active?.id)
                _accountsState.value = current
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 导出所有账号凭据（完全对齐 Cockpit 插件标准 JSON 数组格式）
     */
    fun exportAccountsJson(): String {
        val current = _accountsState.value
        val exportList = current
            .filter { it.tokens.refreshToken.isNotBlank() }
            .map { acc ->
                buildJsonObject {
                    put("email", JsonPrimitive(acc.email))
                    put("refresh_token", JsonPrimitive(acc.tokens.refreshToken))
                    put("refreshToken", JsonPrimitive(acc.tokens.refreshToken))
                    acc.profile.name?.takeIf { it.isNotBlank() }?.let {
                        put("name", JsonPrimitive(it))
                    }
                    acc.customNote?.takeIf { it.isNotBlank() }?.let {
                        put("custom_note", JsonPrimitive(it))
                    }
                }
            }
        val jsonArray = JsonArray(exportList)
        return json.encodeToString(JsonArray.serializer(), jsonArray)
    }

    /**
     * 标记账号错误状态
     */
    suspend fun markAccountError(email: String, errorMessage: String): Result<Unit> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val index = current.indexOfFirst { it.email.equals(email, ignoreCase = true) }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(
                    status = AccountStatus.ERROR,
                    lastErrorMessage = errorMessage
                )
                saveAccountsInternal(current, _activeAccountState.value?.id)
                _accountsState.value = current
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun saveAccountsInternal(accounts: List<AccountInfo>, activeAccountId: String?) {
        val data = AccountStoreData(
            version = 1,
            accounts = accounts,
            activeAccountId = activeAccountId
        )
        val text = json.encodeToString(AccountStoreData.serializer(), data)
        writeTextAtomically(accountsFile, text)
    }

    /**
     * 尝试从官方 OAuth 凭据文件导入激活账号。
     */
    fun importOfficialCredentials(): AccountInfo? {
        val file = officialCredentialsFile()
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            val element = json.parseToJsonElement(text) as? JsonObject ?: return null
            val accessToken = element["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
            val refreshToken = element["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
            val email = element["email"]?.jsonPrimitive?.contentOrNull
                ?: element["user_email"]?.jsonPrimitive?.contentOrNull
                ?: "default-user@antigravity"
            val name = element["name"]?.jsonPrimitive?.contentOrNull
            val expiryTimestamp = resolveExpiryTimestamp(element)
            val tokenType = element["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
            val idToken = element["id_token"]?.jsonPrimitive?.contentOrNull

            AccountInfo(
                id = "acc_${email.hashCode().toUInt().toString(16)}",
                profile = AccountProfile(
                    email = email,
                    name = name
                ),
                tokens = OAuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiryTimestamp = expiryTimestamp,
                    tokenType = tokenType,
                    idToken = idToken
                ),
                isActive = true,
                status = AccountStatus.ACTIVE
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 同步当前激活账号至官方 OAuth 凭据文件。
     */
    fun syncToOfficialCredentials(account: AccountInfo): Boolean {
        return try {
            val file = officialCredentialsFile()
            val fields = readOfficialCredentialFields(file)
            val expiryTimestamp = normalizeEpochSeconds(account.tokens.expiryTimestamp)

            fields["access_token"] = JsonPrimitive(account.tokens.accessToken)
            fields["refresh_token"] = JsonPrimitive(account.tokens.refreshToken)
            fields["email"] = JsonPrimitive(account.email)
            fields["name"] = JsonPrimitive(account.profile.name ?: "")
            fields["expiry_timestamp"] = JsonPrimitive(expiryTimestamp)
            fields["expiry_date"] = JsonPrimitive(expiryTimestamp * MILLIS_PER_SECOND)
            fields["token_type"] = JsonPrimitive(account.tokens.tokenType)
            if ("user_email" in fields) {
                fields["user_email"] = JsonPrimitive(account.email)
            }
            if ("antigravity_cockpit_active_email" in fields) {
                fields["antigravity_cockpit_active_email"] = JsonPrimitive(account.email)
            }

            val idToken = account.tokens.idToken?.takeIf { it.isNotBlank() }
            if (idToken != null) {
                fields["id_token"] = JsonPrimitive(idToken)
            } else {
                fields.remove("id_token")
            }

            val content = json.encodeToString(JsonObject.serializer(), JsonObject(fields))
            writeTextAtomically(file, content)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 捕获官方 OAuth 凭据文件的原始字节，供切换失败时回滚。
     */
    internal fun captureOfficialCredentialsSnapshot(): Result<OfficialCredentialsSnapshot> {
        val file = officialCredentialsFile()
        return try {
            val existed = file.exists()
            val originalBytes = if (existed) {
                file.readBytes()
            } else {
                byteArrayOf()
            }
            Result.success(
                OfficialCredentialsSnapshot(
                    file = file,
                    existed = existed,
                    originalBytes = originalBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 恢复官方 OAuth 凭据文件快照。
     */
    internal fun restoreOfficialCredentialsSnapshot(snapshot: OfficialCredentialsSnapshot): Boolean {
        return try {
            val officialFile = officialCredentialsFile()
            if (snapshot.file.canonicalFile != officialFile.canonicalFile) {
                return false
            }

            if (snapshot.existed) {
                writeBytesAtomically(officialFile, snapshot.originalBytes)
                true
            } else {
                !officialFile.exists() || (officialFile.isFile && officialFile.delete())
            }
        } catch (_: Exception) {
            false
        }
    }

    fun officialCredentialsFile(): File {
        if (customRootDir != null) {
            return File(customRootDir, "oauth_credentials.json")
        }
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        if (!customDataDir.isNullOrBlank()) {
            return File(customDataDir, "oauth_credentials.json")
        }
        val userHome = System.getProperty("user.home")
        return File(userHome, ".gemini/oauth_creds.json")
    }

    private fun resolveExpiryTimestamp(element: JsonObject): Long {
        val rawTimestamp = element["expiry_timestamp"]?.jsonPrimitive?.longOrNull
            ?: element["expiry_date"]?.jsonPrimitive?.longOrNull
            ?: (System.currentTimeMillis() / MILLIS_PER_SECOND + DEFAULT_TOKEN_LIFETIME_SECONDS)
        return normalizeEpochSeconds(rawTimestamp)
    }

    private fun normalizeEpochSeconds(timestamp: Long): Long {
        return if (timestamp >= EPOCH_MILLIS_THRESHOLD) {
            timestamp / MILLIS_PER_SECOND
        } else {
            timestamp
        }
    }

    private fun readOfficialCredentialFields(file: File): MutableMap<String, JsonElement> {
        if (!file.exists()) {
            return mutableMapOf()
        }

        return try {
            val existing = json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
            existing?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }


    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private const val EPOCH_MILLIS_THRESHOLD = 10_000_000_000L
        private const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3_600L

        fun resolveDefaultRootDir(): File {
            val studioHome = System.getenv("ANTIGRAVITY_STUDIO_HOME")
            if (!studioHome.isNullOrBlank()) {
                return File(studioHome)
            }

            val userHome = System.getProperty("user.home")
            val osName = System.getProperty("os.name", "").lowercase()
            return when {
                osName.contains("mac") -> File(userHome, "Library/Application Support/Antigravity Studio")
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")
                        ?.takeIf { it.isNotBlank() }
                        ?: File(userHome, "AppData/Roaming").absolutePath
                    File(appData, "Antigravity Studio")
                }
                else -> {
                    val configHome = System.getenv("XDG_CONFIG_HOME")
                        ?.takeIf { it.isNotBlank() }
                        ?: File(userHome, ".config").absolutePath
                    File(configHome, "Antigravity Studio")
                }
            }
        }
    }

    private fun writeTextAtomically(file: File, content: String) {
        writeBytesAtomically(file, content.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytesAtomically(file: File, content: ByteArray) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val temp = File.createTempFile("${file.name}-", ".tmp", parent)
        try {
            temp.writeBytes(content)
            val moved = try {
                try {
                    Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (!moved) {
                file.writeBytes(content)
            }
        } finally {
            if (temp.exists()) {
                temp.delete()
            }
        }
    }
}
