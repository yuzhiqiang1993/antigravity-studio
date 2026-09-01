package com.yuzhiqiang.antigravity.doctor.engine

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.model.*
import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class DoctorEngine(
    private val configStore: ConfigStore,
    private val proxyServer: LocalProxyServer
) {
    suspend fun diagnose(): DoctorReport = withContext(Dispatchers.IO) {
        val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
        val items = mutableListOf<DoctorCheckItem>()
        val config = configStore.currentConfig
        val actualPort = if (proxyServer.isRunning.value) proxyServer.actualPort.value else config.proxyPort
        val isProxyRunning = proxyServer.isRunning.value

        // =========================================================================
        // 1. 本地代理检测 (PROXY)
        // =========================================================================
        if (!isProxyRunning) {
            items.add(
                DoctorCheckItem(
                    id = "proxy.not_running",
                    category = DoctorCheckCategory.PROXY,
                    title = s.doctorCheckProxyStoppedTitle,
                    status = DoctorCheckStatus.FAILED,
                    message = s.doctorCheckProxyStoppedMsg(actualPort),
                    suggestion = s.doctorCheckProxyStoppedSugg,
                    autoFixable = true,
                    fixAction = DoctorFixAction.StartProxy
                )
            )
        } else {
            // 测试 TCP 端口连通性
            val isTcpConnectable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", actualPort), 1500)
                    true
                }
            } catch (_: Exception) {
                false
            }

            if (isTcpConnectable) {
                items.add(
                    DoctorCheckItem(
                        id = "proxy.healthy",
                        category = DoctorCheckCategory.PROXY,
                        title = s.doctorCheckProxyOkTitle,
                        status = DoctorCheckStatus.PASSED,
                        message = s.doctorCheckProxyOkMsg(actualPort)
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "proxy.unreachable",
                        category = DoctorCheckCategory.PROXY,
                        title = s.doctorCheckProxyUnreachableTitle,
                        status = DoctorCheckStatus.FAILED,
                        message = s.doctorCheckProxyUnreachableMsg(actualPort),
                        suggestion = s.doctorCheckProxyUnreachableSugg,
                        autoFixable = true,
                        fixAction = DoctorFixAction.StartProxy
                    )
                )
            }
        }

        // =========================================================================
        // 2. 官方服务与网络代理检测 (NETWORK)
        // =========================================================================
        val outboundConfig = config.outboundProxy
        val inspection = com.yuzhiqiang.antigravity.network.PlatformNetworkConfig.inspectOutboundProxy(outboundConfig)
        val networkResult = ConnectionTester.testOutboundProxy(outboundConfig)

        val modeLabel = when (outboundConfig.mode) {
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode.AUTO -> s.settingsOutboundAuto
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode.DIRECT -> s.settingsOutboundDirect
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode.SYSTEM -> s.settingsOutboundSystem
            com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode.MANUAL -> s.settingsOutboundManual
        }

        if (networkResult.success) {
            val routeDesc = when {
                networkResult.fellBackToDirect -> s.doctorCheckNetworkFallbackRouteDesc
                networkResult.direct -> s.doctorCheckNetworkDirectRouteDesc
                networkResult.endpoint != null -> "${networkResult.endpoint.protocol.name} ${networkResult.endpoint.address}"
                else -> s.doctorCheckNetworkDirectRouteDesc
            }

            if (networkResult.fellBackToDirect) {
                items.add(
                    DoctorCheckItem(
                        id = "network.official_cloud_code.fallback",
                        category = DoctorCheckCategory.NETWORK,
                        title = s.doctorCheckNetworkOkTitle,
                        status = DoctorCheckStatus.WARNING,
                        message = s.doctorCheckNetworkFallbackMsg(networkResult.latencyMs, modeLabel),
                        suggestion = s.doctorCheckNetworkFallbackSugg,
                        autoFixable = true,
                        fixAction = DoctorFixAction.OpenNetworkSettings
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "network.official_cloud_code.healthy",
                        category = DoctorCheckCategory.NETWORK,
                        title = s.doctorCheckNetworkOkTitle,
                        status = DoctorCheckStatus.PASSED,
                        message = s.doctorCheckNetworkOkWithRouteMsg(networkResult.latencyMs, modeLabel, routeDesc)
                    )
                )
            }
        } else {
            items.add(
                DoctorCheckItem(
                    id = "network.official_cloud_code.unreachable",
                    category = DoctorCheckCategory.NETWORK,
                    title = s.doctorCheckNetworkFailedTitle,
                    status = DoctorCheckStatus.FAILED,
                    message = s.doctorCheckNetworkFailedWithModeMsg(
                        networkResult.error ?: "HTTP ${networkResult.statusCode ?: 502}",
                        modeLabel
                    ),
                    suggestion = s.doctorCheckNetworkFailedSugg,
                    autoFixable = true,
                    fixAction = DoctorFixAction.OpenNetworkSettings
                )
            )
        }

        if (outboundConfig.mode == com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode.SYSTEM &&
            inspection.systemProxies.isEmpty() &&
            !outboundConfig.fallbackToDirect
        ) {
            items.add(
                DoctorCheckItem(
                    id = "network.outbound_proxy.no_system_proxy",
                    category = DoctorCheckCategory.NETWORK,
                    title = s.doctorCheckProxyConfigIssueTitle,
                    status = DoctorCheckStatus.WARNING,
                    message = s.doctorCheckProxyConfigNoSystemMsg,
                    suggestion = s.doctorCheckProxyConfigNoSystemSugg,
                    autoFixable = true,
                    fixAction = DoctorFixAction.OpenNetworkSettings
                )
            )
        }

        // =========================================================================
        // 3. 提供商与配置检测 (PROVIDER / CONFIG)
        // =========================================================================
        val enabledProviders = config.providers.filter { it.enabled }
        if (enabledProviders.isEmpty()) {
            items.add(
                DoctorCheckItem(
                    id = "config.no_enabled_providers",
                    category = DoctorCheckCategory.CONFIG,
                    title = s.doctorCheckNoProvidersTitle,
                    status = DoctorCheckStatus.WARNING,
                    message = s.doctorCheckNoProvidersMsg,
                    suggestion = s.doctorCheckNoProvidersSugg,
                    autoFixable = true,
                    fixAction = DoctorFixAction.OpenAddProvider
                )
            )
        } else {
            for (provider in enabledProviders) {
                val providerBindings = config.providerModelBindings.filter { binding ->
                    binding.providerConfigId == provider.id && binding.enabled
                }
                if (providerBindings.isEmpty()) {
                    items.add(
                        DoctorCheckItem(
                            id = "provider.${provider.id}.no_models",
                            category = DoctorCheckCategory.PROVIDER,
                            title = s.doctorCheckProviderNoModelsTitle(provider.name),
                            status = DoctorCheckStatus.WARNING,
                            message = s.doctorCheckProviderNoModelsMsg,
                            suggestion = s.doctorCheckProviderNoModelsSugg
                        )
                    )
                } else {
                    diagnoseProvider(provider, providerBindings, items, s)
                }
            }
        }

        // =========================================================================
        // 4. 宿主集成检测 (HOST)
        // =========================================================================
        // (1) IDE 诊断
        val ideStatus = IdeHostManager.inspect(
            proxyPort = actualPort,
            isProxyRunning = isProxyRunning,
            customInstallation = config.customHostPaths["ide"]
        )
        if (ideStatus.isInstalled) {
            when {
                ideStatus.needsUpdate -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.ide.mismatch",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckIdeMismatchTitle,
                            status = DoctorCheckStatus.WARNING,
                            message = s.doctorCheckIdeMismatchMsg(
                                ideStatus.configuredEndpoint ?: s.commonUnknown,
                                actualPort
                            ),
                            suggestion = s.doctorCheckIdeMismatchSugg,
                            autoFixable = true,
                            fixAction = DoctorFixAction.UpdateIdeSettings
                        )
                    )
                }

                ideStatus.isProxyActive -> {
                    val statusMsg = if (ideStatus.isRunning) s.doctorCheckIdeRunningSuffix else ""
                    items.add(
                        DoctorCheckItem(
                            id = "host.ide.healthy",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckIdeOkTitle,
                            status = DoctorCheckStatus.PASSED,
                            message = s.doctorCheckIdeOkMsg(actualPort, statusMsg)
                        )
                    )
                }

                else -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.ide.official",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckIdeOfficialTitle,
                            status = DoctorCheckStatus.INFO,
                            message = s.doctorCheckIdeOfficialMsg,
                            autoFixable = true,
                            fixAction = DoctorFixAction.RepairIdeSettings
                        )
                    )
                }
            }
        }

        // (2) App 诊断
        val appStatus = AppHostManager.inspect(
            proxyPort = actualPort,
            isProxyRunning = isProxyRunning,
            customInstallation = config.customHostPaths["app"]
        )
        if (appStatus.isInstalled) {
            when {
                appStatus.needsUpdate -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.app.mismatch",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckAppMismatchTitle,
                            status = DoctorCheckStatus.WARNING,
                            message = s.doctorCheckAppMismatchMsg(
                                appStatus.configuredEndpoint ?: s.commonUnknown,
                                actualPort
                            ),
                            suggestion = s.doctorCheckAppMismatchSugg,
                            autoFixable = true,
                            fixAction = DoctorFixAction.UpdateAppEnvironment
                        )
                    )
                }

                appStatus.isProxyActive -> {
                    val statusMsg = if (appStatus.isRunning) s.doctorCheckAppRunningSuffix else ""
                    items.add(
                        DoctorCheckItem(
                            id = "host.app.healthy",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckAppOkTitle,
                            status = DoctorCheckStatus.PASSED,
                            message = s.doctorCheckAppOkMsg(actualPort, statusMsg)
                        )
                    )
                }

                else -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.app.official",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckAppOfficialTitle,
                            status = DoctorCheckStatus.INFO,
                            message = s.doctorCheckAppOfficialMsg,
                            autoFixable = true,
                            fixAction = DoctorFixAction.RepairAppEnvironment
                        )
                    )
                }
            }
        }

        // (3) CLI 诊断
        val cliStatus = CliHostManager.inspect(
            proxyPort = actualPort,
            isProxyRunning = isProxyRunning,
            customInstallation = config.customHostPaths["cli"]
        )
        if (cliStatus.isInstalled) {
            when {
                cliStatus.needsUpdate -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.cli.mismatch",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckCliMismatchTitle,
                            status = DoctorCheckStatus.WARNING,
                            message = s.doctorCheckCliMismatchMsg(
                                cliStatus.configuredEndpoint ?: s.commonUnknown,
                                actualPort
                            ),
                            suggestion = s.doctorCheckCliMismatchSugg,
                            autoFixable = true,
                            fixAction = DoctorFixAction.UpdateCliConfig
                        )
                    )
                }

                cliStatus.isProxyActive -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.cli.healthy",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckCliOkTitle,
                            status = DoctorCheckStatus.PASSED,
                            message = s.doctorCheckCliOkMsg(actualPort)
                        )
                    )
                }

                else -> {
                    items.add(
                        DoctorCheckItem(
                            id = "host.cli.official",
                            category = DoctorCheckCategory.HOST,
                            title = s.doctorCheckCliOfficialTitle,
                            status = DoctorCheckStatus.INFO,
                            message = s.doctorCheckCliOfficialMsg
                        )
                    )
                }
            }
        }

        // 计算总体健康度
        val overallStatus = when {
            items.any { it.status == DoctorCheckStatus.FAILED } -> DoctorCheckStatus.FAILED
            items.any { it.status == DoctorCheckStatus.WARNING } -> DoctorCheckStatus.WARNING
            else -> DoctorCheckStatus.PASSED
        }

        DoctorReport(
            items = items,
            overallStatus = overallStatus
        )
    }

    private suspend fun diagnoseProvider(
        provider: Provider,
        providerBindings: List<ProviderModelBinding>,
        items: MutableList<DoctorCheckItem>,
        s: com.yuzhiqiang.antigravity.i18n.Strings
    ) {
        val adapter = AdapterFactory.getAdapter(provider.protocol)
        val catalogIds = adapter.fetchModels(provider)
            .map(::normalizeModelReference)
            .toSet()
        if (catalogIds.isNotEmpty()) {
            val invalidModels = providerBindings
                .filter { binding -> normalizeModelReference(binding.providerModelId) !in catalogIds }
                .map(ProviderModelBinding::providerModelId)
            if (invalidModels.isNotEmpty()) {
                items += DoctorCheckItem(
                    id = "provider.${provider.id}.invalid_models",
                    category = DoctorCheckCategory.PROVIDER,
                    title = s.doctorCheckProviderInvalidModelsTitle(provider.name),
                    status = DoctorCheckStatus.FAILED,
                    message = s.doctorCheckProviderInvalidModelsMsg(invalidModels.joinToString(", ")),
                    suggestion = s.doctorCheckProviderInvalidModelsSugg,
                    autoFixable = true,
                    fixAction = DoctorFixAction.PruneInvalidModels(provider.id, invalidModels)
                )
            } else {
                items += DoctorCheckItem(
                    id = "provider.${provider.id}.healthy",
                    category = DoctorCheckCategory.PROVIDER,
                    title = s.doctorCheckProviderOkTitle(provider.name),
                    status = DoctorCheckStatus.PASSED,
                    message = s.doctorCheckProviderOkMsg(providerBindings.size)
                )
            }
            return
        }

        val connection = ConnectionTester.testProvider(provider)
        if (connection.success) {
            items += DoctorCheckItem(
                id = "provider.${provider.id}.catalog_unavailable",
                category = DoctorCheckCategory.PROVIDER,
                title = s.doctorCheckProviderUnverifiedTitle(provider.name),
                status = DoctorCheckStatus.WARNING,
                message = s.doctorCheckProviderUnverifiedMsg,
                suggestion = s.doctorCheckProviderUnverifiedSugg
            )
        } else {
            items += DoctorCheckItem(
                id = "provider.${provider.id}.unreachable",
                category = DoctorCheckCategory.PROVIDER,
                title = s.doctorCheckProviderNoModelsTitle(provider.name),
                status = DoctorCheckStatus.FAILED,
                message = s.doctorCheckNetworkFailedMsg(connection.error ?: "HTTP ${connection.statusCode}"),
                suggestion = s.doctorCheckProviderNoModelsSugg
            )
        }
    }

    private fun pruneInvalidModels(action: DoctorFixAction.PruneInvalidModels): Boolean {
        val invalidIds = action.invalidModelIds.map(::normalizeModelReference).toSet()
        configStore.updateConfig { current ->
            val removedBindings = current.providerModelBindings.filter { binding ->
                binding.providerConfigId == action.providerId &&
                        normalizeModelReference(binding.providerModelId) in invalidIds
            }
            val removedBindingIds = removedBindings.map(ProviderModelBinding::bindingId).toSet()
            val removedVariantIds = current.modelRouteVariants
                .filter { variant -> variant.bindingId in removedBindingIds }
                .mapTo(mutableSetOf()) { variant -> variant.variantId }
            current.copy(
                providerModelBindings = current.providerModelBindings.filterNot { binding ->
                    binding.bindingId in removedBindingIds
                },
                modelRouteVariants = current.modelRouteVariants.filterNot { variant ->
                    variant.variantId in removedVariantIds
                },
                compressionPolicyAssignments = current.compressionPolicyAssignments.filterNot { assignment ->
                    when (assignment.targetType) {
                        CompressionPolicyTargetType.PROVIDER_MODEL_BINDING -> assignment.targetId in removedBindingIds
                        CompressionPolicyTargetType.MODEL_ROUTE_VARIANT -> assignment.targetId in removedVariantIds
                        CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL -> false
                    }
                }
            )
        }
        return true
    }

    private fun normalizeModelReference(value: String): String {
        return value.trim().removePrefix("models/")
    }

    suspend fun autoFix(action: DoctorFixAction): Boolean = withContext(Dispatchers.IO) {
        val port = if (proxyServer.isRunning.value) {
            proxyServer.actualPort.value
        } else {
            configStore.currentConfig.proxyPort
        }
        try {
            when (action) {
                is DoctorFixAction.StartProxy -> {
                    proxyServer.start(configStore.currentConfig.proxyPort).isSuccess
                }

                is DoctorFixAction.RepairIdeSettings -> {
                    IdeHostManager.enable(port)
                }

                is DoctorFixAction.RepairAppEnvironment -> {
                    val customPath = configStore.currentConfig.customHostPaths["app"]
                    val isRunning = AppHostManager.isRunning(customPath)
                    if (isRunning) {
                        AppHostManager.terminate(customPath, force = true)
                    }
                    val ok = AppHostManager.enable(port, customPath)
                    if (ok && isRunning) {
                        AppHostManager.launch(customPath, port)
                    }
                    ok
                }

                is DoctorFixAction.UpdateIdeSettings -> {
                    val ok = IdeHostManager.enable(port)
                    if (ok && IdeHostManager.isRunning()) {
                        IdeHostManager.restart(configStore.currentConfig.customHostPaths["ide"])
                    }
                    ok
                }

                is DoctorFixAction.UpdateAppEnvironment -> {
                    val customPath = configStore.currentConfig.customHostPaths["app"]
                    val isRunning = AppHostManager.isRunning(customPath)
                    if (isRunning) {
                        AppHostManager.terminate(customPath, force = true)
                    }
                    val ok = AppHostManager.enable(port, customPath)
                    if (ok && isRunning) {
                        AppHostManager.launch(customPath, port)
                    }
                    ok
                }

                is DoctorFixAction.UpdateCliConfig -> {
                    CliHostManager.enable(port)
                }

                is DoctorFixAction.ResetIdeHostToOfficial -> {
                    val ok = IdeHostManager.forceReset()
                    if (ok && IdeHostManager.isRunning()) {
                        IdeHostManager.restart(configStore.currentConfig.customHostPaths["ide"])
                    }
                    ok
                }

                is DoctorFixAction.ResetAppHostToOfficial -> {
                    val customPath = configStore.currentConfig.customHostPaths["app"]
                    val isRunning = AppHostManager.isRunning(customPath)
                    if (isRunning) {
                        AppHostManager.terminate(customPath, force = true)
                    }
                    val ok = AppHostManager.forceReset(customPath)
                    if (ok && isRunning) {
                        AppHostManager.launch(customPath, null)
                    }
                    ok
                }

                is DoctorFixAction.ResetCliHostToOfficial -> {
                    CliHostManager.forceReset()
                }

                is DoctorFixAction.RestartIdeHost -> {
                    IdeHostManager.restart(configStore.currentConfig.customHostPaths["ide"])
                }

                is DoctorFixAction.RestartAppHost -> {
                    AppHostManager.restart(configStore.currentConfig.customHostPaths["app"])
                }

                is DoctorFixAction.PruneInvalidModels -> {
                    pruneInvalidModels(action)
                }

                is DoctorFixAction.RetestNetwork -> {
                    ConnectionTester.testOfficialService().success
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
