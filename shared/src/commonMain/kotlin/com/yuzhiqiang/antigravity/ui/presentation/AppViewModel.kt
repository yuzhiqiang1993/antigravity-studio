package com.yuzhiqiang.antigravity.ui.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuzhiqiang.antigravity.data.presets.PresetProviderTemplate
import com.yuzhiqiang.antigravity.data.presets.ProviderPresets
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorReport
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.host.macos.MacHostManager
import com.yuzhiqiang.antigravity.host.windows.WindowsHostManager
import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.i18n.I18nManager
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.adapters.AdapterFactory
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.components.NoticeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavTab {
    OVERVIEW,
    MODELS,
    ACTIVITY,
    SETTINGS
}

class AppViewModel(
    val configStore: ConfigStore = ConfigStore()
) : ViewModel() {

    val proxyServer = LocalProxyServer(configStore)
    val doctorEngine = DoctorEngine(configStore, proxyServer)

    val config: StateFlow<AppConfig> = configStore.configState
    val configLoadError: StateFlow<String?> = configStore.loadError
    val isProxyRunning: StateFlow<Boolean> = proxyServer.isRunning
    val actualProxyPort: StateFlow<Int> = proxyServer.actualPort
    val activityLogs: StateFlow<List<ActivityLog>> = ActivityRecorder.logs

    private val _currentTab = MutableStateFlow(NavTab.OVERVIEW)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _openProviderEditorRequest = MutableStateFlow(false)
    val openProviderEditorRequest: StateFlow<Boolean> = _openProviderEditorRequest.asStateFlow()

    // IDE Host 状态
    private val _isIdeHostActive = MutableStateFlow(false)
    val isIdeHostActive: StateFlow<Boolean> = _isIdeHostActive.asStateFlow()

    private val _isIdeRunning = MutableStateFlow(false)
    val isIdeRunning: StateFlow<Boolean> = _isIdeRunning.asStateFlow()

    private val _isIdeInstalled = MutableStateFlow(false)
    val isIdeInstalled: StateFlow<Boolean> = _isIdeInstalled.asStateFlow()

    private val _ideHostError = MutableStateFlow<String?>(null)
    val ideHostError: StateFlow<String?> = _ideHostError.asStateFlow()

    private val _doctorReport = MutableStateFlow<DoctorReport?>(null)
    val doctorReport: StateFlow<DoctorReport?> = _doctorReport.asStateFlow()

    private val _isDoctorRunning = MutableStateFlow(false)
    val isDoctorRunning: StateFlow<Boolean> = _isDoctorRunning.asStateFlow()

    private val _showDoctorDialog = MutableStateFlow(false)
    val showDoctorDialog: StateFlow<Boolean> = _showDoctorDialog.asStateFlow()

    // Toast 通知状态
    private val _notice = MutableStateFlow<NoticeState?>(null)
    val notice: StateFlow<NoticeState?> = _notice.asStateFlow()

    // 确认对话框状态
    data class ConfirmDialogState(
        val title: String,
        val message: String,
        val isDestructive: Boolean = false,
        val onConfirm: () -> Unit
    )

    private val _confirmDialog = MutableStateFlow<ConfirmDialogState?>(null)
    val confirmDialog: StateFlow<ConfirmDialogState?> = _confirmDialog.asStateFlow()

    // CLI Host 状态
    private val _isCliHostActive = MutableStateFlow(false)
    val isCliHostActive: StateFlow<Boolean> = _isCliHostActive.asStateFlow()

    private val _isCliInstalled = MutableStateFlow(false)
    val isCliInstalled: StateFlow<Boolean> = _isCliInstalled.asStateFlow()

    // App Host 状态
    private val _isAppHostActive = MutableStateFlow(false)
    val isAppHostActive: StateFlow<Boolean> = _isAppHostActive.asStateFlow()

    private val _isAppRunning = MutableStateFlow(false)
    val isAppRunning: StateFlow<Boolean> = _isAppRunning.asStateFlow()

    private val _isAppInstalled = MutableStateFlow(false)
    val isAppInstalled: StateFlow<Boolean> = _isAppInstalled.asStateFlow()

    // 连接测试状态
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTester.TestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTester.TestResult?> = _connectionTestResult.asStateFlow()

    // 官方原生模型动态列表
    private val _officialModels = MutableStateFlow<List<OfficialCatalogModel>>(emptyList())
    val officialModels: StateFlow<List<OfficialCatalogModel>> = _officialModels.asStateFlow()

    private val _isFetchingOfficialModels = MutableStateFlow(false)
    val isFetchingOfficialModels: StateFlow<Boolean> = _isFetchingOfficialModels.asStateFlow()

    private val _officialModelsError = MutableStateFlow<String?>(null)
    val officialModelsError: StateFlow<String?> = _officialModelsError.asStateFlow()

    init {
        val initialLang = AppLanguage.fromCode(configStore.currentConfig.language)
        I18nManager.currentLanguage = initialLang

        // 启动时自动运行本地代理并异步检测 Host 状态与拉取官方模型
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            proxyServer.start(configStore.currentConfig.proxyPort)
            refreshHostStatus()
            fetchOfficialModels().join()
        }
    }

    fun fetchOfficialModels(): Job {
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFetchingOfficialModels.value = true
            _officialModelsError.value = null
            try {
                val currentConfig = configStore.currentConfig
                val excludedCustomIds = buildSet {
                    currentConfig.upstreamModels.forEach {
                        add(it.id)
                        add(it.upstreamModelId)
                        it.displayName?.let { name -> if (name.isNotBlank()) add(name) }
                    }
                    currentConfig.virtualModels.forEach {
                        add(it.id)
                        add(it.upstreamModelId)
                        it.displayName?.let { name -> if (name.isNotBlank()) add(name) }
                    }
                }
                val result = com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe.fetchOfficialModels(
                    excludedModelIds = excludedCustomIds
                )
                result.fold(
                    onSuccess = { models -> _officialModels.value = models },
                    onFailure = { error ->
                        _officialModelsError.value = error.message ?: "官方模型同步失败"
                    }
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _officialModelsError.value = error.message ?: "官方模型同步失败"
            } finally {
                _isFetchingOfficialModels.value = false
            }
        }
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun requestOpenProviderEditor() {
        _openProviderEditorRequest.value = true
    }

    fun consumeOpenProviderEditorRequest() {
        _openProviderEditorRequest.value = false
    }

    fun openDoctorDialog() {
        _showDoctorDialog.value = true
        runDoctor()
    }

    fun closeDoctorDialog() {
        _showDoctorDialog.value = false
    }

    fun refreshHostStatus() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val port = actualProxyPort.value
            _isIdeHostActive.value = IdeHostManager.isActive(port)
            _isIdeInstalled.value = IdeHostManager.isInstalled()
            _isIdeRunning.value = IdeHostManager.isRunning()

            _isCliInstalled.value = CliHostManager.isInstalled()
            _isCliHostActive.value = CliHostManager.isActive(port)

            _isAppInstalled.value = AppHostManager.isInstalled()
            _isAppHostActive.value = AppHostManager.isActive(port)
            _isAppRunning.value = AppHostManager.isRunning()
        }
    }

    // ---- Toast 通知 ----
    fun showNotice(message: String, kind: NoticeKind = NoticeKind.SUCCESS) {
        _notice.value = NoticeState(message = message, kind = kind)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ---- 确认对话框 ----
    fun showConfirmDialog(state: ConfirmDialogState) {
        _confirmDialog.value = state
    }

    fun dismissConfirmDialog() {
        _confirmDialog.value = null
    }

    fun startProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = proxyServer.start(configStore.currentConfig.proxyPort)
            refreshHostStatus()
            if (result.isSuccess) {
                showNotice("本地代理已启动 (${result.getOrThrow()})", NoticeKind.SUCCESS)
            } else {
                showNotice(
                    "本地代理启动失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                    NoticeKind.ERROR
                )
            }
        }
    }

    fun stopProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            proxyServer.stop()
            refreshHostStatus()
            showNotice("本地代理已停止", NoticeKind.SUCCESS)
        }
    }

    fun restartProxy() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = restartProxyInternal()
            refreshHostStatus()
            if (result.isSuccess) {
                showNotice("本地代理已重启 (${result.getOrThrow()})", NoticeKind.SUCCESS)
            } else {
                showNotice(
                    "本地代理重启失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                    NoticeKind.ERROR
                )
            }
        }
    }

    private fun restartProxyInternal(): Result<Int> {
        proxyServer.stop()
        return proxyServer.start(configStore.currentConfig.proxyPort)
    }

    fun toggleIdeHost() {
        val port = actualProxyPort.value
        val shouldBeActive = !_isIdeHostActive.value
        val operationSucceeded = if (!shouldBeActive) {
            IdeHostManager.disable(port)
        } else {
            IdeHostManager.enable(port)
        }
        val actualState = IdeHostManager.isActive(port)
        _isIdeHostActive.value = actualState
        _ideHostError.value = if (operationSucceeded && actualState == shouldBeActive) {
            null
        } else {
            "host_update_failed"
        }
        if (operationSucceeded && actualState == shouldBeActive) {
            showNotice(
                if (shouldBeActive) "IDE Host 已启用代理接入" else "IDE Host 已恢复官方直连",
                NoticeKind.SUCCESS
            )
        } else {
            showNotice("IDE Host 配置更新失败", NoticeKind.ERROR)
        }
    }

    // ---- CLI Host 管理 ----
    fun toggleCliHost() {
        if (!_isCliInstalled.value) {
            showNotice("未检测到 agy CLI 安装", NoticeKind.ERROR)
            return
        }
        val port = actualProxyPort.value
        val shouldBeActive = !_isCliHostActive.value
        val success = if (shouldBeActive) {
            CliHostManager.enable(port)
        } else {
            CliHostManager.disable()
        }
        _isCliHostActive.value = CliHostManager.isActive(port)
        if (success) {
            showNotice(
                if (shouldBeActive) "CLI Host 已启用代理接入" else "CLI Host 已恢复官方直连",
                NoticeKind.SUCCESS
            )
        } else {
            showNotice("CLI Host 配置更新失败", NoticeKind.ERROR)
        }
    }

    // ---- App Host 管理 ----
    fun toggleAppHost() {
        if (!_isAppInstalled.value) {
            showNotice("未检测到 Antigravity App 安装", NoticeKind.ERROR)
            return
        }
        val port = actualProxyPort.value
        val shouldBeActive = !_isAppHostActive.value
        val success = if (shouldBeActive) {
            AppHostManager.enable(port)
        } else {
            AppHostManager.disable()
        }
        _isAppHostActive.value = AppHostManager.isActive(port)
        if (success) {
            showNotice(
                if (shouldBeActive) "App Host 已启用代理接入" else "App Host 已恢复官方直连",
                NoticeKind.SUCCESS
            )
        } else {
            showNotice("App Host 配置更新失败", NoticeKind.ERROR)
        }
    }

    fun launchIde() {
        val ok = IdeHostManager.launch()
        if (ok) {
            showNotice("已唤起 Antigravity IDE", NoticeKind.SUCCESS)
        } else {
            showNotice("启动 Antigravity IDE 失败", NoticeKind.ERROR)
        }
    }

    fun launchApp() {
        val ok = AppHostManager.launch()
        if (ok) {
            showNotice("已唤起 Antigravity App", NoticeKind.SUCCESS)
        } else {
            showNotice("启动 Antigravity App 失败", NoticeKind.ERROR)
        }
    }

    // ---- 连接测试 ----
    fun testProxyConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = ConnectionTester.testProxy(actualProxyPort.value)
            _connectionTestResult.value = result
            _isTestingConnection.value = false
            if (result.success) {
                showNotice("代理连接正常 (${result.latencyMs}ms)", NoticeKind.SUCCESS)
            } else {
                showNotice("代理连接失败: ${result.error ?: "HTTP ${result.statusCode}"}", NoticeKind.ERROR)
            }
        }
    }

    fun toggleOfficialModel(modelId: String) {
        configStore.updateConfig { current ->
            val list = current.disabledOfficialModels.toMutableList()
            if (list.contains(modelId)) {
                list.remove(modelId)
            } else {
                list.add(modelId)
            }
            current.copy(disabledOfficialModels = list)
        }
    }

    fun toggleCustomModel(modelId: String) {
        configStore.updateConfig { current ->
            val targetModel = current.upstreamModels.find { it.id == modelId }
            val newEnabled = targetModel?.let { !it.enabled } ?: true
            val updatedUpstream = current.upstreamModels.map { model ->
                if (model.id == modelId) {
                    model.copy(enabled = newEnabled)
                } else {
                    model
                }
            }
            val updatedVirtual = current.virtualModels.map { vModel ->
                if (vModel.upstreamModelId == modelId) {
                    vModel.copy(enabled = newEnabled)
                } else {
                    vModel
                }
            }
            current.copy(
                upstreamModels = updatedUpstream,
                virtualModels = updatedVirtual
            )
        }
    }

    fun deleteProvider(providerId: String): Boolean {
        try {
            configStore.updateConfig { current ->
                val removedUpstreams = current.upstreamModels.filter { it.providerId == providerId }
                current.ensureFallbackReferencesAreSafe(removedUpstreams)
                current
                    .removeUpstreamReferences(removedUpstreams)
                    .copy(providers = current.providers.filterNot { it.id == providerId })
            }
            showNotice("服务商及其模型已删除", NoticeKind.SUCCESS)
            return true
        } catch (error: Exception) {
            showNotice("服务商删除失败：${error.message ?: "存在未处理的 fallback 引用"}", NoticeKind.ERROR)
            return false
        }
    }

    private fun normalizeModelReference(value: String): String {
        return value.trim().removePrefix("models/")
    }

    fun saveProvider(
        provider: Provider,
        selectedModels: List<UpstreamModel>
    ): Boolean {
        try {
            configStore.updateConfig { current ->
                val currentProviderModels = current.upstreamModels.filter { model ->
                    model.providerId == provider.id
                }
                val selectedUpstreamIds = selectedModels.map { model -> model.upstreamModelId }.toSet()
                val removedModels = currentProviderModels.filter { model ->
                    model.upstreamModelId !in selectedUpstreamIds
                }
                val cleaned = current.removeUpstreamReferences(removedModels)
                val synchronized = ProviderModelSynchronizer.synchronize(
                    config = cleaned,
                    provider = provider,
                    selectedModels = selectedModels
                ).getOrThrow()
                val removedVirtualModels = current.virtualModels.filter { previous ->
                    synchronized.virtualModels.none { updated -> updated.id == previous.id }
                }
                current.ensureFallbackReferencesAreSafe(
                    removedUpstreams = removedModels,
                    removedVirtualModels = removedVirtualModels
                )
                cleaned.removeVirtualReferences(removedVirtualModels).copy(
                    providers = cleaned.providers.filterNot { item -> item.id == provider.id } + provider,
                    upstreamModels = synchronized.upstreamModels,
                    virtualModels = synchronized.virtualModels
                )
            }
            showNotice("服务商配置已保存", NoticeKind.SUCCESS)
            return true
        } catch (error: Exception) {
            showNotice(
                "服务商保存失败：${error.message ?: "无法分配稳定模型 ID"}",
                NoticeKind.ERROR
            )
            return false
        }
    }

    private fun AppConfig.ensureFallbackReferencesAreSafe(
        removedUpstreams: List<UpstreamModel>,
        removedVirtualModels: List<VirtualModel> = emptyList()
    ) {
        val blockers = fallbackBlockers(removedUpstreams, removedVirtualModels)
        if (blockers.isNotEmpty()) {
            throw IllegalStateException(
                "无法删除模型：${blockers.joinToString("；")}仍引用待删除入口作为 fallback，请先调整 fallback"
            )
        }
    }

    private fun AppConfig.fallbackBlockers(
        removedUpstreams: List<UpstreamModel>,
        removedVirtualModels: List<VirtualModel> = emptyList()
    ): List<String> {
        if (removedUpstreams.isEmpty() && removedVirtualModels.isEmpty()) return emptyList()
        val removedUpstreamRefs = removedUpstreams
            .flatMap { upstream -> listOfNotNull(upstream.id, upstream.upstreamModelId, upstream.hostModelId) }
            .map(::normalizeModelReference)
            .toSet()
        val upstreamVirtualModels = virtualModels.filter { virtual ->
            normalizeModelReference(virtual.upstreamModelId) in removedUpstreamRefs
        }
        val allRemovedVirtualModels =
            (upstreamVirtualModels + removedVirtualModels).distinctBy { virtual -> virtual.id }
        val removedVirtualIds = allRemovedVirtualModels.map { virtual -> virtual.id }.toSet()
        val removedVirtualRefs = allRemovedVirtualModels
            .flatMap { virtual -> RouteResolver.acceptedIds(virtual) + virtual.id }
            .map(::normalizeModelReference)
            .toSet()
        return virtualModels
            .filterNot { virtual -> virtual.id in removedVirtualIds }
            .mapNotNull { virtual ->
                val fallback = virtual.fallbackVirtualModelId?.let(::normalizeModelReference)
                if (fallback != null && fallback in removedVirtualRefs) {
                    "${virtual.displayName ?: virtual.name.ifBlank { virtual.id }}"
                } else {
                    null
                }
            }
    }

    private fun AppConfig.removeVirtualReferences(removedVirtualModels: List<VirtualModel>): AppConfig {
        if (removedVirtualModels.isEmpty()) return this
        val removedVirtualRefs = removedVirtualModels
            .flatMap { virtual -> RouteResolver.acceptedIds(virtual) + virtual.id }
            .map(::normalizeModelReference)
            .toSet()
        return copy(
            modelCompressionPolicies = modelCompressionPolicies
                .filterKeys { key -> normalizeModelReference(key) !in removedVirtualRefs }
        )
    }

    private fun AppConfig.removeUpstreamReferences(removedUpstreams: List<UpstreamModel>): AppConfig {
        if (removedUpstreams.isEmpty()) return this
        val removedUpstreamIds = removedUpstreams.map { it.id }.toSet()
        val removedUpstreamRefs = removedUpstreams
            .flatMap { listOfNotNull(it.id, it.upstreamModelId, it.hostModelId) }
            .map(::normalizeModelReference)
            .toSet()
        val removedVirtualModels = virtualModels.filter {
            normalizeModelReference(it.upstreamModelId) in removedUpstreamRefs
        }
        val removedVirtualModelIds = removedVirtualModels.map { it.id }.toSet()
        val removedVirtualRefs = removedVirtualModels
            .flatMap { RouteResolver.acceptedIds(it) + it.id }
            .map(::normalizeModelReference)
            .toSet()
        val removedPolicyKeys = removedUpstreamRefs + removedVirtualRefs
        return copy(
            upstreamModels = upstreamModels.filterNot { it.id in removedUpstreamIds },
            virtualModels = virtualModels
                .filterNot { it.id in removedVirtualModelIds }
                .map { virtual ->
                    val fallback = virtual.fallbackVirtualModelId?.let(::normalizeModelReference)
                    if (fallback != null && fallback in removedVirtualRefs) {
                        virtual.copy(fallbackVirtualModelId = null)
                    } else {
                        virtual
                    }
                },
            modelCompressionPolicies = modelCompressionPolicies
                .filterKeys { key -> normalizeModelReference(key) !in removedPolicyKeys }
        )
    }

    fun saveCompressionPolicy(modelId: String, policy: ModelCompressionPolicy?) {
        configStore.updateConfig { current ->
            val updatedPolicies = current.modelCompressionPolicies.toMutableMap()

            // 检查是否属于官方模型列表
            val currentOfficial = _officialModels.value
            val matchedOfficial = currentOfficial.find { it.id == modelId }
            if (matchedOfficial != null) {
                // 查找同一 BaseName 聚类或存在 replacement 映射的所有关联 ID
                val regex = Regex("""^(.*?)(?:\s*\((.*?)\))?$""")
                val targetBaseName = regex.find(matchedOfficial.displayName.ifBlank { matchedOfficial.id })
                    ?.groupValues?.getOrNull(1)?.trim() ?: matchedOfficial.id

                val relatedIds = currentOfficial.filter { m ->
                    val mBase = regex.find(m.displayName.ifBlank { m.id })?.groupValues?.getOrNull(1)?.trim() ?: m.id
                    mBase.equals(
                        targetBaseName,
                        ignoreCase = true
                    ) || m.replacementModelId == modelId || matchedOfficial.replacementModelId == m.id
                }.map { it.id }.toSet() + modelId

                relatedIds.forEach { id ->
                    if (policy != null) {
                        updatedPolicies[id] = policy
                    } else {
                        updatedPolicies.remove(id)
                    }
                }
            } else {
                if (policy != null) {
                    updatedPolicies[modelId] = policy
                } else {
                    updatedPolicies.remove(modelId)
                }
            }

            current.copy(modelCompressionPolicies = updatedPolicies)
        }
    }

    fun clearActivityLogs() {
        ActivityRecorder.clear()
    }

    fun runDoctor() {
        viewModelScope.launch {
            _isDoctorRunning.value = true
            try {
                _doctorReport.value = doctorEngine.diagnose()
            } finally {
                _isDoctorRunning.value = false
            }
        }
    }

    fun runDoctorAutoFix(action: com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction) {
        if (action is com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction.OpenAddProvider) {
            selectTab(NavTab.MODELS)
            requestOpenProviderEditor()
            closeDoctorDialog()
            return
        }
        viewModelScope.launch {
            val success = doctorEngine.autoFix(action)
            refreshHostStatus()
            if (success) {
                showNotice("已执行自动修复", NoticeKind.SUCCESS)
            } else {
                showNotice("自动修复失败，请手动检查", NoticeKind.ERROR)
            }
            runDoctor()
        }
    }

    // ---- 模型连通性测试状态 ----
    enum class ModelTestStatusKind {
        PENDING, SUCCESS, ERROR
    }

    data class ModelTestStatus(
        val status: ModelTestStatusKind,
        val latencyMs: Long? = null,
        val error: String? = null
    )

    private val _modelTestStatuses = MutableStateFlow<Map<String, ModelTestStatus>>(emptyMap())
    val modelTestStatuses: StateFlow<Map<String, ModelTestStatus>> = _modelTestStatuses.asStateFlow()

    private val _providerTestingIds = MutableStateFlow<Set<String>>(emptySet())
    val providerTestingIds: StateFlow<Set<String>> = _providerTestingIds.asStateFlow()

    fun testProviderModels(providerId: String) {
        val provider = config.value.providers.find { it.id == providerId } ?: return
        val models = config.value.upstreamModels.filter { it.providerId == providerId }
        if (models.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _providerTestingIds.value = _providerTestingIds.value + providerId
            // 将该 Provider 下所有模型置为 PENDING
            val pendingMap = _modelTestStatuses.value.toMutableMap()
            models.forEach {
                pendingMap[it.id] = ModelTestStatus(ModelTestStatusKind.PENDING)
            }
            _modelTestStatuses.value = pendingMap

            var successCount = 0
            val updatedMap = _modelTestStatuses.value.toMutableMap()

            // 并发测试 (限制并发为 3)
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            val jobs = models.map { model ->
                launch {
                    semaphore.acquire()
                    try {
                        val result = ConnectionTester.testProvider(provider, model.upstreamModelId)
                        synchronized(updatedMap) {
                            if (result.success) {
                                successCount++
                                updatedMap[model.id] = ModelTestStatus(
                                    status = ModelTestStatusKind.SUCCESS,
                                    latencyMs = result.latencyMs
                                )
                            } else {
                                updatedMap[model.id] = ModelTestStatus(
                                    status = ModelTestStatusKind.ERROR,
                                    error = result.error ?: "HTTP ${result.statusCode}"
                                )
                            }
                            _modelTestStatuses.value = updatedMap.toMap()
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
            _providerTestingIds.value = _providerTestingIds.value - providerId

            val total = models.size
            if (successCount == total) {
                showNotice("服务商测试完成：$successCount/$total 项测试通过", NoticeKind.SUCCESS)
            } else {
                showNotice(
                    "服务商测试完成：$successCount/$total 项通过，${total - successCount} 项失败",
                    NoticeKind.ERROR
                )
            }
        }
    }

    fun testSingleModel(model: UpstreamModel, provider: Provider) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val updatedMap = _modelTestStatuses.value.toMutableMap()
            updatedMap[model.id] = ModelTestStatus(ModelTestStatusKind.PENDING)
            _modelTestStatuses.value = updatedMap

            val result = ConnectionTester.testProvider(provider, model.upstreamModelId)
            val finalMap = _modelTestStatuses.value.toMutableMap()
            if (result.success) {
                finalMap[model.id] = ModelTestStatus(
                    status = ModelTestStatusKind.SUCCESS,
                    latencyMs = result.latencyMs
                )
                showNotice(
                    "${model.displayName ?: model.upstreamModelId} 测试成功 (${result.latencyMs}ms)",
                    NoticeKind.SUCCESS
                )
            } else {
                finalMap[model.id] = ModelTestStatus(
                    status = ModelTestStatusKind.ERROR,
                    error = result.error ?: "HTTP ${result.statusCode}"
                )
                showNotice(
                    "${model.displayName ?: model.upstreamModelId} 测试失败: ${result.error ?: result.statusCode}",
                    NoticeKind.ERROR
                )
            }
            _modelTestStatuses.value = finalMap
        }
    }

    fun deleteSingleModel(modelId: String) {
        try {
            configStore.updateConfig { current ->
                val normalizedReference = normalizeModelReference(modelId)
                val matchedVirtualUpstreamIds = current.virtualModels
                    .filter { virtual ->
                        RouteResolver.acceptedIds(virtual)
                            .map(::normalizeModelReference)
                            .contains(normalizedReference)
                    }
                    .map { virtual -> virtual.upstreamModelId }
                    .toSet()
                val removed = current.upstreamModels.filter { upstream ->
                    val references = listOfNotNull(
                        upstream.id,
                        upstream.upstreamModelId,
                        upstream.hostModelId
                    ).map(::normalizeModelReference)
                    normalizedReference in references || upstream.id in matchedVirtualUpstreamIds
                }
                if (removed.isEmpty()) {
                    throw IllegalArgumentException("未找到要删除的模型：$modelId")
                }
                current.ensureFallbackReferencesAreSafe(removed)
                current.removeUpstreamReferences(removed)
            }
            showNotice("已删除模型")
        } catch (error: Exception) {
            showNotice("模型删除失败：${error.message ?: "存在未处理的 fallback 引用"}", NoticeKind.ERROR)
        }
    }

    fun updateVirtualModelFallback(
        virtualModelId: String,
        fallbackVirtualModelId: String?
    ): Boolean {
        return try {
            configStore.updateConfig { current ->
                val source = current.virtualModels.firstOrNull { virtual -> virtual.id == virtualModelId }
                    ?: throw IllegalArgumentException("VirtualModel 不存在：$virtualModelId")
                val normalizedTarget = fallbackVirtualModelId
                    ?.trim()
                    ?.removePrefix("models/")
                    ?.takeIf { it.isNotEmpty() }
                val target = normalizedTarget?.let { targetId ->
                    current.virtualModels.firstOrNull { virtual ->
                        virtual.id == targetId || targetId in RouteResolver.acceptedIds(virtual)
                    }
                }
                if (normalizedTarget != null && target == null) {
                    throw IllegalArgumentException("fallback VirtualModel 不存在：$normalizedTarget")
                }
                if (target?.id == source.id) {
                    throw IllegalArgumentException("VirtualModel 不能将自身设置为 fallback")
                }
                current.copy(
                    virtualModels = current.virtualModels.map { virtual ->
                        if (virtual.id == source.id) {
                            virtual.copy(fallbackVirtualModelId = target?.id)
                        } else {
                            virtual
                        }
                    }
                )
            }
            showNotice(
                if (fallbackVirtualModelId.isNullOrBlank()) {
                    "已清除 fallback"
                } else {
                    "fallback 配置已保存"
                },
                NoticeKind.SUCCESS
            )
            true
        } catch (error: Exception) {
            showNotice("fallback 保存失败：${error.message ?: "未知错误"}", NoticeKind.ERROR)
            false
        }
    }

    fun updateSingleModel(updatedModel: UpstreamModel): Boolean {
        try {
            configStore.updateConfig { current ->
                val provider = current.providers.firstOrNull { item -> item.id == updatedModel.providerId }
                    ?: throw IllegalArgumentException("模型关联的 Provider 不存在")
                val providerModels = current.upstreamModels.map { model ->
                    if (model.id == updatedModel.id) {
                        updatedModel
                    } else {
                        model
                    }
                }.filter { model -> model.providerId == provider.id }
                val synchronized = ProviderModelSynchronizer.synchronize(
                    config = current,
                    provider = provider,
                    selectedModels = providerModels
                ).getOrThrow()
                val removedVirtualModels = current.virtualModels.filter { previous ->
                    synchronized.virtualModels.none { updated -> updated.id == previous.id }
                }
                current.ensureFallbackReferencesAreSafe(
                    removedUpstreams = emptyList(),
                    removedVirtualModels = removedVirtualModels
                )
                current.removeVirtualReferences(removedVirtualModels).copy(
                    upstreamModels = synchronized.upstreamModels,
                    virtualModels = synchronized.virtualModels
                )
            }
            showNotice("已更新模型配置", NoticeKind.SUCCESS)
            return true
        } catch (error: Exception) {
            showNotice(
                "模型配置更新失败：${error.message ?: "未知错误"}",
                NoticeKind.ERROR
            )
            return false
        }
    }

    fun updateLanguage(lang: AppLanguage) {
        I18nManager.currentLanguage = lang
        configStore.updateConfig { it.copy(language = lang.code) }
    }

    fun updateThemeMode(mode: String) {
        configStore.updateConfig { it.copy(themeMode = mode) }
    }

    fun updateProxyPort(port: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                configStore.updateConfig { current -> current.copy(proxyPort = port) }
                val result = restartProxyInternal()
                refreshHostStatus()
                if (result.isSuccess) {
                    showNotice("代理端口已更新为 ${result.getOrThrow()}", NoticeKind.SUCCESS)
                } else {
                    showNotice(
                        "代理端口更新后启动失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                        NoticeKind.ERROR
                    )
                }
            } catch (error: Exception) {
                showNotice(
                    "代理端口保存失败：${error.message ?: "未知错误"}",
                    NoticeKind.ERROR
                )
            }
        }
    }

    override fun onCleared() {
        proxyServer.stop()
        super.onCleared()
    }
}
