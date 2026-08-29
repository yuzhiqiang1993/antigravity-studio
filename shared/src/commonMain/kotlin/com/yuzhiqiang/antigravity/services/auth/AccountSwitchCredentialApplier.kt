package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.OfficialCredentialsStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 负责账号切换过程中的凭据快照捕获、Token 刷新、系统钥匙串及各宿主凭据写入。
 */
internal class AccountSwitchCredentialApplier(
    private val accountStore: AccountStore,
    private val tokenRefresher: suspend (String) -> Result<OAuthTokens>,
    private val systemCredentialStore: SystemCredentialStore
) {
    private fun log(stage: String, message: String) {
        AppLog.i("Auth/Switch") { "[$stage] $message" }
    }

    fun captureOriginalState(request: AccountSwitchSession.Request): OriginalState {
        val ideSnapshot = if (request.applyToIde && hasStateDb(StateDbInjector.TargetHost.IDE)) {
            StateDbInjector.capture(StateDbInjector.TargetHost.IDE).getOrThrow()
        } else {
            null
        }
        val appDbSnapshot = if (request.applyToAppCli && hasStateDb(StateDbInjector.TargetHost.APP)) {
            StateDbInjector.capture(StateDbInjector.TargetHost.APP).getOrThrow()
        } else {
            null
        }

        var sharedCredentialsSnapshot: OfficialCredentialsStore.Snapshot? = null
        var jetskiTokenSnapshot: FileSnapshot? = null
        return try {
            if (request.applyToAppCli) {
                sharedCredentialsSnapshot = accountStore.captureOfficialCredentialsSnapshot().getOrThrow()
                jetskiTokenSnapshot = captureFileSnapshot(resolveJetskiStandaloneTokenFile())
            }
            val systemCredentialSnapshot = if (request.applyToAppCli) {
                systemCredentialStore.capture().getOrThrow()
            } else {
                null
            }
            OriginalState(
                ideSnapshot = ideSnapshot,
                appDbSnapshot = appDbSnapshot,
                sharedCredentialsSnapshot = sharedCredentialsSnapshot,
                jetskiTokenSnapshot = jetskiTokenSnapshot,
                systemCredentialSnapshot = systemCredentialSnapshot
            )
        } catch (error: Exception) {
            sharedCredentialsSnapshot?.close()
            jetskiTokenSnapshot?.close()
            throw error
        }
    }

    suspend fun prepareTargetAccount(request: AccountSwitchSession.Request): AccountInfo {
        val storedTargetAccount = accountStore.currentAccounts().firstOrNull { account ->
            account.id == request.targetAccount.id ||
                    account.email.equals(request.targetAccount.email, ignoreCase = true)
        } ?: request.targetAccount
        if (!request.applyToAppCli || storedTargetAccount.tokens.refreshToken.isBlank()) {
            return storedTargetAccount
        }

        log("联网刷新Token", "正在向 Google 刷新 " + storedTargetAccount.email + " 的最新 Access/ID Token...")
        val refreshResult = tokenRefresher(storedTargetAccount.tokens.refreshToken).getOrNull()
            ?: return storedTargetAccount.also {
                log("Token刷新失败", "使用当前缓存的 Token 继续切号")
            }
        val refreshedAccount = storedTargetAccount.copy(
            tokens = storedTargetAccount.tokens.mergeRefreshResult(refreshResult)
        )
        accountStore.updateTokens(
            email = refreshedAccount.email,
            tokens = refreshedAccount.tokens,
            name = refreshedAccount.profile.name,
            avatarUrl = refreshedAccount.profile.avatarUrl
        ).getOrThrow()
        log(
            "Token刷新成功",
            "已持久化最新 Token (idTokenLen=" + (refreshedAccount.tokens.idToken?.length ?: 0) + ")"
        )
        return accountStore.currentAccounts().firstOrNull { account -> account.id == refreshedAccount.id }
            ?: refreshedAccount
    }

    suspend fun applyCredentials(
        request: AccountSwitchSession.Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges,
        sharedCredentialsSnapshot: OfficialCredentialsStore.Snapshot?
    ): AccountInfo {
        request.progressCallback?.invoke("2/4 正在写入目标账号与 App & CLI 共享凭据...")
        log(
            "切号开始",
            "目标账号: " + request.targetAccount.email + ", applyToIde=" + request.applyToIde + ", applyToAppCli=" + request.applyToAppCli
        )

        val targetAccount = request.targetAccount

        if (request.applyToIde && (!ideWasRunning || request.restartIde)) {
            val ideDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.IDE)
                .any { file -> file.isFile }
            if (IdeHostManager.isInstalled(request.ideInstallationPath) && ideDbExists) {
                log("IDE凭据注入", "正在向 IDE state.vscdb 写入账号数据...")
                requireStep(
                    StateDbInjector.inject(targetAccount, StateDbInjector.TargetHost.IDE),
                    "IDE 账号数据库写入失败"
                )
                changes.ideDbWritten = true
                log("IDE凭据注入完成", "已更新 IDE state.vscdb")
            } else {
                changes.ideUnavailable = true
            }
        }

        if (!request.applyToAppCli) {
            return targetAccount
        }

        // 同步写入官方与镜像 OAuth 凭据文件
        log("凭据文件同步", "正在写入 ~/.gemini/oauth_creds.json 及镜像文件...")
        val credentialSnapshot = sharedCredentialsSnapshot
            ?: throw IllegalStateException("App & CLI 共享 OAuth 凭据快照缺失")
        changes.sharedCredentialsWriteAttempted = true
        requireStep(
            accountStore.syncToOfficialCredentials(targetAccount, credentialSnapshot),
            "App & CLI 共享 OAuth 凭据文件写入失败"
        )
        changes.sharedCredentialsWritten = true
        changes.appUnavailable = !AppHostManager.isInstalled(request.appInstallationPath)

        // 核心突破：注入系统级安全存储 (macOS Keychain: service=gemini, account=antigravity)
        log("Keychain注入", "正在向系统钥匙串写入 Antigravity 认证凭据...")
        changes.systemCredentialWriteAttempted = true
        val keychainResult = systemCredentialStore.inject(targetAccount)
        requireStep(
            keychainResult.isSuccess,
            "系统钥匙串凭据注入失败: " + keychainResult.exceptionOrNull()?.message
        )

        if (appWasRunning && !request.restartApp) {
            return targetAccount
        }

        val appDbExists = StateDbInjector.resolveCandidateDbFiles(StateDbInjector.TargetHost.APP)
            .any { file -> file.isFile }
        changes.jetskiTokenWriteAttempted = true
        // 这些文件是 App 对共享 OAuth 的兼容投影，不是独立于 CLI 的另一套认证凭据。
        requireStep(
            writeJetskiStandaloneToken(targetAccount),
            "App 共享凭据兼容投影写入失败"
        )
        changes.jetskiTokenWritten = true

        if (appDbExists) {
            requireStep(
                StateDbInjector.inject(targetAccount, StateDbInjector.TargetHost.APP),
                "App 共享凭据运行态投影写入失败"
            )
            changes.appDbWritten = true
        }
        log("App凭据写入完成", "所有文件与系统钥匙串已就绪")
        return targetAccount
    }

    private fun hasStateDb(targetHost: StateDbInjector.TargetHost): Boolean {
        return StateDbInjector.resolveCandidateDbFiles(targetHost).any { file -> file.isFile }
    }

    private fun requireStep(isSuccess: Boolean, message: String) {
        if (!isSuccess) {
            throw IllegalStateException(message)
        }
    }

    companion object {
        fun resolveJetskiStandaloneTokenFile(): File {
            val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
                ?: System.getenv("GEMINI_DATA_DIR")
            val dataDir = customDataDir
                ?.takeIf { path -> path.isNotBlank() }
                ?.let(::File)
                ?: File(System.getProperty("user.home"), ".gemini")
            return File(dataDir, "jetski-standalone-oauth-token")
        }


        fun captureFileSnapshot(file: File): FileSnapshot {
            val existed = file.exists()
            val originalBytes = if (existed) file.readBytes() else byteArrayOf()
            return FileSnapshot(file, existed, originalBytes)
        }

        fun restoreFileSnapshot(snapshot: FileSnapshot): Boolean {
            return try {
                if (snapshot.existed) {
                    writeSensitiveBytesAtomically(snapshot.file, snapshot.originalBytes)
                    true
                } else {
                    !snapshot.file.exists() || (snapshot.file.isFile && snapshot.file.delete())
                }
            } catch (_: Exception) {
                false
            }
        }

        fun writeSensitiveTextAtomically(file: File, content: String) {
            writeSensitiveBytesAtomically(file, content.toByteArray(Charsets.UTF_8))
        }

        fun writeSensitiveBytesAtomically(file: File, content: ByteArray) {
            AtomicFileWriter.writeBytes(
                target = file,
                content = content,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
                disallowSymlinks = true
            ).getOrThrow()
        }

        fun writeJetskiStandaloneToken(account: AccountInfo): Boolean {
            return try {
                val tokenFile = resolveJetskiStandaloneTokenFile()
                val expiryIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(account.tokens.expiryTimestamp * 1000L))

                val payload = buildJsonObject {
                    putJsonObject("token") {
                        put("access_token", account.tokens.accessToken)
                        put("token_type", "Bearer")
                        put("refresh_token", account.tokens.refreshToken)
                        put("expiry", expiryIso)
                    }
                    put("auth_method", "consumer")
                }.toString()

                writeSensitiveTextAtomically(tokenFile, payload)
                true
            } catch (_: Exception) {
                false
            }
        }

    }
}
