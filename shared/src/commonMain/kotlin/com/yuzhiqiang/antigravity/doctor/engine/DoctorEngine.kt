package com.yuzhiqiang.antigravity.doctor.engine

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.model.*
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
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
                    title = "本地代理服务未运行",
                    status = DoctorCheckStatus.FAILED,
                    message = "代理服务处于停止状态，无法拦截转发请求（配置端口：$actualPort）。",
                    suggestion = "请启动本地代理服务。",
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
                        title = "本地代理服务运行正常",
                        status = DoctorCheckStatus.PASSED,
                        message = "代理已就绪并正常监听 http://127.0.0.1:$actualPort。"
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "proxy.unreachable",
                        category = DoctorCheckCategory.PROXY,
                        title = "本地代理端点无法连通",
                        status = DoctorCheckStatus.FAILED,
                        message = "无法连接 127.0.0.1:$actualPort，请检查端口占用或权限。",
                        suggestion = "尝试重启代理服务。",
                        autoFixable = true,
                        fixAction = DoctorFixAction.StartProxy
                    )
                )
            }
        }

        // =========================================================================
        // 2. 官方服务网络检测 (NETWORK)
        // =========================================================================
        val networkResult = ConnectionTester.testOfficialService()
        if (networkResult.success) {
            items.add(
                DoctorCheckItem(
                    id = "network.official_cloud_code.healthy",
                    category = DoctorCheckCategory.NETWORK,
                    title = "连接官方服务",
                    status = DoctorCheckStatus.PASSED,
                    message = "官方 Cloud Code 服务可建立网络通信（${networkResult.latencyMs}ms）。"
                )
            )
        } else {
            items.add(
                DoctorCheckItem(
                    id = "network.official_cloud_code.unreachable",
                    category = DoctorCheckCategory.NETWORK,
                    title = "连接官方服务失败",
                    status = DoctorCheckStatus.FAILED,
                    message = "无法连通 Google 官方服务：${networkResult.error ?: "HTTP ${networkResult.statusCode}"}。",
                    suggestion = "请检查网络与代理配置；如直连正常但 Studio 仍失败，请重启 Studio 后重新检测。",
                    autoFixable = true,
                    fixAction = DoctorFixAction.RetestNetwork
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
                    title = "未配置或未启用任何提供商",
                    status = DoctorCheckStatus.WARNING,
                    message = "当前没有已启用的 Provider，所有模型请求将被拦截或直接失败。",
                    suggestion = "前往「模型管理」添加 Provider。",
                    autoFixable = true,
                    fixAction = DoctorFixAction.OpenAddProvider
                )
            )
        } else {
            for (provider in enabledProviders) {
                val providerUpstreams = config.upstreamModels.filter { it.providerId == provider.id && it.enabled }
                if (providerUpstreams.isEmpty()) {
                    items.add(
                        DoctorCheckItem(
                            id = "provider.${provider.id}.no_models",
                            category = DoctorCheckCategory.PROVIDER,
                            title = "提供商「${provider.name}」未配置模型",
                            status = DoctorCheckStatus.WARNING,
                            message = "该提供商已启用，但尚未关联任何上游模型。",
                            suggestion = "请在模型管理中配置上游模型。"
                        )
                    )
                } else {
                    diagnoseProvider(provider, providerUpstreams, items)
                }
            }
        }

        // =========================================================================
        // 4. 宿主集成检测 (HOST)
        // =========================================================================
        // (1) IDE 诊断
        if (IdeHostManager.isInstalled(config.customHostPaths["ide"])) {
            val ideActive = IdeHostManager.isActive(actualPort)
            if (ideActive) {
                items.add(
                    DoctorCheckItem(
                        id = "host.ide.healthy",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity IDE 代理接入正常",
                        status = DoctorCheckStatus.PASSED,
                        message = "settings.json 已正确配置为 http://127.0.0.1:$actualPort。"
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "host.ide.official",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity IDE 使用官方模式（未接入代理）",
                        status = DoctorCheckStatus.INFO,
                        message = "当前直连 Google 官方服务，可直接正常使用。如需在 IDE 中使用自定义模型，可启用代理接入。",
                        autoFixable = true,
                        fixAction = DoctorFixAction.RepairIdeSettings
                    )
                )
            }
        }

        // (2) App 诊断
        if (AppHostManager.isInstalled(config.customHostPaths["app"])) {
            val appActive = AppHostManager.isActive(actualPort)
            val appRunning = AppHostManager.isRunning()
            if (appActive) {
                val statusMsg = if (appRunning) "（App 正在运行）" else ""
                items.add(
                    DoctorCheckItem(
                        id = "host.app.healthy",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity App 代理接入正常",
                        status = DoctorCheckStatus.PASSED,
                        message = "环境变量 CLOUD_CODE_URL 已正确配置为 http://127.0.0.1:$actualPort $statusMsg。"
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "host.app.official",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity App 使用官方模式（未接入代理）",
                        status = DoctorCheckStatus.INFO,
                        message = "当前直连 Google 官方服务。如需在 App 中使用自定义模型，可启用代理接入。",
                        autoFixable = true,
                        fixAction = DoctorFixAction.RepairAppEnvironment
                    )
                )
            }
        }

        // (3) CLI 诊断
        if (CliHostManager.isInstalled(config.customHostPaths["cli"])) {
            val cliActive = CliHostManager.isActive(actualPort)
            if (cliActive) {
                items.add(
                    DoctorCheckItem(
                        id = "host.cli.healthy",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity CLI 代理接入正常",
                        status = DoctorCheckStatus.PASSED,
                        message = "已在 CLI 配置文件中配置 cloud_code_url 为 http://127.0.0.1:$actualPort。"
                    )
                )
            } else {
                items.add(
                    DoctorCheckItem(
                        id = "host.cli.official",
                        category = DoctorCheckCategory.HOST,
                        title = "Antigravity CLI 使用官方模式（未接入代理）",
                        status = DoctorCheckStatus.INFO,
                        message = "CLI 当前处于官方直连模式。"
                    )
                )
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
        providerUpstreams: List<UpstreamModel>,
        items: MutableList<DoctorCheckItem>
    ) {
        val adapter = AdapterFactory.getAdapter(provider.protocol)
        val catalogIds = adapter.fetchModels(provider)
            .map(::normalizeModelReference)
            .toSet()
        if (catalogIds.isNotEmpty()) {
            val invalidModels = providerUpstreams
                .filter { model -> normalizeModelReference(model.upstreamModelId) !in catalogIds }
                .map(UpstreamModel::upstreamModelId)
            if (invalidModels.isNotEmpty()) {
                items += DoctorCheckItem(
                    id = "provider.${provider.id}.invalid_models",
                    category = DoctorCheckCategory.PROVIDER,
                    title = "提供商「${provider.name}」存在失效模型",
                    status = DoctorCheckStatus.FAILED,
                    message = "上游当前未提供以下模型：${invalidModels.joinToString(", ")}。",
                    suggestion = "建议清理失效模型，避免请求直接返回模型不存在错误。",
                    autoFixable = true,
                    fixAction = DoctorFixAction.PruneInvalidModels(provider.id, invalidModels)
                )
            } else {
                items += DoctorCheckItem(
                    id = "provider.${provider.id}.healthy",
                    category = DoctorCheckCategory.PROVIDER,
                    title = "提供商「${provider.name}」连通正常",
                    status = DoctorCheckStatus.PASSED,
                    message = "鉴权成功，已配置的 ${providerUpstreams.size} 个模型均存在于上游目录中。"
                )
            }
            return
        }

        val connection = ConnectionTester.testProvider(provider)
        if (connection.success) {
            items += DoctorCheckItem(
                id = "provider.${provider.id}.catalog_unavailable",
                category = DoctorCheckCategory.PROVIDER,
                title = "提供商「${provider.name}」可连接但无法验证模型目录",
                status = DoctorCheckStatus.WARNING,
                message = "已建立上游连接，但该协议或端点没有返回可解析的模型目录。",
                suggestion = "请确认 models endpoint 已配置，并手动核对模型 ID。"
            )
        } else {
            items += DoctorCheckItem(
                id = "provider.${provider.id}.unreachable",
                category = DoctorCheckCategory.PROVIDER,
                title = "提供商「${provider.name}」连通失败",
                status = DoctorCheckStatus.FAILED,
                message = "无法连接上游端点或鉴权失败：${connection.error ?: "HTTP ${connection.statusCode}"}。",
                suggestion = "请检查提供商的 API Key、网络代理或 Base URL 是否正确。"
            )
        }
    }

    private fun pruneInvalidModels(action: DoctorFixAction.PruneInvalidModels): Boolean {
        val invalidIds = action.invalidModelIds.map(::normalizeModelReference).toSet()
        configStore.updateConfig { current ->
            val removedUpstreams = current.upstreamModels.filter { model ->
                model.providerId == action.providerId &&
                        normalizeModelReference(model.upstreamModelId) in invalidIds
            }
            val removedUpstreamIds = removedUpstreams.map(UpstreamModel::id).toSet()
            val removedVirtuals = current.virtualModels.filter { virtual ->
                virtual.upstreamModelId in removedUpstreamIds
            }
            val removedVirtualIds = removedVirtuals.flatMap { virtual ->
                listOf(virtual.id, virtual.hostModelId.orEmpty())
            }.map(::normalizeModelReference).toSet()
            val removedVirtualModelIds = removedVirtuals.map { virtual -> virtual.id }.toSet()
            val removedReferences = invalidIds + removedUpstreamIds + removedVirtualIds
            current.copy(
                upstreamModels = current.upstreamModels.filterNot { it.id in removedUpstreamIds },
                virtualModels = current.virtualModels
                    .filterNot { it.id in removedVirtualModelIds }
                    .map { virtual ->
                        val fallback = virtual.fallbackVirtualModelId
                        if (fallback != null && normalizeModelReference(fallback) in removedReferences) {
                            virtual.copy(fallbackVirtualModelId = null)
                        } else {
                            virtual
                        }
                    },
                modelCompressionPolicies = current.modelCompressionPolicies.filterKeys { key ->
                    normalizeModelReference(key) !in removedReferences
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
                    AppHostManager.enable(port)
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
