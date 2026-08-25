package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨宿主无感热切号协调器。
 * 协调凭证原子写入、系统环境变量更新、Language Server 进程凭证热注册与快照安全回滚。
 * 区分 IDE 独立认证与 App/CLI 共享认证。
 */
class HotSwitchCoordinator(
    private val accountStore: AccountStore
) {
    private val mutex = Mutex()

    private val _ideActiveAccount = MutableStateFlow<AccountInfo?>(null)
    val ideActiveAccount: StateFlow<AccountInfo?> = _ideActiveAccount.asStateFlow()

    init {
        // 初始时 IDE 默认与 CLI/App 当前活跃账号对齐
        _ideActiveAccount.value = accountStore.currentActiveAccount()
    }

    private data class LsCandidate(
        val pid: Long,
        val port: Int,
        val csrf: String
    )

    /**
     * 执行全局无感热切号（同时更新 App/CLI 共享凭据与 IDE Language Server）
     */
    suspend fun switchAccount(
        targetAccount: AccountInfo,
        progressCallback: ((phase: String) -> Unit)? = null
    ): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val originalActive = accountStore.currentActiveAccount()
            val originalIdeActive = _ideActiveAccount.value

            try {
                // 阶段 1: 凭据写入与 App/CLI 本地状态持久化
                progressCallback?.invoke("1/3 正在更新 App/CLI 本地凭据...")
                val setTargetResult = accountStore.setActiveAccount(targetAccount.id)
                if (setTargetResult.isFailure) {
                    return@withContext Result.failure(setTargetResult.exceptionOrNull() ?: IllegalStateException("写入凭证失败"))
                }

                // 阶段 2: 发现并调用 IDE Language Server 进行凭证热注册
                progressCallback?.invoke("2/3 正在向 IDE 后台语言服务注册新凭据...")
                val lsRegistered = registerToActiveLanguageServers(targetAccount)
                _ideActiveAccount.value = targetAccount

                // 阶段 3: 预热可用模型
                progressCallback?.invoke("3/3 正在刷新模型目录...")
                OfficialCatalogProbe.clearRawOfficialCatalog()
                OfficialCatalogProbe.fetchOfficialModels()

                Result.success(Unit)
            } catch (e: Exception) {
                // 异常时尝试原子回滚
                if (originalActive != null) {
                    try {
                        accountStore.setActiveAccount(originalActive.id)
                        _ideActiveAccount.value = originalIdeActive
                        OfficialCatalogProbe.fetchOfficialModels()
                    } catch (_: Exception) {
                    }
                }
                Result.failure(IllegalStateException("热切号失败，已自动回滚至原账号: ${e.message ?: "未知异常"}", e))
            }
        }
    }

    /**
     * 仅无感切换 IDE 的当前活跃账号（不影响 App/CLI 的全局凭据）
     */
    suspend fun switchIdeOnly(
        targetAccount: AccountInfo,
        progressCallback: ((phase: String) -> Unit)? = null
    ): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                progressCallback?.invoke("1/2 正在向 IDE 注册新凭据...")
                registerToActiveLanguageServers(targetAccount)
                _ideActiveAccount.value = targetAccount

                progressCallback?.invoke("2/2 正在刷新 IDE 模型目录...")
                OfficialCatalogProbe.clearRawOfficialCatalog()
                OfficialCatalogProbe.fetchOfficialModels()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(IllegalStateException("IDE 独立切号失败: ${e.message ?: "未知异常"}", e))
            }
        }
    }


    private fun registerToActiveLanguageServers(targetAccount: AccountInfo): Boolean {
        val candidates = discoverLsCandidates()
        if (candidates.isEmpty()) {
            return true // 未启动 IDE 时忽略，凭证已写入文件，IDE 启动时会自动读取
        }

        val trustAllSsl = createInsecureSslSocketFactory()
        var anySuccess = false

        for (candidate in candidates) {
            try {
                val url = URL("https://127.0.0.1:${candidate.port}/exa.language_server_pb.LanguageServerService/RegisterGdmUser")
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    sslSocketFactory = trustAllSsl
                    hostnameVerifier = HostnameVerifier { _, _ -> true }
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Codeium-Csrf-Token", candidate.csrf)
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write("{}".toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    anySuccess = true
                }
            } catch (_: Exception) {
            }
        }

        return anySuccess
    }

    private fun discoverLsCandidates(): List<LsCandidate> {
        val candidates = mutableListOf<LsCandidate>()
        val os = System.getProperty("os.name", "").lowercase()
        try {
            if (os.contains("mac") || os.contains("linux")) {
                val process = ProcessBuilder("/bin/ps", "-axo", "pid=,command=").start()
                val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
                process.waitFor()

                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.contains("language_server")) continue
                    val separator = trimmed.indexOfFirst { it.isWhitespace() }
                    if (separator <= 0) continue
                    val pid = trimmed.substring(0, separator).trim().toLongOrNull() ?: continue
                    val command = trimmed.substring(separator).trim()

                    val csrf = extractFlagValue(command, "--csrf_token") ?: continue
                    val port = extractFlagValue(command, "--https_server_port")?.toIntOrNull()
                    if (port != null && port > 0) {
                        candidates.add(LsCandidate(pid, port, csrf))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return candidates
    }

    private fun extractFlagValue(command: String, flag: String): String? {
        val prefix = "$flag="
        val parts = command.split(Regex("\\s+"))
        for (i in parts.indices) {
            val part = parts[i]
            if (part == flag && i + 1 < parts.size) {
                return parts[i + 1].trim('"', '\'')
            }
            if (part.startsWith(prefix)) {
                return part.removePrefix(prefix).trim('"', '\'')
            }
        }
        return null
    }

    private fun createInsecureSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }
}
