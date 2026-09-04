package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

@Serializable
data class AccountStoreData(
    val version: Int = 1,
    val accounts: List<AccountInfo> = emptyList(),
    val activeAccountId: String? = null
)

@Serializable
data class ActiveAccountSnapshot(
    val version: Int = 1,
    val accountId: String,
    val email: String,
    val source: String = "antigravity-studio",
    val switchedAt: Long
)

/**
 * Studio 账号状态持久化存储器。
 * 官方 OAuth 凭据文件由 [OfficialCredentialsStore] 独立管理，本类只提供事务门面。
 */
class AccountStore(
    private val customRootDir: File? = null
) {
    private val officialCredentialsStore = OfficialCredentialsStore(customRootDir)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val mutex = Mutex()

    private val rootDir: File by lazy {
        customRootDir ?: AppDataPaths.rootDir()
    }

    private val accountsFile: File by lazy {
        File(rootDir, AppDataPaths.ACCOUNTS_FILE_NAME)
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
            val existingIndex =
                current.indexOfFirst { it.id == account.id || it.email.equals(account.email, ignoreCase = true) }
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
            val wasActiveRemoved = currentActive == null || currentActive.id == idOrEmail || currentActive.email.equals(
                idOrEmail,
                ignoreCase = true
            )
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
                tokens = old.tokens.mergeRefreshResult(tokens),
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
     * 在外部凭据与宿主验证全部成功后，一次性提交目标账号 Token 与活跃状态。
     */
    internal suspend fun commitSwitchedAccount(account: AccountInfo): Result<AccountInfo> = mutex.withLock {
        try {
            val current = _accountsState.value.toMutableList()
            val index = current.indexOfFirst { stored ->
                stored.id == account.id || stored.email.equals(account.email, ignoreCase = true)
            }
            if (index < 0) {
                return Result.failure(IllegalArgumentException("账号不存在: ${account.email}"))
            }

            val old = current[index]
            val committed = old.copy(
                profile = account.profile,
                tokens = old.tokens.mergeRefreshResult(account.tokens),
                isActive = true,
                status = AccountStatus.ACTIVE,
                lastRefreshedAt = System.currentTimeMillis(),
                lastErrorMessage = null
            )
            val updatedAccounts = current.mapIndexed { itemIndex, stored ->
                if (itemIndex == index) committed else stored.copy(isActive = false)
            }

            saveAccountsInternal(updatedAccounts, committed.id)
            writeActiveAccountSnapshot(committed)
            _accountsState.value = updatedAccounts
            _activeAccountState.value = committed
            Result.success(committed)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun writeActiveAccountSnapshot(account: AccountInfo) {
        runCatching {
            val dir = customRootDir ?: File(System.getProperty("user.home"), ".gemini/antigravity")
            dir.mkdirs()
            val targetFile = File(dir, "active-account.json")
            val snapshot = ActiveAccountSnapshot(
                accountId = account.id,
                email = account.email,
                switchedAt = System.currentTimeMillis()
            )
            val payload = json.encodeToString(ActiveAccountSnapshot.serializer(), snapshot)
            AtomicFileWriter.writeText(
                target = targetFile,
                content = payload,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY
            )
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
    fun importOfficialCredentials(): AccountInfo? = officialCredentialsStore.importAccount()

    /**
     * 同步当前激活账号至 App 与 CLI 共用的官方 OAuth 凭据文件。
     */
    internal fun syncToOfficialCredentials(
        account: AccountInfo,
        snapshot: OfficialCredentialsStore.Snapshot
    ): Boolean = officialCredentialsStore.sync(account, snapshot)

    /**
     * 捕获官方 OAuth 凭据文件的原始字节，供切换失败时回滚。
     */
    internal fun captureOfficialCredentialsSnapshot(
        targetFiles: List<File>? = null
    ): Result<OfficialCredentialsStore.Snapshot> = officialCredentialsStore.captureSnapshot(targetFiles)

    /**
     * 恢复官方 OAuth 凭据文件快照。
     */
    internal fun restoreOfficialCredentialsSnapshot(snapshot: OfficialCredentialsStore.Snapshot): Boolean =
        officialCredentialsStore.restoreSnapshot(snapshot)

    fun officialCredentialsFile(): File = officialCredentialsStore.primaryFile()

    private fun writeTextAtomically(file: File, content: String) {
        AtomicFileWriter.writeText(
            target = file,
            content = content,
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true
        ).getOrThrow()
    }


}
