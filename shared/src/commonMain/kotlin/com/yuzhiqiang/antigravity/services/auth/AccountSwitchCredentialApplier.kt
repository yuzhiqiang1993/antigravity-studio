package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
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
    private val googleAuthService: GoogleAuthService
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

        val sharedCredentialsSnapshot = if (request.applyToAppCli) {
            accountStore.captureOfficialCredentialsSnapshot().getOrThrow()
        } else {
            null
        }
        val jetskiTokenSnapshot = if (request.applyToAppCli) {
            captureFileSnapshot(resolveJetskiStandaloneTokenFile())
        } else {
            null
        }
        val appOauthFileSnapshot = if (request.applyToAppCli) {
            captureFileSnapshot(resolveAppOauthCredentialsFile())
        } else {
            null
        }
        return OriginalState(
            studioAccount = accountStore.currentActiveAccount(),
            ideSnapshot = ideSnapshot,
            appDbSnapshot = appDbSnapshot,
            sharedCredentialsSnapshot = sharedCredentialsSnapshot,
            jetskiTokenSnapshot = jetskiTokenSnapshot,
            appOauthFileSnapshot = appOauthFileSnapshot
        )
    }

    suspend fun applyCredentials(
        request: AccountSwitchSession.Request,
        ideWasRunning: Boolean,
        appWasRunning: Boolean,
        changes: AppliedChanges
    ): AccountInfo {
        request.progressCallback?.invoke("2/4 正在写入目标账号与 App & CLI 共享凭据...")
        log("切号开始", "目标账号: " + request.targetAccount.email + ", applyToIde=" + request.applyToIde + ", applyToAppCli=" + request.applyToAppCli)

        // 1. 若包含 App & CLI 目标且有 Refresh Token，先联网向 Google 刷新最新的 ID Token 与 Access Token (对齐 Cockpit 插件机制)
        val targetAccount = if (request.applyToAppCli && request.targetAccount.tokens.refreshToken.isNotBlank()) {
            log("联网刷新Token", "正在向 Google 刷新 " + request.targetAccount.email + " 的最新 Access/ID Token...")
            val refreshResult = googleAuthService.refreshAccessToken(request.targetAccount.tokens.refreshToken).getOrNull()
            if (refreshResult != null) {
                log("Token刷新成功", "已获取最新 ID Token (len=" + (refreshResult.idToken?.length ?: 0) + ")")
                val updated = request.targetAccount.copy(tokens = refreshResult)
                accountStore.updateTokens(
                    email = updated.email,
                    tokens = updated.tokens,
                    name = updated.profile.name,
                    avatarUrl = updated.profile.avatarUrl
                )
                updated
            } else {
                log("Token刷新失败", "使用当前缓存的 Token 继续切号")
                request.targetAccount
            }
        } else {
            request.targetAccount
        }

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

        requireStep(
            accountStore.setActiveAccount(targetAccount.id).isSuccess,
            "Studio 活跃账号更新失败"
        )
        changes.studioAccountChanged = true

        // 同步写入官方与镜像 OAuth 凭据文件
        log("凭据文件同步", "正在写入 ~/.gemini/oauth_creds.json 及镜像文件...")
        requireStep(
            accountStore.syncToOfficialCredentials(targetAccount),
            "App & CLI 共享 OAuth 凭据文件写入失败"
        )
        changes.sharedCredentialsWritten = true
        changes.appUnavailable = !AppHostManager.isInstalled(request.appInstallationPath)

        // 核心突破：注入系统级安全存储 (macOS Keychain: service=gemini, account=antigravity)
        log("Keychain注入", "正在向系统钥匙串写入 Antigravity 认证凭据...")
        val keychainResult = SystemCredentialInjector.inject(targetAccount)
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
        changes.appOauthFileWriteAttempted = true
        requireStep(
            writeAppOauthCredentials(targetAccount),
            "App 共享 OAuth 兼容投影写入失败"
        )
        changes.appOauthFileWritten = true
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

        fun resolveAppOauthCredentialsFile(): File {
            return HostAccountDetector.resolvePlatformAppCredentialsFile()
        }

        fun captureFileSnapshot(file: File): FileSnapshot {
            val existed = file.exists()
            val originalBytes = if (existed) file.readBytes() else byteArrayOf()
            return FileSnapshot(file, existed, originalBytes)
        }

        fun restoreFileSnapshot(snapshot: FileSnapshot): Boolean {
            return try {
                if (snapshot.existed) {
                    writeSensitiveTextAtomically(
                        snapshot.file,
                        snapshot.originalBytes.toString(Charsets.UTF_8)
                    )
                    true
                } else {
                    !snapshot.file.exists() || (snapshot.file.isFile && snapshot.file.delete())
                }
            } catch (_: Exception) {
                false
            }
        }

        fun writeSensitiveTextAtomically(file: File, content: String) {
            val parent = file.parentFile ?: throw IllegalStateException("凭据文件缺少父目录")
            parent.mkdirs()
            val tempFile = File.createTempFile("." + file.name + ".", ".tmp", parent)
            try {
                tempFile.writeText(content, Charsets.UTF_8)
                // POSIX 文件权限操作在 Windows 上行为不一致且可能触发安全策略拦截，仅在类 Unix 系统执行
                if (!System.getProperty("os.name", "").lowercase().contains("win")) {
                    tempFile.setReadable(false, false)
                    tempFile.setWritable(false, false)
                    tempFile.setExecutable(false, false)
                    tempFile.setReadable(true, true)
                    tempFile.setWritable(true, true)
                }
                try {
                    java.nio.file.Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
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

                tokenFile.parentFile?.mkdirs()
                writeSensitiveTextAtomically(tokenFile, payload)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun writeAppOauthCredentials(account: AccountInfo): Boolean {
            return try {
                val appCredsFile = resolveAppOauthCredentialsFile()
                if (!appCredsFile.parentFile.exists()) {
                    return true
                }
                val expiryTimestamp = account.tokens.expiryTimestamp
                val idToken = account.tokens.idToken?.takeIf { it.isNotBlank() }
                val payload = buildJsonObject {
                    put("access_token", account.tokens.accessToken)
                    put("refresh_token", account.tokens.refreshToken)
                    put("email", account.email)
                    put("name", account.profile.name ?: "")
                    put("expiry_timestamp", expiryTimestamp)
                    put("expiry_date", expiryTimestamp * 1000L)
                    put("token_type", "Bearer")
                    put("antigravity_cockpit_active_email", account.email)
                    if (idToken != null) {
                        put("id_token", idToken)
                    }
                }.toString()
                writeSensitiveTextAtomically(appCredsFile, payload)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
