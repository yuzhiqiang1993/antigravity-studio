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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HostLifecycleDelegate(
    private val scope: CoroutineScope,
    private val configStore: ConfigStore,
    private val proxyServer: LocalProxyServer,
    private val ideDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(
            HostType.IDE,
            isInstalled = false,
            isRunning = false,
            integrationState = ClientIntegrationState.UNAVAILABLE,
            configurationState = ClientConfigurationState.UNAVAILABLE
        )
    ),
    private val appDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(
            HostType.APP,
            isInstalled = false,
            isRunning = false,
            integrationState = ClientIntegrationState.UNAVAILABLE,
            configurationState = ClientConfigurationState.UNAVAILABLE
        )
    ),
    private val cliDetailedStatusFlow: MutableStateFlow<HostDetailedStatus> = MutableStateFlow(
        HostDetailedStatus(
            HostType.CLI,
            isInstalled = false,
            isRunning = false,
            integrationState = ClientIntegrationState.UNAVAILABLE,
            configurationState = ClientConfigurationState.UNAVAILABLE
        )
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

    private val _operatingHostKeys = MutableStateFlow<Set<String>>(emptySet())
    val operatingHostKeys: StateFlow<Set<String>> = _operatingHostKeys.asStateFlow()

    fun refreshHostStatus(actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            val hostPaths = configStore.currentConfig.customHostPaths
            val isProxyRunning = proxyServer.isRunning.value

            val ideStatus = IdeHostManager.inspect(actualPort, isProxyRunning, hostPaths["ide"])
            ideDetailedStatusFlow.value = ideStatus
            isIdeHostActiveFlow.value = ideStatus.isProxyActive
            isIdeInstalledFlow.value = ideStatus.isInstalled
            isIdeRunningFlow.value = ideStatus.isRunning

            if (ideStatus.isRunning) {
                com.yuzhiqiang.antigravity.services.events.HostProcessWatcher.trackPids(
                    "Antigravity IDE",
                    IdeHostManager.findPids(hostPaths["ide"])
                )
            }

            val appStatus = AppHostManager.inspect(actualPort, isProxyRunning, hostPaths["app"])
            appDetailedStatusFlow.value = appStatus
            isAppHostActiveFlow.value = appStatus.isProxyActive
            isAppInstalledFlow.value = appStatus.isInstalled
            isAppRunningFlow.value = appStatus.isRunning

            if (appStatus.isRunning) {
                com.yuzhiqiang.antigravity.services.events.HostProcessWatcher.trackPids(
                    "Antigravity App",
                    AppHostManager.findPids(hostPaths["app"])
                )
            }

            val cliStatus = CliHostManager.inspect(actualPort, isProxyRunning, hostPaths["cli"])
            cliDetailedStatusFlow.value = cliStatus
            isCliInstalledFlow.value = cliStatus.isInstalled
            isCliHostActiveFlow.value = cliStatus.isProxyActive
        }
    }

    private val s get() = com.yuzhiqiang.antigravity.i18n.I18nManager.strings

    fun requestToggleIdeHost(actualPort: Int) {
        val currentStatus = ideDetailedStatusFlow.value
        val isRunning = currentStatus.isRunning
        val needsUpdate = currentStatus.needsUpdate

        if (needsUpdate) {
            val endpoint = currentStatus.configuredEndpoint ?: s.commonUnknown
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostIdeUpdateConfirmTitle,
                    message = if (isRunning) {
                        s.hostIdeUpdateConfirmMessageRunning(endpoint, actualPort)
                    } else {
                        s.hostIdeUpdateConfirmMessageStopped(endpoint, actualPort)
                    },
                    confirmLabel = s.hostUpdateAction,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableIdeHostInternal(isRunning, actualPort, isUpdate = true) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice(s.hostStartProxyFirstNotice(s.hostIdeTitle), NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostIdeEnableConfirmTitle,
                    message = if (isRunning) s.hostIdeEnableConfirmMessageRunning else s.hostIdeEnableConfirmMessageStopped,
                    confirmLabel = s.hostEnable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableIdeHostInternal(isRunning, actualPort, isUpdate = false) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostIdeDisableConfirmTitle,
                    message = if (isRunning) s.hostIdeDisableConfirmMessageRunning else s.hostIdeDisableConfirmMessageStopped,
                    confirmLabel = s.hostDisable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { disableIdeHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

    fun enableIdeHostInternal(wasRunning: Boolean, actualPort: Int, isUpdate: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "ide"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["ide"]
                val isCurrentlyRunning = wasRunning || IdeHostManager.isRunning(customInstallation)
                val operationSucceeded = IdeHostManager.enable(actualPort)
                val restartSucceeded = if (isCurrentlyRunning) IdeHostManager.restart(customInstallation) else true
                val newStatus = IdeHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                ideDetailedStatusFlow.value = newStatus
                isIdeHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && newStatus.isProxyActive
                ideHostErrorFlow.value = if (succeeded) null else "host_update_failed"
                showNotice(
                    when {
                        succeeded && isCurrentlyRunning && isUpdate -> s.hostIdeUpdatedAndRestarted
                        succeeded && isCurrentlyRunning -> s.hostIdeEnabledAndRestarted
                        succeeded -> s.hostIdeEnabledPendingStart
                        operationSucceeded && !restartSucceeded -> s.hostIdeConfigUpdatedRestartFailed
                        else -> s.hostIdeEnableFailed
                    },
                    if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
                )
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "ide"
            }
        }
    }

    fun disableIdeHostInternal(wasRunning: Boolean, actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "ide"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["ide"]
                val isCurrentlyRunning = wasRunning || IdeHostManager.isRunning(customInstallation)
                val operationSucceeded = IdeHostManager.disable(actualPort)
                val restartSucceeded = if (isCurrentlyRunning) IdeHostManager.restart(customInstallation) else true
                val newStatus = IdeHostManager.inspect(actualPort, proxyServer.isRunning.value, customInstallation)
                ideDetailedStatusFlow.value = newStatus
                isIdeHostActiveFlow.value = newStatus.isProxyActive
                val succeeded = operationSucceeded && restartSucceeded && !newStatus.isProxyActive
                ideHostErrorFlow.value = if (succeeded) null else "host_update_failed"
                showNotice(
                    when {
                        succeeded && isCurrentlyRunning -> s.hostIdeRestoredAndRestarted
                        succeeded -> s.hostIdeRestored
                        operationSucceeded && !restartSucceeded -> s.hostIdeConfigUpdatedRestartFailed
                        else -> s.hostIdeDisableFailed
                    },
                    if (succeeded) NoticeKind.SUCCESS else NoticeKind.ERROR
                )
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "ide"
            }
        }
    }

    fun requestToggleAppHost(actualPort: Int) {
        val currentStatus = appDetailedStatusFlow.value
        if (!currentStatus.isInstalled) {
            showNotice(s.hostAppNotInstalled, NoticeKind.ERROR)
            return
        }
        val isRunning = currentStatus.isRunning
        val needsUpdate = currentStatus.needsUpdate

        if (needsUpdate) {
            val endpoint = currentStatus.configuredEndpoint ?: s.commonUnknown
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostAppUpdateConfirmTitle,
                    message = if (isRunning) {
                        s.hostAppUpdateConfirmMessageRunning(endpoint, actualPort)
                    } else {
                        s.hostAppUpdateConfirmMessageStopped(endpoint, actualPort)
                    },
                    confirmLabel = s.hostUpdateAction,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableAppHostInternal(isRunning, actualPort, isUpdate = true) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice(s.hostStartProxyFirstNotice(s.hostAppTitle), NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostAppEnableConfirmTitle,
                    message = if (isRunning) s.hostAppEnableConfirmMessageRunning else s.hostAppEnableConfirmMessageStopped,
                    confirmLabel = s.hostEnable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableAppHostInternal(isRunning, actualPort, isUpdate = false) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostAppDisableConfirmTitle,
                    message = if (isRunning) s.hostAppDisableConfirmMessageRunning else s.hostAppDisableConfirmMessageStopped,
                    confirmLabel = s.hostDisable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { disableAppHostInternal(isRunning, actualPort) }
                )
            )
        }
    }

    fun enableAppHostInternal(wasRunning: Boolean, actualPort: Int, isUpdate: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "app"
            try {
                val result = AppHostManager.enableDetailed(actualPort, configStore.currentConfig.customHostPaths["app"])
                showNotice(
                    if (result.isSuccess) s.hostAppEnabledPendingStart else result.exceptionOrNull()?.message ?: s.hostAppEnableFailed,
                    if (result.isSuccess) NoticeKind.SUCCESS else NoticeKind.ERROR
                )
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "app"
            }
        }
    }

    fun disableAppHostInternal(wasRunning: Boolean, actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "app"
            try {
                val success = AppHostManager.disable(configStore.currentConfig.customHostPaths["app"])
                showNotice(if (success) s.hostAppRestored else s.hostAppDisableFailed, if (success) NoticeKind.SUCCESS else NoticeKind.ERROR)
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "app"
            }
        }
    }

    fun requestToggleCliHost(actualPort: Int) {
        val currentStatus = cliDetailedStatusFlow.value
        if (!currentStatus.isInstalled) {
            showNotice(s.hostCliNotInstalled, NoticeKind.ERROR)
            return
        }
        val needsUpdate = currentStatus.needsUpdate
        if (needsUpdate) {
            val endpoint = currentStatus.configuredEndpoint ?: s.commonUnknown
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostCliUpdateConfirmTitle,
                    message = s.hostCliUpdateConfirmMessage(endpoint, actualPort),
                    confirmLabel = s.hostUpdateAction,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableCliHostInternal(actualPort) }
                )
            )
            return
        }

        val shouldBeActive = !currentStatus.isProxyActive
        if (shouldBeActive && !proxyServer.isRunning.value) {
            showNotice(s.hostStartProxyFirstNotice(s.hostCliTitle), NoticeKind.ERROR)
            return
        }
        if (shouldBeActive) {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostCliEnableConfirmTitle,
                    message = s.hostCliEnableConfirmMessage,
                    confirmLabel = s.hostEnable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { enableCliHostInternal(actualPort) }
                )
            )
        } else {
            showConfirmDialog(
                AppViewModel.ConfirmDialogState(
                    title = s.hostCliDisableConfirmTitle,
                    message = s.hostCliDisableConfirmMessage,
                    confirmLabel = s.hostDisable,
                    cancelLabel = s.commonCancel,
                    isDestructive = false,
                    onConfirm = { disableCliHostInternal(actualPort) }
                )
            )
        }
    }

    fun enableCliHostInternal(actualPort: Int) = updateCliIntegration(actualPort, enabled = true)

    fun disableCliHostInternal(actualPort: Int) = updateCliIntegration(actualPort, enabled = false)

    private fun updateCliIntegration(actualPort: Int, enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "cli"
            try {
                val customPath = configStore.currentConfig.customHostPaths["cli"]
                val success = if (enabled) CliHostManager.enable(actualPort, customPath) else CliHostManager.disable()
                showNotice(
                    if (success) {
                        if (enabled) s.hostCliEnabledNotice else s.hostCliDisabledNotice
                    } else if (enabled) s.hostCliEnableFailed else s.hostCliDisableFailed,
                    if (success) NoticeKind.SUCCESS else NoticeKind.ERROR
                )
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "cli"
            }
        }
    }

    fun requestMigrateSharedEnvironment(actualPort: Int) {
        showConfirmDialog(
            AppViewModel.ConfirmDialogState(
                title = s.hostMigrateSharedEnvironment,
                message = s.hostMigrateSharedEnvironmentConfirmMessage,
                confirmLabel = s.hostMigrateSharedEnvironment,
                cancelLabel = s.commonCancel,
                isDestructive = false,
                onConfirm = {
                    scope.launch(Dispatchers.IO) {
                        val result = com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.migrateLegacyEnvironment()
                        showNotice(
                            if (result.isSuccess) s.hostMigrateSharedEnvironmentSuccess else result.exceptionOrNull()?.message ?: s.hostCliDisableFailed,
                            if (result.isSuccess) NoticeKind.SUCCESS else NoticeKind.ERROR
                        )
                        refreshHostStatus(actualPort)
                    }
                }
            )
        )
    }

    fun copyCliLaunchCommand(actualPort: Int) {
        if (!proxyServer.isRunning.value) {
            showNotice(s.hostCliLaunchCommandRequiresProxy, NoticeKind.ERROR)
            return
        }
        scope.launch(Dispatchers.IO) {
            val command = CliHostManager.buildLaunchCommand(actualPort, configStore.currentConfig.customHostPaths["cli"])
            command.fold(
                onSuccess = { text ->
                    val copied = com.yuzhiqiang.antigravity.ui.utils.copyToClipboard(text)
                    val updated = copied && CliHostManager.enable(actualPort)
                    showNotice(if (updated) s.hostCliLaunchCommandCopied else s.hostCliEnableFailed, if (updated) NoticeKind.SUCCESS else NoticeKind.ERROR)
                    refreshHostStatus(actualPort)
                },
                onFailure = { error -> showNotice(error.message ?: s.hostCliLaunchCommandRequiresIntegration, NoticeKind.ERROR) }
            )
        }
    }

    /**
     * 强制重置指定宿主为纯净官方直连模式（无视所有历史收据与冲突设置）。
     */
    fun requestForceResetHost(hostKey: String, actualPort: Int) {
        val hostName = when (hostKey) {
            "ide" -> s.hostIdeTitle
            "app" -> s.hostAppTitle
            "cli" -> s.hostCliTitle
            else -> hostKey
        }
        showConfirmDialog(
            AppViewModel.ConfirmDialogState(
                title = if (hostKey == "ide") s.hostForceResetConfirmTitle(hostName) else s.hostLaunchResetConfirmTitle(hostName),
                message = if (hostKey == "ide") s.hostForceResetConfirmMessage(hostName) else s.hostLaunchResetConfirmMessage(hostName),
                confirmLabel = s.hostForceReset,
                cancelLabel = s.commonCancel,
                isDestructive = true,
                onConfirm = { forceResetHostInternal(hostKey, actualPort) }
            )
        )
    }

    private fun forceResetHostInternal(hostKey: String, actualPort: Int) {
        if (hostKey == "app") {
            disableAppHostInternal(appDetailedStatusFlow.value.isRunning, actualPort)
            return
        }
        if (hostKey == "cli") {
            disableCliHostInternal(actualPort)
            return
        }
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + hostKey
            try {
                when (hostKey) {
                    "ide" -> {
                        val customPath = configStore.currentConfig.customHostPaths["ide"]
                        val isRunning = IdeHostManager.isRunning(customPath)
                        IdeHostManager.forceReset()
                        if (isRunning) {
                            IdeHostManager.restart(customPath)
                        }
                        showNotice(s.hostForceResetSuccess(s.hostIdeTitle), NoticeKind.SUCCESS)
                    }

                }
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - hostKey
            }
        }
    }

    fun restartIde(actualPort: Int) {
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "ide"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["ide"]
                val ok = IdeHostManager.restart(customInstallation)
                if (ok) {
                    showNotice(s.hostRestartSuccess(s.hostIdeTitle), NoticeKind.SUCCESS)
                } else {
                    showNotice(s.hostRestartFailed(s.hostIdeTitle), NoticeKind.ERROR)
                }
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "ide"
            }
        }
    }

    fun launchIde(actualPort: Int) {
        if (IdeHostManager.isActive(actualPort) && !proxyServer.isRunning.value) {
            showNotice(s.hostLaunchProxyNotRunning(s.hostIdeTitle), NoticeKind.ERROR)
            return
        }
        scope.launch(Dispatchers.IO) {
            val ok = IdeHostManager.launch(configStore.currentConfig.customHostPaths["ide"])
            if (ok) {
                showNotice(s.hostLaunchSuccess(s.hostIdeTitle), NoticeKind.SUCCESS)
            } else {
                showNotice(s.hostLaunchFailed(s.hostIdeTitle), NoticeKind.ERROR)
            }
            refreshHostStatus(actualPort)
        }
    }

    fun restartApp(actualPort: Int) {
        if (!canLaunchApp()) return
        scope.launch(Dispatchers.IO) {
            _operatingHostKeys.value = _operatingHostKeys.value + "app"
            try {
                val customInstallation = configStore.currentConfig.customHostPaths["app"]
                val ok = AppHostManager.restart(customInstallation, actualPort)
                if (ok) {
                    showNotice(s.hostRestartSuccess(s.hostAppTitle), NoticeKind.SUCCESS)
                } else {
                    showNotice(s.hostRestartFailed(s.hostAppTitle), NoticeKind.ERROR)
                }
                refreshHostStatus(actualPort)
            } finally {
                _operatingHostKeys.value = _operatingHostKeys.value - "app"
            }
        }
    }

    private fun canLaunchApp(): Boolean {
        val configured = com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
            .configuredLaunchEndpoint(com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.EnvironmentOwner.APP)
        if (configured.isFailure) {
            showNotice(configured.exceptionOrNull()?.message ?: s.hostLaunchFailed(s.hostAppTitle), NoticeKind.ERROR)
            return false
        }
        if (configured.getOrNull() != null && !proxyServer.isRunning.value) {
            showNotice(s.hostLaunchProxyNotRunning(s.hostAppTitle), NoticeKind.ERROR)
            return false
        }
        return true
    }

    fun launchApp(actualPort: Int) {
        if (!canLaunchApp()) return
        scope.launch(Dispatchers.IO) {
            val ok = AppHostManager.launch(configStore.currentConfig.customHostPaths["app"], actualPort)
            if (ok) {
                showNotice(s.hostLaunchSuccess(s.hostAppTitle), NoticeKind.SUCCESS)
            } else {
                showNotice(s.hostLaunchFailed(s.hostAppTitle), NoticeKind.ERROR)
            }
            refreshHostStatus(actualPort)
        }
    }
}
