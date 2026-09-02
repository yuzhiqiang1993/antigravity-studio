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
    private val lockTimeoutMs: Long = 30_000L,
    private val heartbeatIntervalMs: Long = 5_000L
) {
    private val inProcessMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val lockFile: File
        get() = File(lockDirectory, LOCK_FILE_NAME)

    @Serializable
    data class LockPayload(
        val pid: Long,
        val owner: String,
        val sessionId: String,
        val acquiredAt: Long,
        val lastHeartbeat: Long
    )

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
        return if (payload != null) {
            (now - payload.lastHeartbeat) > lockTimeoutMs
        } else {
            (now - file.lastModified()) > lockTimeoutMs
        }
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
                acquiredAt = now,
                lastHeartbeat = now
            )
            val bytes = json.encodeToString(LockPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
            Files.write(file.toPath(), bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        }
    }

    private fun releaseLock(sessionId: String) {
        val file = lockFile
        if (!file.exists()) return
        runCatching {
            val content = file.readText(Charsets.UTF_8)
            val payload = json.decodeFromString(LockPayload.serializer(), content)
            if (payload.sessionId == sessionId) {
                file.delete()
            }
        }.onFailure {
            runCatching { file.delete() }
        }
    }

    companion object {
        const val LOCK_FILE_NAME = ".antigravity-ide-cockpit-account-switch.lock"
    }
}
