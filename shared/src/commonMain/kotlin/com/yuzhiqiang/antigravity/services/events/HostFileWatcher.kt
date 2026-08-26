package com.yuzhiqiang.antigravity.services.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 宿主凭据与全局状态数据库文件监听器。
 *
 * 仅监听账号探测实际使用的凭据目录和 globalStorage 目录。启动时尚不存在的
 * 目录会由低频重扫补注册，SQLite 连续写入统一采用尾沿防抖合并。
 */
object HostFileWatcher {

    private const val POLL_INTERVAL_MILLIS = 250L
    private const val DEBOUNCE_MILLIS = 250L
    private const val DIRECTORY_RESCAN_INTERVAL_MILLIS = 10_000L

    private val isRunning = AtomicBoolean(false)
    private val lifecycleLock = Any()

    @Volatile
    private var watchScope: CoroutineScope? = null

    @Volatile
    private var watchService: WatchService? = null

    private val targetFileNames = setOf(
        "jetski-standalone-oauth-token",
        "oauth_credentials.json",
        "oauth_creds.json",
        "state.vscdb",
        "state.vscdb-journal",
        "state.vscdb-wal"
    )

    /**
     * 启动文件系统监听。
     */
    fun start() {
        synchronized(lifecycleLock) {
            if (!isRunning.compareAndSet(false, true)) {
                return
            }

            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            watchScope = scope
            scope.launch {
                watchHostFiles(scope)
            }
        }
    }

    /**
     * 停止文件系统监听，并清理本轮注册的系统资源。
     */
    fun stop() {
        val resources = synchronized(lifecycleLock) {
            if (!isRunning.compareAndSet(true, false)) {
                return
            }

            val currentResources = watchService to watchScope
            watchService = null
            watchScope = null
            currentResources
        }

        closeWatchService(resources.first)
        resources.second?.cancel()
    }

    private fun watchHostFiles(scope: CoroutineScope) {
        val currentWatchService = try {
            FileSystems.getDefault().newWatchService()
        } catch (error: Exception) {
            System.err.println("创建宿主文件监听服务失败：${error.message ?: "未知错误"}")
            clearStoppedScope(scope)
            return
        }

        val shouldContinue = synchronized(lifecycleLock) {
            if (isRunning.get() && watchScope === scope) {
                watchService = currentWatchService
                true
            } else {
                false
            }
        }
        if (!shouldContinue) {
            closeWatchService(currentWatchService)
            return
        }

        val registeredKeys = mutableMapOf<Path, WatchKey>()
        val failedRegistrations = mutableSetOf<Path>()

        try {
            runWatchLoop(
                scope,
                currentWatchService,
                registeredKeys,
                failedRegistrations
            )
        } catch (error: ClosedWatchServiceException) {
            if (isRunning.get()) {
                System.err.println("宿主文件监听服务意外关闭：${error.message ?: "未知错误"}")
            }
        } catch (error: Exception) {
            if (isRunning.get()) {
                System.err.println("宿主文件监听异常：${error.message ?: "未知错误"}")
            }
        } finally {
            registeredKeys.values.forEach { key -> key.cancel() }
            registeredKeys.clear()
            closeWatchService(currentWatchService)
            clearStoppedScope(scope, currentWatchService)
        }
    }

    private fun runWatchLoop(
        scope: CoroutineScope,
        currentWatchService: WatchService,
        registeredKeys: MutableMap<Path, WatchKey>,
        failedRegistrations: MutableSet<Path>
    ) {
        var lastDirectoryScanTime = 0L
        var pendingEvent: PendingFileEvent? = null

        while (isCurrentRun(scope, currentWatchService)) {
            val now = System.currentTimeMillis()
            if (now - lastDirectoryScanTime >= DIRECTORY_RESCAN_INTERVAL_MILLIS) {
                registerAvailableDirectories(
                    currentWatchService,
                    registeredKeys,
                    failedRegistrations
                )
                lastDirectoryScanTime = now
            }

            val key = pollWatchKey(currentWatchService)
            if (key != null) {
                pendingEvent = collectLatestFileEvent(key, pendingEvent)
                resetWatchKey(key, registeredKeys)
            }
            pendingEvent = emitDebouncedEvent(pendingEvent, System.currentTimeMillis())
        }
    }

