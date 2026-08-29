package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.*
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 负责官方目录、Provider 配置、自定义模型及模型连通性测试。
 *
 * StateFlow 由 AppViewModel 持有并注入，委托类只承载模型相关副作用和状态更新。
 */
class ProviderModelDelegate(
    private val scope: CoroutineScope,
    private val configStore: ConfigStore,
    private val accountStore: AccountStore,
    private val googleAuthService: GoogleAuthService,
    private val configFlow: StateFlow<AppConfig>,
    private val officialModelsFlow: MutableStateFlow<List<OfficialCatalogModel>>,
    private val isFetchingOfficialModelsFlow: MutableStateFlow<Boolean>,
    private val officialModelsErrorFlow: MutableStateFlow<String?>,
    private val modelTestStatusesFlow: MutableStateFlow<Map<String, AppViewModel.ModelTestStatus>>,
    private val providerTestingIdsFlow: MutableStateFlow<Set<String>>,
    private val showNotice: (String, NoticeKind) -> Unit
) {

    private val s get() = com.yuzhiqiang.antigravity.i18n.I18nManager.strings

    fun fetchOfficialModels(): Job {
        return scope.launch(Dispatchers.IO) {
            isFetchingOfficialModelsFlow.value = true
            officialModelsErrorFlow.value = null
            try {
                val currentAccount = accountStore.currentActiveAccount()
                    ?: accountStore.currentAccounts().firstOrNull()

                val excludedCustomIds = configStore.currentConfig.upstreamModels
                    .map(UpstreamModel::id)
                    .toSet()
                val result = OfficialCatalogProbe.fetchOfficialModels(
                    account = currentAccount,
                    tokenRefreshCallback = { refreshToken ->
                        googleAuthService.refreshAccessToken(refreshToken).map { it.accessToken }
                    },
                    excludedModelIds = excludedCustomIds
                )
                result.fold(
                    onSuccess = { models -> officialModelsFlow.value = models },
                    onFailure = { error ->
                        officialModelsErrorFlow.value = error.message ?: s.modelsOfficialSyncFailed(s.commonUnknown)
                    }
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                officialModelsErrorFlow.value = error.message ?: s.modelsOfficialSyncFailed(s.commonUnknown)
            } finally {
                isFetchingOfficialModelsFlow.value = false
            }
        }
    }

    fun toggleOfficialModel(modelId: String) {
        val current = configStore.currentConfig
        val list = current.disabledOfficialModels.toMutableList()
        if (list.contains(modelId)) list.remove(modelId) else list.add(modelId)
        configStore.saveConfig(current.copy(disabledOfficialModels = list))
    }

    fun toggleOfficialModelGroup(modelIds: Set<String>, enable: Boolean) {
        val current = configStore.currentConfig
        val set = current.disabledOfficialModels.toMutableSet()
        if (enable) set.removeAll(modelIds) else set.addAll(modelIds)
        configStore.saveConfig(current.copy(disabledOfficialModels = set.toList()))
    }

    fun toggleCustomModel(modelId: String) {
        val current = configStore.currentConfig
        val updated = current.upstreamModels.map {
            if (it.id == modelId) it.copy(enabled = !it.enabled) else it
        }
        val affectedUpstream = updated.find { it.id == modelId }
        val updatedVirtuals = if (affectedUpstream != null) {
            current.virtualModels.map { virtual ->
                if (virtual.upstreamModelId == modelId) virtual.copy(enabled = affectedUpstream.enabled) else virtual
            }
        } else {
            current.virtualModels
        }
        configStore.saveConfig(current.copy(upstreamModels = updated, virtualModels = updatedVirtuals))
    }

    fun saveProvider(provider: Provider, models: List<UpstreamModel>): Boolean {
        return try {
            val syncResult = ProviderModelSynchronizer.synchronize(
                config = configStore.currentConfig,
                provider = provider,
                selectedModels = models
            ).getOrThrow()

            configStore.updateConfig { current ->
                val updatedProviders = current.providers.filterNot { it.id == provider.id } + provider
                current.copy(
                    providers = updatedProviders,
                    upstreamModels = syncResult.upstreamModels,
                    virtualModels = syncResult.virtualModels
                )
            }
            showNotice(s.modelsProviderSaved(provider.name), NoticeKind.SUCCESS)
            true
        } catch (e: Exception) {
            showNotice(s.modelsProviderSaveFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    fun deleteProvider(providerId: String) {
        val current = configStore.currentConfig
        val targetProvider = current.providers.find { it.id == providerId } ?: return
        val removedUpstreams = current.upstreamModels.filter { it.providerId == providerId }
        val removedVirtualModels = current.virtualModels.filter {
            it.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
        }
        try {
            configStore.saveConfig(
                current.copy(
                    providers = current.providers.filterNot { it.id == providerId },
                    upstreamModels = current.upstreamModels.filterNot { it.providerId == providerId },
                    virtualModels = current.virtualModels.filterNot { virtual ->
                        virtual.upstreamModelId in removedUpstreams.map(UpstreamModel::id).toSet()
                    },
                    modelCompressionPolicies = current.modelCompressionPolicies.filterKeys { key ->
                        key !in removedUpstreams.map(UpstreamModel::id).toSet() &&
                                key !in removedVirtualModels.map(VirtualModel::id).toSet()
                    }
                )
            )
            showNotice(s.modelsProviderDeleted(targetProvider.name), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsProviderDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun deleteSingleModel(modelId: String) {
        val current = configStore.currentConfig
        val targetModel = current.upstreamModels.find { it.id == modelId } ?: return
        try {
            configStore.saveConfig(
                current.copy(
                    upstreamModels = current.upstreamModels.filterNot { it.id == modelId },
                    virtualModels = current.virtualModels.filterNot { it.upstreamModelId == modelId },
                    modelCompressionPolicies = current.modelCompressionPolicies - modelId
                )
            )
            showNotice(s.modelsModelDeleted(targetModel.displayName ?: targetModel.name), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsModelDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun updateSingleModel(updatedModel: UpstreamModel): Boolean {
        return try {
            configStore.updateConfig { current ->
                val provider = current.providers.firstOrNull { item -> item.id == updatedModel.providerId }
                    ?: throw IllegalArgumentException(s.modelsProviderNotFound)
                val providerModels = current.upstreamModels.map { model ->
                    if (model.id == updatedModel.id) updatedModel else model
                }.filter { model -> model.providerId == provider.id }
                val synchronized = ProviderModelSynchronizer.synchronize(
                    config = current,
                    provider = provider,
                    selectedModels = providerModels
                ).getOrThrow()
                current.copy(
                    upstreamModels = synchronized.upstreamModels,
                    virtualModels = synchronized.virtualModels
                )
            }
            showNotice(s.modelsModelUpdated(updatedModel.displayName ?: updatedModel.name), NoticeKind.SUCCESS)
            true
        } catch (e: Exception) {
            showNotice(s.modelsModelUpdateFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    fun saveCompressionPolicy(modelId: String, policy: ModelCompressionPolicy?) {
        configStore.updateConfig { current ->
            val updatedPolicies = current.modelCompressionPolicies.toMutableMap()
            var updatedUpstreams = current.upstreamModels
            val currentOfficial = officialModelsFlow.value
            val matchedOfficial = currentOfficial.find { it.id == modelId }
            if (matchedOfficial != null) {
                val regex = Regex("""^(.*?)(?:\s*\((.*?)\))?$""")
                val targetBaseName = regex.find(matchedOfficial.displayName.ifBlank { matchedOfficial.id })
                    ?.groupValues?.getOrNull(1)?.trim() ?: matchedOfficial.id

                val relatedIds = currentOfficial.filter { m ->
                    val mBase = regex.find(m.displayName.ifBlank { m.id })?.groupValues?.getOrNull(1)?.trim() ?: m.id
                    mBase.equals(targetBaseName, ignoreCase = true) ||
                            m.replacementModelId == modelId || matchedOfficial.replacementModelId == m.id
                }.map { it.id }.toMutableSet()
                relatedIds.add(modelId)

                // 补充模型族基础 ID 与 -tiered 父条目 ID，确保全量覆盖
                val baseSlug = modelId.removeSuffix("-high")
                    .removeSuffix("-medium")
                    .removeSuffix("-low")
                    .removeSuffix("-tiered")
                relatedIds.add(baseSlug)
                relatedIds.add("$baseSlug-tiered")

                relatedIds.forEach { id ->
                    if (policy != null) updatedPolicies[id] = policy else updatedPolicies.remove(id)
                }
            } else {
                if (policy != null) updatedPolicies[modelId] = policy else updatedPolicies.remove(modelId)
                // 同步更新 UpstreamModel 实体内部的 compressionPolicy 字段，确保双向一致
                updatedUpstreams = current.upstreamModels.map { upstream ->
                    val isDirectMatch = upstream.id == modelId || upstream.upstreamModelId == modelId
                    val isVirtualMatch =
                        current.virtualModels.any { it.id == modelId && it.upstreamModelId == upstream.id }
                    if (isDirectMatch || isVirtualMatch) {
                        upstream.copy(compressionPolicy = policy)
                    } else {
                        upstream
                    }
                }
            }
            current.copy(
                upstreamModels = updatedUpstreams,
                modelCompressionPolicies = updatedPolicies
            )
        }
    }

    fun testSingleModel(model: UpstreamModel, provider: Provider) {
        scope.launch(Dispatchers.IO) {
            val updatedMap = modelTestStatusesFlow.value.toMutableMap()
            updatedMap[model.id] = AppViewModel.ModelTestStatus(AppViewModel.ModelTestStatusKind.PENDING)
            modelTestStatusesFlow.value = updatedMap

            val result = ConnectionTester.testProvider(provider, model)
            val finalMap = modelTestStatusesFlow.value.toMutableMap()
            if (result.success) {
                finalMap[model.id] = AppViewModel.ModelTestStatus(
                    status = AppViewModel.ModelTestStatusKind.SUCCESS,
                    latencyMs = result.latencyMs
                )
                showNotice(
                    s.modelsModelTestSuccess(model.displayName ?: model.upstreamModelId, result.latencyMs),
                    NoticeKind.SUCCESS
                )
            } else {
                finalMap[model.id] = AppViewModel.ModelTestStatus(
                    status = AppViewModel.ModelTestStatusKind.ERROR,
                    error = result.error ?: "HTTP ${result.statusCode}"
                )
                showNotice(
                    s.modelsModelTestFailed(
                        model.displayName ?: model.upstreamModelId,
                        result.error ?: result.statusCode.toString()
                    ),
                    NoticeKind.ERROR
                )
            }
            modelTestStatusesFlow.value = finalMap
        }
    }

    fun testProviderModels(providerId: String) {
        val current = configFlow.value
        val provider = current.providers.find { it.id == providerId } ?: return
        val models = current.upstreamModels.filter { it.providerId == providerId }
        if (models.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            providerTestingIdsFlow.value = providerTestingIdsFlow.value + providerId
            val pendingMap = modelTestStatusesFlow.value.toMutableMap()
            models.forEach {
                pendingMap[it.id] = AppViewModel.ModelTestStatus(AppViewModel.ModelTestStatusKind.PENDING)
            }
            modelTestStatusesFlow.value = pendingMap

            var successCount = 0
            val updatedMap = modelTestStatusesFlow.value.toMutableMap()
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            val testMutex = Mutex()
            val jobs = models.map { model ->
                launch {
                    semaphore.acquire()
                    try {
                        val result = ConnectionTester.testProvider(provider, model)
                        testMutex.withLock {
                            if (result.success) {
                                successCount++
                                updatedMap[model.id] = AppViewModel.ModelTestStatus(
                                    status = AppViewModel.ModelTestStatusKind.SUCCESS,
                                    latencyMs = result.latencyMs
                                )
                            } else {
                                updatedMap[model.id] = AppViewModel.ModelTestStatus(
                                    status = AppViewModel.ModelTestStatusKind.ERROR,
                                    error = result.error ?: "HTTP ${result.statusCode}"
                                )
                            }
                            modelTestStatusesFlow.value = updatedMap.toMap()
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
            providerTestingIdsFlow.value = providerTestingIdsFlow.value - providerId

            val total = models.size
            if (successCount == total) {
                showNotice(s.modelsBatchTestSuccess(successCount, total), NoticeKind.SUCCESS)
            } else {
                showNotice(
                    s.modelsBatchTestPartial(successCount, total, total - successCount),
                    NoticeKind.ERROR
                )
            }
        }
    }
}
