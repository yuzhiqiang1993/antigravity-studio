package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/**
 * 跨进程账号切换排他锁，兼容 Cockpit 插件规范。
 * 路径：~/.gemini/antigravity/.antigravity-ide-cockpit-account-switch.lock
 */
class CrossProcessAccountSwitchLock(
    private val lockDirectory: File = File(System.getProperty("user.home"), ".gemini/antigravity"),
    private val lockTimeoutMs: Long = 120_000L,
    private val heartbeatIntervalMs: Long = 5_000L
) {
    private val inProcessMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val lockFile: File
        get() = File(lockDirectory, LOCK_FILE_NAME)

    @Serializable
    data class LockPayload(
        val pid: Long = 0L,
        val owner: String = "unknown",
        val sessionId: String = "",
        val instanceId: String? = null,
        val acquiredAt: Long = 0L,
        val lastHeartbeat: Long = 0L,
        val restartPending: Boolean = false
    ) {
        val effectiveSessionId: String
            get() = sessionId.ifEmpty { instanceId ?: "" }
        val effectiveHeartbeat: Long
            get() = if (lastHeartbeat > 0) lastHeartbeat else acquiredAt
    }

    class LockAcquisitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun <T> withCrossProcessLock(
        owner: String = "antigravity-studio",
        block: suspend () -> T
    ): T = inProcessMutex.withLock {
        val sessionId = UUID.randomUUID().toString()
        val currentPid = runCatching { ProcessHandle.current().pid() }.getOrDefault(0L)
        acquireLock(owner, sessionId, currentPid)
        val heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                updateHeartbeat(owner, sessionId, currentPid)
            }
        }
        try {
            block()
        } finally {
            heartbeatJob.cancelAndJoin()
            releaseLock(sessionId)
        }
    }

    private fun acquireLock(owner: String, sessionId: String, pid: Long) {
        lockDirectory.mkdirs()
        val file = lockFile
        val now = System.currentTimeMillis()
        val payload = LockPayload(
            pid = pid,
            owner = owner,
            sessionId = sessionId,
            instanceId = sessionId,
            acquiredAt = now,
            lastHeartbeat = now
        )
        val payloadBytes = json.encodeToString(LockPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

        var acquired = false
        var attempts = 0
        while (!acquired && attempts < 3) {
            attempts++
            try {
                Files.write(
                    file.toPath(),
                    payloadBytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                )
                runCatching {
                    Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
                }
                acquired = true
            } catch (e: Exception) {
                // 文件已存在，检查是否超时失效
                if (isLockStale(file)) {
                    AppLog.w("Auth/Lock") { "检测到过期的跨进程切号锁，正在清理..." }
                    runCatching { file.delete() }
                } else {
                    throw LockAcquisitionException("跨进程账号切换锁已被其他进程持有: ${file.absolutePath}", e)
                }
            }
        }
        if (!acquired) {
            throw LockAcquisitionException("未能获取跨进程账号切换锁")
        }
    }

    private fun isLockStale(file: File): Boolean {
        if (!file.exists()) return true
        val now = System.currentTimeMillis()
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return true
        val payload = runCatching { json.decodeFromString(LockPayload.serializer(), content) }.getOrNull()
        if (payload == null) {
            return (now - file.lastModified()) > lockTimeoutMs
        }
        val isTimeout = (now - payload.effectiveHeartbeat) > lockTimeoutMs
        if (isTimeout) return true

        // 检查进程存活（若非待重启状态且 PID > 0 且进程已死亡，则立即可自愈接管）
        if (!payload.restartPending && payload.pid > 0) {
            val isAlive = runCatching {
                ProcessHandle.of(payload.pid).map { it.isAlive }.orElse(false)
            }.getOrDefault(true)
            if (!isAlive) {
                AppLog.i("Auth/Lock") { "持锁进程 PID=${payload.pid} 已退出，判定锁为 stale" }
                return true
            }
        }
        return false
    }

    private fun updateHeartbeat(owner: String, sessionId: String, pid: Long) {
        val file = lockFile
        if (!file.exists()) return
        runCatching {
            val now = System.currentTimeMillis()
            val payload = LockPayload(
                pid = pid,
                owner = owner,
                sessionId = sessionId,
                instanceId = sessionId,
                acquiredAt = now,
                lastHeartbeat = now
            )
            val bytes = json.encodeToString(LockPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
            val tempFile = File(file.parentFile, "${file.name}.tmp-${UUID.randomUUID()}")
            Files.write(tempFile.toPath(), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            runCatching {
                Files.setPosixFilePermissions(tempFile.toPath(), PosixFilePermissions.fromString("rw-------"))
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }

    private fun releaseLock(sessionId: String) {
        val file = lockFile
        if (!file.exists()) return
        runCatching {
            val content = file.readText(Charsets.UTF_8)
            val payload = json.decodeFromString(LockPayload.serializer(), content)
            if (payload.effectiveSessionId == sessionId) {
                file.delete()
            }
        }
    }

    companion object {
        const val LOCK_FILE_NAME = ".antigravity-ide-cockpit-account-switch.lock"
    }
}