    private fun registerAvailableDirectories(
        currentWatchService: WatchService,
        registeredKeys: MutableMap<Path, WatchKey>,
        failedRegistrations: MutableSet<Path>
    ) {
        resolveWatchDirectories().forEach { directory ->
            val path = directory.toPath().toAbsolutePath().normalize()
            if (!directory.isDirectory || registeredKeys.containsKey(path)) {
                return@forEach
            }

            try {
                val key = path.register(
                    currentWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                registeredKeys[path] = key
                failedRegistrations.remove(path)
            } catch (error: Exception) {
                if (failedRegistrations.add(path)) {
                    System.err.println(
                        "注册宿主文件目录失败：path=$path，原因=${error.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    private fun pollWatchKey(currentWatchService: WatchService): WatchKey? {
        return try {
            currentWatchService.poll(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        }
    }

    private fun collectLatestFileEvent(
        key: WatchKey,
        currentPendingEvent: PendingFileEvent?
    ): PendingFileEvent? {
        val directory = key.watchable() as? Path ?: return currentPendingEvent
        val eventTime = System.currentTimeMillis()
        var latestEvent = currentPendingEvent

        key.pollEvents().forEach { event ->
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                return@forEach
            }

            val relativePath = event.context() as? Path ?: return@forEach
            val fileName = relativePath.fileName?.toString() ?: return@forEach
            if (!targetFileNames.contains(fileName)) {
                return@forEach
            }

            val fullPath = directory.resolve(relativePath).toAbsolutePath().normalize()
            latestEvent = PendingFileEvent(
                path = fullPath,
                fileName = fileName,
                lastModifiedAt = eventTime
            )
        }
        return latestEvent
    }

    private fun resetWatchKey(
        key: WatchKey,
        registeredKeys: MutableMap<Path, WatchKey>
    ) {
        if (key.reset()) {
            return
        }

        val invalidPath = registeredKeys.entries
            .firstOrNull { entry -> entry.value == key }
            ?.key
        if (invalidPath != null) {
            registeredKeys.remove(invalidPath)
        }
    }

    private fun emitDebouncedEvent(
        pendingEvent: PendingFileEvent?,
        now: Long
    ): PendingFileEvent? {
        if (pendingEvent == null || now - pendingEvent.lastModifiedAt < DEBOUNCE_MILLIS) {
            return pendingEvent
        }

        HostEventHub.emit(
            HostEvent.FileModified(
                path = pendingEvent.path.toString(),
                fileName = pendingEvent.fileName
            )
        )
        return null
    }

    private fun isCurrentRun(
        scope: CoroutineScope,
        currentWatchService: WatchService
    ): Boolean {
        return isRunning.get() &&
                scope.isActive &&
                watchScope === scope &&
                watchService === currentWatchService
    }

    private fun clearStoppedScope(
        scope: CoroutineScope,
        currentWatchService: WatchService? = null
    ) {
        synchronized(lifecycleLock) {
            if (currentWatchService != null && watchService === currentWatchService) {
                watchService = null
            }
            if (watchScope === scope) {
                watchScope = null
                isRunning.set(false)
            }
        }
    }

    private fun closeWatchService(service: WatchService?) {
        if (service == null) {
            return
        }
        try {
            service.close()
        } catch (error: Exception) {
            System.err.println("关闭宿主文件监听服务失败：${error.message ?: "未知错误"}")
        }
    }

    private fun resolveWatchDirectories(): List<File> {
        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()

        return buildList {
            add(File(userHome, ".gemini"))
            add(File(userHome, ".gemini/antigravity-ide"))
            add(File(userHome, ".config/antigravity"))
            add(File(userHome, ".antigravity"))
            val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
                ?: System.getenv("GEMINI_DATA_DIR")
            if (!customDataDir.isNullOrBlank()) {
                add(File(customDataDir))
            }

            addAll(resolvePlatformWatchDirectories(userHome, osName))
        }.distinctBy { directory -> directory.absoluteFile.normalize().path }
    }

    private fun resolvePlatformWatchDirectories(
        userHome: String,
        osName: String
    ): List<File> {
        return when {
            osName.contains("mac") -> resolveMacWatchDirectories(userHome)
            osName.contains("win") -> resolveWindowsWatchDirectories(userHome)
            else -> resolveLinuxWatchDirectories(userHome)
        }
    }

    private fun resolveMacWatchDirectories(userHome: String): List<File> {
        val applicationSupport = File(userHome, "Library/Application Support")
        return listOf(
            File(applicationSupport, "Antigravity"),
            File(applicationSupport, "Antigravity/User/globalStorage"),
            File(applicationSupport, "Antigravity App/User/globalStorage"),
            File(applicationSupport, "Antigravity IDE"),
            File(applicationSupport, "Antigravity IDE/User/globalStorage"),
            File(applicationSupport, "Antigravity-IDE"),
            File(applicationSupport, "Antigravity-IDE/User/globalStorage")
        )
    }

    private fun resolveWindowsWatchDirectories(userHome: String): List<File> {
        val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
        return listOf(
            File(appData, "Antigravity"),
            File(appData, "Antigravity/User/globalStorage"),
            File(appData, "Antigravity App/User/globalStorage"),
            File(appData, "Antigravity IDE"),
            File(appData, "Antigravity IDE/User/globalStorage"),
            File(appData, "Antigravity-IDE"),
            File(appData, "Antigravity-IDE/User/globalStorage")
        )
    }

    private fun resolveLinuxWatchDirectories(userHome: String): List<File> {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { path -> path.isNotBlank() }
            ?: "$userHome/.config"
        return listOf(
            File(configHome, "Antigravity/User/globalStorage"),
            File(configHome, "Antigravity App/User/globalStorage"),
            File(configHome, "Antigravity IDE"),
            File(configHome, "Antigravity IDE/User/globalStorage"),
            File(configHome, "Antigravity-IDE"),
            File(configHome, "Antigravity-IDE/User/globalStorage")
        )
    }

    private data class PendingFileEvent(
        val path: Path,
        val fileName: String,
        val lastModifiedAt: Long
    )
}
