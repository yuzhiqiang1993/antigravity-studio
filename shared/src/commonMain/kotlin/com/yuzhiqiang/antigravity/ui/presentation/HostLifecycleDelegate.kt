package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HostLifecycleDelegate(
    val operatingHostKeys: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
    private val scope: CoroutineScope,
    private val configStore: ConfigStore,
    private val proxyServer: LocalProxyServer,
    private val isIdeHostActiveFlow: MutableStateFlow<Boolean>,
    private val isIdeInstalledFlow: MutableStateFlow<Boolean>,
    private val isIdeRunningFlow: MutableStateFlow<Boolean>,
    private val ideHostErrorFlow: MutableStateFlow<String?>,
    private val isAppHostActiveFlow: MutableStateFlow<Boolean>,
    private val isAppInstalledFlow: MutableStateFlow<Boolean>,
    private val isAppRunningFlow: MutableStateFlow<Boolean>,
    private val isCliInstalledFlow: MutableStateFlow<Boolean>,
    private val isCliHostActiveFlow: MutableStateFlow<Boolean>,
    private val showNotice: (String, NoticeKind) -> Unit,
    private val showConfirmDialog: (AppViewModel.ConfirmDialogState) -> Unit
) {

    fun refreshHostStatus(actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            val hostPaths = configStore.currentConfig.customHostPaths
            isIdeHostActiveFlow.value = IdeHostManager.isActive(actualPort)
            isIdeInstalledFlow.value = IdeHostManager.isInstalled(hostPaths["ide"])
            isIdeRunningFlow.value = IdeHostManager.isRunning()

            isCliInstalledFlow.value = CliHostManager.isInstalled()
            isCliHostActiveFlow.value = CliHostManager.isActive(actualPort)

            isAppInstalledFlow.value = AppHostManager.isInstalled(hostPaths["app"])
            isAppHostActiveFlow.value = AppHostManager.isActive(actualPort)
            isAppRunningFlow.value = AppHostManager.isRunning()
        }
    }

    fun requestToggleIdeHost(actualPort: Int) {
        val shouldBeActive = !isIdeHostActiveFlow.value
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice("请先启动本地代理服务，再接入 Antigravity IDE", NoticeKind.ERROR)
            return
        }
        val isRunning = isIdeRunningFlow.value
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认启用代理模式",
                    message = if (isRunning) "启用代理模式后，Antigravity IDE 会注入配置的模型并自动重启使配置生效。是否继续？" else "启用代理模式将使 Antigravity IDE 在启动时连接本地代理。是否继续？",
                    confirmLabel = "启用代理",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableIdeHostInternal(isRunning, actualPort) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认停用代理接入",
                    message = if (isRunning) "将停用 Antigravity IDE 的代理接入并重启。若没有其他入口共享同一环境，将同时恢复官方配置。是否继续？" else "将停用 Antigravity IDE 的代理接入。若没有其他入口共享同一环境，将同时恢复官方配置。是否继续？",
                    confirmLabel = "恢复直连",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { disableIdeHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

    fun enableIdeHostInternal(wasRunning: Boolean, actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            operatingHostKeys.value = operatingHostKeys.value + "ide"
            try {
            val customInstallation = configStore.currentConfig.customHostPaths["ide"]
            val isCurrentlyRunning = wasRunning || IdeHostManager.isRunning()
            val operationSucceeded = IdeHostManager.enable(actualPort)
            val restartSucceeded = if (isCurrentlyRunning) IdeHostManager.restart(customInstallation) else true
            val actualState = IdeHostManager.isActive(actualPort)
            isIdeHostActiveFlow.value = actualState
            val succeeded = operationSucceeded && restartSucceeded && actualState
            ideHostErrorFlow.value = if (succeeded) null else "host_update_failed"
            showNotice(
                when {
                    succeeded && isCurrentlyRunning -> "Antigravity IDE 已启用代理模式并完成重启"
                    succeeded -> "Antigravity IDE 已启用代理模式，启动后生效"
                    operationSucceeded && !restartSucceeded -> "Antigravity IDE 配置已更新，但自动重启失败"
                    else -> "Antigravity IDE 代理接入配置失败"
                },
                if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
            )
            refreshHostStatus(actualPort)
            } finally {
                operatingHostKeys.value = operatingHostKeys.value - "ide"
            }
        }
    }

   fun disableIdeHostInternal(wasRunning: Boolean, actualPort: Int) {
       scope.launch(Dispatchers.IO) {
           operatingHostKeys.value = operatingHostKeys.value + "ide"
           try {
           val customInstallation = configStore.currentConfig.customHostPaths["ide"]
           val isCurrentlyRunning = wasRunning || IdeHostManager.isRunning()
           val operationSucceeded = IdeHostManager.disable(actualPort)
           val restartSucceeded = if (isCurrentlyRunning) IdeHostManager.restart(customInstallation) else true
           val actualState = IdeHostManager.isActive(actualPort)
           isIdeHostActiveFlow.value = actualState
           val succeeded = operationSucceeded && restartSucceeded && !actualState
           ideHostErrorFlow.value = if (succeeded) null else "host_update_failed"
           showNotice(
               when {
                   succeeded && isCurrentlyRunning -> "Antigravity IDE 已恢复官方直连并完成重启"
                   succeeded -> "Antigravity IDE 已恢复官方直连"
                   operationSucceeded && !restartSucceeded -> "Antigravity IDE 配置已更新，但自动重启失败"
                   else -> "Antigravity IDE 停用代理接入失败"
               },
               if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
           )
           refreshHostStatus(actualPort)
           } finally {
               operatingHostKeys.value = operatingHostKeys.value - "ide"
           }
       }
   }

    fun requestToggleAppHost(actualPort: Int) {
        if (!isAppInstalledFlow.value) {
            showNotice("未检测到 Antigravity App 安装", NoticeKind.ERROR)
            return
        }
        val shouldBeActive = !isAppHostActiveFlow.value
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice("请先启动本地代理服务，再接入 Antigravity App", NoticeKind.ERROR)
            return
        }
        val isRunning = isAppRunningFlow.value
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认启用代理模式",
                    message = if (isRunning) "启用代理模式后，Antigravity App 会注入配置的模型并自动重启使配置生效。是否继续？" else "启用代理模式将使 Antigravity App 在启动时连接本地代理。是否继续？",
                    confirmLabel = "启用代理",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableAppHostInternal(isRunning, actualPort) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认停用代理接入",
                    message = if (isRunning) "将停用 Antigravity App 的代理接入并重启。若没有其他入口共享同一环境，将同时恢复官方配置。是否继续？" else "将停用 Antigravity App 的代理接入。若没有其他入口共享同一环境，将同时恢复官方配置。是否继续？",
                    confirmLabel = "恢复直连",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { disableAppHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

   fun enableAppHostInternal(wasRunning: Boolean, actualPort: Int) {
       scope.launch(Dispatchers.IO) {
           operatingHostKeys.value = operatingHostKeys.value + "app"
           try {
           val customInstallation = configStore.currentConfig.customHostPaths["app"]
           val isCurrentlyRunning = wasRunning || AppHostManager.isRunning()
           val operationSucceeded = AppHostManager.enable(actualPort)
           val restartSucceeded = if (isCurrentlyRunning) AppHostManager.restart(customInstallation, actualPort) else true
           isAppHostActiveFlow.value = AppHostManager.isActive(actualPort)
           val succeeded = operationSucceeded && restartSucceeded && isAppHostActiveFlow.value
           showNotice(
               when {
                   succeeded && isCurrentlyRunning -> "Antigravity App 已启用代理模式并完成重启"
                   succeeded -> "Antigravity App 已启用代理模式，启动后生效"
                   operationSucceeded && !restartSucceeded -> "Antigravity App 配置已更新，但自动重启失败"
                   else -> "Antigravity App 代理接入配置失败"
               },
               if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
           )
           refreshHostStatus(actualPort)
           } finally {
               operatingHostKeys.value = operatingHostKeys.value - "app"
           }
       }
   }

   fun disableAppHostInternal(wasRunning: Boolean, actualPort: Int) {
       scope.launch(Dispatchers.IO) {
           operatingHostKeys.value = operatingHostKeys.value + "app"
           try {
           val customInstallation = configStore.currentConfig.customHostPaths["app"]
           val isCurrentlyRunning = wasRunning || AppHostManager.isRunning()
           val operationSucceeded = AppHostManager.disable()
           val restartSucceeded = if (isCurrentlyRunning) AppHostManager.restart(customInstallation, null) else true
           isAppHostActiveFlow.value = AppHostManager.isActive(actualPort)
           val succeeded = operationSucceeded && restartSucceeded && !isAppHostActiveFlow.value
           showNotice(
               when {
                   succeeded && isCurrentlyRunning -> "Antigravity App 已恢复官方直连并完成重启"
                   succeeded -> "Antigravity App 已恢复官方直连"
                   operationSucceeded && !restartSucceeded -> "Antigravity App 配置已更新，但自动重启失败"
                   else -> "Antigravity App 停用代理接入失败"
               },
               if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
           )
           refreshHostStatus(actualPort)
           } finally {
               operatingHostKeys.value = operatingHostKeys.value - "app"
           }
       }
   }

    fun requestToggleCliHost(actualPort: Int) {
        if (!isCliInstalledFlow.value) {
            showNotice("未检测到 agy CLI 安装", NoticeKind.ERROR)
            return
        }
        val shouldBeActive = !isCliHostActiveFlow.value
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice("请先启动本地代理服务，再接入 Antigravity CLI", NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认启用代理模式",
                    message = "启用代理模式后会在用户环境中配置 CLOUD_CODE_URL；完全退出并重新打开终端应用后生效。是否继续？",
                    confirmLabel = "启用代理",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableCliHostInternal(actualPort) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认停用代理接入",
                    message = "将停用 CLI 的代理接入。若没有其他入口共享同一环境，将同时恢复官方配置；完全退出并重新打开终端应用后生效。是否继续？",
                    confirmLabel = "恢复直连",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { disableCliHostInternal(actualPort) }
                )
            )
        }
    }

    fun enableCliHostInternal(actualPort: Int) {
        val success = CliHostManager.enable(actualPort)
        isCliHostActiveFlow.value = CliHostManager.isActive(actualPort)
        if (success) {
            showNotice("CLI 已启用代理模式；请完全退出并重新打开终端应用", NoticeKind.SUCCESS)
        } else {
            showNotice("CLI 代理接入配置失败", NoticeKind.ERROR)
        }
        refreshHostStatus(actualPort)
    }

    fun disableCliHostInternal(actualPort: Int) {
        val success = CliHostManager.disable()
        isCliHostActiveFlow.value = CliHostManager.isActive(actualPort)
        if (success) {
            showNotice("CLI 代理接入已停用；请完全退出并重新打开终端应用", NoticeKind.SUCCESS)
        } else {
            showNotice("CLI 停用代理接入失败", NoticeKind.ERROR)
        }
        refreshHostStatus(actualPort)
    }

    fun restartIde(actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            operatingHostKeys.value = operatingHostKeys.value + "ide"
            try {
            val customInstallation = configStore.currentConfig.customHostPaths["ide"]
            val ok = IdeHostManager.restart(customInstallation)
            if (ok) {
                showNotice("已重启 Antigravity IDE", NoticeKind.SUCCESS)
            } else {
                showNotice("重启 Antigravity IDE 失败", NoticeKind.ERROR)
            }
            refreshHostStatus(actualPort)
            } finally {
                operatingHostKeys.value = operatingHostKeys.value - "ide"
            }
        }
    }

    fun launchIde(actualPort: Int) {
        if (IdeHostManager.isActive(actualPort) && !proxyServer.isRunning.value) {
            showNotice("当前 IDE 已接入代理，请先启动本地代理", NoticeKind.ERROR)
            return
        }
        val ok = IdeHostManager.launch(configStore.currentConfig.customHostPaths["ide"])
        if (ok) {
            showNotice("已打开 Antigravity IDE", NoticeKind.SUCCESS)
        } else {
            showNotice("打开 Antigravity IDE 失败", NoticeKind.ERROR)
        }
        refreshHostStatus(actualPort)
    }

   fun restartApp(actualPort: Int) {
       scope.launch(Dispatchers.IO) {
           operatingHostKeys.value = operatingHostKeys.value + "app"
           try {
           val customInstallation = configStore.currentConfig.customHostPaths["app"]
           val ok = AppHostManager.restart(customInstallation, actualPort)
           if (ok) {
               showNotice("已重启 Antigravity App", NoticeKind.SUCCESS)
           } else {
               showNotice("重启 Antigravity App 失败", NoticeKind.ERROR)
           }
           refreshHostStatus(actualPort)
           } finally {
               operatingHostKeys.value = operatingHostKeys.value - "app"
           }
       }
   }

   fun launchApp(actualPort: Int) {
       if (AppHostManager.isActive(actualPort) && !proxyServer.isRunning.value) {
           showNotice("当前 App 已接入代理，请先启动本地代理", NoticeKind.ERROR)
           return
       }
       val ok = AppHostManager.launch(configStore.currentConfig.customHostPaths["app"], actualPort)
       if (ok) {
           showNotice("已打开 Antigravity App", NoticeKind.SUCCESS)
       } else {
           showNotice("打开 Antigravity App 失败", NoticeKind.ERROR)
       }
       refreshHostStatus(actualPort)
   }
}
