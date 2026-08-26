package com.yuzhiqiang.antigravity.services.events

import com.yuzhiqiang.antigravity.logging.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 宿主进程存活与生命周期异步监听器。
 * 利用 JVM ProcessHandle.onExit() 捕获 IDE / App 进程退出事件。
 */
object HostProcessWatcher {

    private val isRunning = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val trackedProcesses = ConcurrentHashMap<Long, TrackedProcess>()

    /**
     * 启动进程监听器
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            return
        }
        generation.incrementAndGet()
    }

    /**
     * 跟踪指定进程 PID 的退出事件
     */
    fun track(label: String, pid: Long) {
        if (!isRunning.get() || pid <= 1L) {
            return
        }

        val currentGeneration = generation.get()
        val trackedProcess = TrackedProcess(label, currentGeneration)
        if (trackedProcesses.putIfAbsent(pid, trackedProcess) != null) {
            return
        }

        try {
            val handleOpt = ProcessHandle.of(pid)
            if (handleOpt.isPresent) {
                val handle = handleOpt.get()
                handle.onExit().thenAccept { exitedHandle ->
                    val exitedPid = exitedHandle.pid()
                    val isCurrentRun = isRunning.get() &&
                            generation.get() == trackedProcess.generation
                    val removed = trackedProcesses.remove(exitedPid, trackedProcess)
                    if (!isCurrentRun || !removed) {
                        return@thenAccept
                    }
                    HostEventHub.emit(
                        HostEvent.ProcessExited(
                            label = trackedProcess.label,
                            pid = exitedPid
                        )
                    )
                }
            } else {
                trackedProcesses.remove(pid, trackedProcess)
            }
        } catch (error: Exception) {
            trackedProcesses.remove(pid, trackedProcess)
            AppLog.w("Host/Process", error) { "注册宿主进程退出监听失败：pid=$pid，原因=${error.message ?: "未知错误"}" }
        }
    }

    /**
     * 批量跟踪进程列表
     */
    fun trackPids(label: String, pids: List<Long>) {
        pids.forEach { track(label, it) }
    }

    /**
     * 停止监听器
     */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        generation.incrementAndGet()
        trackedProcesses.clear()
    }

    private data class TrackedProcess(
        val label: String,
        val generation: Long
    )
}
