package com.yuzhiqiang.antigravity.services.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 工作流租约锁管理器。
 * 当有长流式请求或关键任务执行时加锁，防止自动智能切号打断当前正在进行的会话。
 */
object WorkflowLeaseManager {

    private val idGenerator = AtomicLong(1L)
    private val activeLeases = mutableMapOf<Long, Long>() // leaseId -> acquireTimestamp
    private val mutex = Mutex()

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
        activeLeases.isNotEmpty()
    }

    private fun cleanExpiredLeases(timeoutMs: Long) {
        val now = System.currentTimeMillis()
        activeLeases.entries.removeIf { (now - it.value) > timeoutMs }
    }
}
