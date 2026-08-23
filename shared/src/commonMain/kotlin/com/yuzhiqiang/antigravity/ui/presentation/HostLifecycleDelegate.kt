package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.host.model.ClientConfigurationState
import com.yuzhiqiang.antigravity.host.model.ClientIntegrationState
import com.yuzhiqiang.antigravity.host.model.HostDetailedStatus
import com.yuzhiqiang.antigravity.host.model.HostType
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
    private val ideDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(HostType.IDE, isInstalled = false, isRunning = false, integrationState = ClientIntegrationState.UNAVAILABLE, configurationState = ClientConfigurationState.UNAVAILABLE)
    ),
    private val appDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(HostType.APP, isInstalled = false, isRunning = false, integrationState = ClientIntegrationState.UNAVAILABLE, configurationState = ClientConfigurationState.UNAVAILABLE)
    ),
    private val cliDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(HostType.CLI, isInstalled = false, isRunning = false, integrationState = ClientIntegrationState.UNAVAILABLE, configurationState = ClientConfigurationState.UNAVAILABLE)
    ),
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
            val isProxyRunning = proxyServer.isRunning.value

            val ideStatus = IdeHostManager.inspect(actualPort, isProxyRunning, hostPaths["ide"])
            ideDetailedStatusFlow.value = ideStatus
            isIdeHostActiveFlow.value = ideStatus.isProxyActive
            isIdeInstalledFlow.value = ideStatus.isInstalled
            isIdeRunningFlow.value = ideStatus.isRunning

            val appStatus = AppHostManager.inspect(actualPort, isProxyRunning, hostPaths["app"])
            appDetailedStatusFlow.value = appStatus
            isAppHostActiveFlow.value = appStatus.isProxyActive
            isAppInstalledFlow.value = appStatus.isInstalled
            isAppRunningFlow.value = appStatus.isRunning

            val cliStatus = CliHostManager.inspect(actualPort, isProxyRunning, hostPaths["cli"])
            cliDetailedStatusFlow.value = cliStatus
            isCliInstalledFlow.value = cliStatus.isInstalled
            isCliHostActiveFlow.value = cliStatus.isProxyActive
        }
    }

    fun requestToggleIdeHost(actualPort: Int) {
        val currentStatus = ideDetailedStatusFlow.value
        val isRunning = currentStatus.isRunning
        val needsUpdate = currentStatus.needsUpdate

        if (needsUpdate) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "更新 Antigravity IDE 代理配置",
                    message = if (isRunning) {
                        "检测到 IDE 当前代理配置（${currentStatus.configuredEndpoint ?: "未知"}）与本地代理端口（$actualPort）不匹配。更新后将自动重启 IDE 使配置生效。是否继续？"
                    } else {
                        "检测到 IDE 当前代理配置（${currentStatus.configuredEndpoint ?: "未知"}）与本地代理端口（$actualPort）不匹配。是否更新为当前代理端口？"
                    },
                    confirmLabel = "更新配置",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableIdeHostInternal(isRunning, actualPort, isUpdate = true) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice("请先启动本地代理服务，再接入 Antigravity IDE", NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认启用代理模式",
                    message = if (isRunning) "启用代理模式后，Antigravity IDE 会注入配置的模型并自动重启使配置生效。是否继续？" else "启用代理模式将使 Antigravity IDE 在启动时连接本地代理。是否继续？",
                    confirmLabel = "启用代理",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableIdeHostInternal(isRunning, actualPort, isUpdate = false) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认停用代理接入",
                    message = if (isRunning) "将停用 Antigravity IDE 的代理接入并重启恢复官方直连模式。是否继续？" else "将停用 Antigravity IDE 的代理接入，恢复官方直连模式。是否继续？",
                    confirmLabel = "恢复直连",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { disableIdeHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

    fun enableIdeHostInternal(wasRunning: Boolean, actualPort: Int, isUpdate: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            operatingHostKeys.value = operatingHostKeys.value + "ide"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["ide"]
                val isCurrentlyRunning = wasRunning || IdeHostManager.isRunning()
                val operationSucceeded = IdeHostManager.enable(actualPort)
                val restartSucceeded = if (isCurrentlyRunning) IdeHostManager.restart(customInstallation) else true
                val newStatus = IdeHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                ideDetailedStatusFlow.value = newStatus
                isIdeHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && newStatus.isProxyActive
                ideHostErrorFlow.value = if (succeeded) null else "host_update_failed"
                showNotice(
                    when {
                        succeeded && isCurrentlyRunning && isUpdate -> "Antigravity IDE 代理配置已更新并完成重启"
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
                val newStatus = IdeHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                ideDetailedStatusFlow.value = newStatus
                isIdeHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && !newStatus.isProxyActive
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
        val currentStatus = appDetailedStatusFlow.value
        if (!currentStatus.isInstalled) {
            showNotice("未检测到 Antigravity App 安装", NoticeKind.ERROR)
            return
        }
        val isRunning = currentStatus.isRunning
        val needsUpdate = currentStatus.needsUpdate

        if (needsUpdate) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "更新 Antigravity App 代理配置",
                    message = if (isRunning) {
                        "检测到 App 当前代理环境变量（${currentStatus.configuredEndpoint ?: "未知"}）与本地代理端口（$actualPort）不匹配。更新后将自动重启 App 使配置生效。是否继续？"
                    } else {
                        "检测到 App 当前代理环境变量（${currentStatus.configuredEndpoint ?: "未知"}）与本地代理端口（$actualPort）不匹配。是否更新为当前代理端口？"
                    },
                    confirmLabel = "更新配置",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableAppHostInternal(isRunning, actualPort, isUpdate = true) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice("请先启动本地代理服务，再接入 Antigravity App", NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认启用代理模式",
                    message = if (isRunning) "启用代理模式后，Antigravity App 会注入配置的模型并自动重启使配置生效。是否继续？" else "启用代理模式将使 Antigravity App 在启动时连接本地代理。是否继续？",
                    confirmLabel = "启用代理",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableAppHostInternal(isRunning, actualPort, isUpdate = false) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "确认停用代理接入",
                    message = if (isRunning) "将停用 Antigravity App 的代理接入并重启恢复官方直连模式。是否继续？" else "将停用 Antigravity App 的代理接入，恢复官方直连模式。是否继续？",
                    confirmLabel = "恢复直连",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { disableAppHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

    fun enableAppHostInternal(wasRunning: Boolean, actualPort: Int, isUpdate: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            operatingHostKeys.value = operatingHostKeys.value + "app"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["app"]
                val isCurrentlyRunning = wasRunning || AppHostManager.isRunning()
                val operationSucceeded = AppHostManager.enable(actualPort)
                val restartSucceeded = if (isCurrentlyRunning) AppHostManager.restart(customInstallation, actualPort) else true
                val newStatus = AppHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                appDetailedStatusFlow.value = newStatus
                isAppHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && newStatus.isProxyActive
                showNotice(
                    when {
                        succeeded && isCurrentlyRunning && isUpdate -> "Antigravity App 代理配置已更新并完成重启"
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
                val newStatus = AppHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                appDetailedStatusFlow.value = newStatus
                isAppHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && !newStatus.isProxyActive
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
        val currentStatus = cliDetailedStatusFlow.value
        if (!currentStatus.isInstalled) {
            showNotice("未检测到 agy CLI 安装", NoticeKind.ERROR)
            return
        }
        val needsUpdate = currentStatus.needsUpdate
        if (needsUpdate) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = "更新 Antigravity CLI 代理配置",
                    message = "检测到 CLI 当前代理配置（${currentStatus.configuredEndpoint ?: "未知"}）与本地代理端口（$actualPort）不匹配。更新后请完全退出并重新打开终端应用生效。是否继续？",
                    confirmLabel = "更新配置",
                    cancelLabel = "取消",
                    isDestructive = false,
                    onConfirm = { enableCliHostInternal(actualPort) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
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
                    message = "将停用 CLI 的代理接入并恢复官方直连模式；完全退出并重新打开终端应用后生效。是否继续？",
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

    /**
     * 强制重置指定宿主为纯净官方直连模式（无视所有历史收据与冲突设置）。
     */
    fun requestForceResetHost(hostKey: String, actualPort: Int) {
        val hostName = when (hostKey) {
            "ide" -> "Antigravity IDE"
            "app" -> "Antigravity App"
            "cli" -> "Antigravity CLI"
            else -> hostKey
        }
        showConfirmDialog(
            AppViewModel.ConfirmDialogState(
                title = "强制重置 $hostName 为官方模式",
                message = "此操作将强制清除 $hostName 的所有代理配置、环境变量与托管记录，恢复为最干净的官方直连模式。若应用正在运行将自动重启生效。是否确认重置？",
                confirmLabel = "强制重置",
                cancelLabel = "取消",
                isDestructive = true,
                onConfirm = { forceResetHostInternal(hostKey, actualPort) }
            )
        )
    }

    private fun forceResetHostInternal(hostKey: String, actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            operatingHostKeys.value = operatingHostKeys.value + hostKey
            try {
                when (hostKey) {
                    "ide" -> {
                        val isRunning = IdeHostManager.isRunning()
                        val customPath = configStore.currentConfig.customHostPaths["ide"]
                        IdeHostManager.forceReset()
                        if (isRunning) {
                            IdeHostManager.restart(customPath)
                        }
                        showNotice("Antigravity IDE 已强制重置为官方直连模式", NoticeKind.SUCCESS)
                    }
                    "app" -> {
                        val isRunning = AppHostManager.isRunning()
                        val customPath = configStore.currentConfig.customHostPaths["app"]
                        AppHostManager.forceReset()
                        if (isRunning) {
                            AppHostManager.restart(customPath, null)
                        }
                        showNotice("Antigravity App 已强制重置为官方直连模式", NoticeKind.SUCCESS)
                    }
                    "cli" -> {
                        CliHostManager.forceReset()
                        showNotice("Antigravity CLI 已强制重置为官方直连模式", NoticeKind.SUCCESS)
                    }
                }
                refreshHostStatus(actualPort)
            } finally {
                operatingHostKeys.value = operatingHostKeys.value - hostKey
            }
        }
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

