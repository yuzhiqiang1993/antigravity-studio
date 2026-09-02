package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 工作流租约锁管理器。
 * 包含进程内租约锁以及跨进程/插件写入的外部 Inhibitor（switch-inhibitors 目录下的 json 文件）。
 * 当有长流式请求、自动化任务执行或外部活动租约时加锁，防止切号打断当前正在进行的会话。
 */
object WorkflowLeaseManager {

    private val idGenerator = AtomicLong(1L)
    private val activeLeases = mutableMapOf<Long, Long>() // leaseId -> acquireTimestamp
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var customInhibitorDir: File? = null

    val inhibitorDirectory: File
        get() = customInhibitorDir ?: File(System.getProperty("user.home"), ".gemini/antigravity/switch-inhibitors")

    @Serializable
    data class InhibitorPayload(
        val id: String? = null,
        val leaseId: String? = null,
        val holder: String? = null,
        val owner: String? = null,
        val reason: String? = null,
        val acquiredAt: Long? = null,
        val heartbeatAt: Long? = null,
        val expiresAt: Long? = null,
        val ttlMs: Long? = null
    ) {
        val effectiveId: String?
            get() = id ?: leaseId
        val effectiveHolder: String?
            get() = holder ?: owner
        val effectiveExpiresAt: Long?
            get() = expiresAt ?: (if (heartbeatAt != null && ttlMs != null) heartbeatAt + ttlMs else null)
    }

    suspend fun acquireLease(timeoutMs: Long = 60_000L): Long = mutex.withLock {
        val id = idGenerator.getAndIncrement()
        cleanExpiredLeases(timeoutMs)
        activeLeases[id] = System.currentTimeMillis()
        id
    }

    suspend fun releaseLease(leaseId: Long) = mutex.withLock {
        activeLeases.remove(leaseId)
    }

    suspend fun isLocked(timeoutMs: Long = 60_000L): Boolean = mutex.withLock {
        cleanExpiredLeases(timeoutMs)
        if (activeLeases.isNotEmpty()) {
            return@withLock true
        }
        hasActiveExternalInhibitors()
    }

    fun hasActiveExternalInhibitors(): Boolean {
        val dir = inhibitorDirectory
        if (!dir.exists() || !dir.isDirectory) return false
        val now = System.currentTimeMillis()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: return false

        for (file in files) {
            val fileAge = now - file.lastModified()
            try {
                val content = file.readText(Charsets.UTF_8).trim()
                if (content.isBlank()) {
                    if (fileAge > 60_000L) {
                        runCatching { file.delete() }
                    }
                    continue
                }
                val payload = json.decodeFromString<InhibitorPayload>(content)
                val expiresAt = payload.effectiveExpiresAt
                if (expiresAt != null && expiresAt > now) {
                    AppLog.i("Auth/Lease") { "检测到活动的工作流外部抑制器: ${file.name}, reason: ${payload.reason}" }
                    return true
                }
            } catch (e: Exception) {
                if (fileAge <= 60_000L) {
                    AppLog.w("Auth/Lease") { "解析外部抑制器失败，采取 fail-closed 策略锁定: ${file.name}" }
                    return true
                } else {
                    AppLog.w("Auth/Lease") { "忽略并清理陈旧的损坏抑制器文件: ${file.name}" }
                    runCatching { file.delete() }
                }
            }
        }
        return false
    }

    private fun cleanExpiredLeases(timeoutMs: Long) {
        val now = System.currentTimeMillis()
        activeLeases.entries.removeIf { (now - it.value) > timeoutMs }
    }
}
