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

                val excludedCustomIds = configStore.currentConfig.providerModelBindings
                    .map(ProviderModelBinding::bindingId)
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

    fun toggleOfficialModel(catalogModelId: String) {
        val current = configStore.currentConfig
        val disabledIds = current.disabledOfficialCatalogModelIds.toMutableList()
        if (catalogModelId in disabledIds) disabledIds.remove(catalogModelId) else disabledIds.add(catalogModelId)
        configStore.saveConfig(current.copy(disabledOfficialCatalogModelIds = disabledIds))
    }

    fun toggleOfficialModelGroup(catalogModelIds: Set<String>, enable: Boolean) {
        val current = configStore.currentConfig
        val disabledIds = current.disabledOfficialCatalogModelIds.toMutableSet()
        if (enable) disabledIds.removeAll(catalogModelIds) else disabledIds.addAll(catalogModelIds)
        configStore.saveConfig(current.copy(disabledOfficialCatalogModelIds = disabledIds.toList()))
    }

    fun toggleCustomModel(bindingId: String) {
        val current = configStore.currentConfig
        val updatedBindings = current.providerModelBindings.map { binding ->
            if (binding.bindingId == bindingId) binding.copy(enabled = !binding.enabled) else binding
        }
        val updatedBinding = updatedBindings.firstOrNull { it.bindingId == bindingId }
        val updatedVariants = if (updatedBinding != null) {
            current.modelRouteVariants.map { variant ->
                if (variant.bindingId == bindingId) variant.copy(enabled = updatedBinding.enabled) else variant
            }
        } else {
            current.modelRouteVariants
        }
        configStore.saveConfig(
            current.copy(
                providerModelBindings = updatedBindings,
                modelRouteVariants = updatedVariants
            )
        )
    }

    fun saveProvider(provider: Provider, bindings: List<ProviderModelBinding>): Boolean {
        return try {
            val syncResult = ProviderModelSynchronizer.synchronize(
                config = configStore.currentConfig,
                provider = provider,
                selectedBindings = bindings
            ).getOrThrow()

            configStore.updateConfig { current ->
                val updatedProviders = current.providers.filterNot { it.id == provider.id } + provider
                current.copy(
                    providers = updatedProviders,
                    providerModelBindings = syncResult.providerModelBindings,
                    modelRouteVariants = syncResult.modelRouteVariants,
                    compressionPolicyAssignments = retainValidCompressionPolicyAssignments(
                        assignments = current.compressionPolicyAssignments,
                        bindings = syncResult.providerModelBindings,
                        variants = syncResult.modelRouteVariants
                    )
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
        val removedBindingIds = current.providerModelBindings
            .filter { it.providerConfigId == providerId }
            .mapTo(mutableSetOf(), ProviderModelBinding::bindingId)
        val removedVariantIds = current.modelRouteVariants
            .filter { it.bindingId in removedBindingIds }
            .mapTo(mutableSetOf(), ModelRouteVariant::variantId)
        try {
            configStore.saveConfig(
                current.copy(
                    providers = current.providers.filterNot { it.id == providerId },
                    providerModelBindings = current.providerModelBindings.filterNot {
                        it.bindingId in removedBindingIds
                    },
                    modelRouteVariants = current.modelRouteVariants.filterNot {
                        it.variantId in removedVariantIds
                    },
                    compressionPolicyAssignments = current.compressionPolicyAssignments.filterNot { assignment ->
                        assignment.targetsAny(removedBindingIds, removedVariantIds)
                    }
                )
            )
            showNotice(s.modelsProviderDeleted(targetProvider.name), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsProviderDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun deleteSingleModel(bindingId: String) {
        val current = configStore.currentConfig
        val targetBinding = current.providerModelBindings.find { it.bindingId == bindingId } ?: return
        val removedVariantIds = current.modelRouteVariants
            .filter { it.bindingId == bindingId }
            .mapTo(mutableSetOf(), ModelRouteVariant::variantId)
        try {
            configStore.saveConfig(
                current.copy(
                    providerModelBindings = current.providerModelBindings.filterNot { it.bindingId == bindingId },
                    modelRouteVariants = current.modelRouteVariants.filterNot { it.variantId in removedVariantIds },
                    compressionPolicyAssignments = current.compressionPolicyAssignments.filterNot { assignment ->
                        assignment.targetsAny(setOf(bindingId), removedVariantIds)
                    }
                )
            )
            showNotice(s.modelsModelDeleted(targetBinding.effectiveName), NoticeKind.SUCCESS)
        } catch (e: Exception) {
            showNotice(s.modelsModelDeleteFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
        }
    }

    fun updateSingleModel(updatedBinding: ProviderModelBinding): Boolean {
        return try {
            configStore.updateConfig { current ->
                val provider = current.providers.firstOrNull { item -> item.id == updatedBinding.providerConfigId }
                    ?: throw IllegalArgumentException(s.modelsProviderNotFound)
                val providerBindings = current.providerModelBindings.map { binding ->
                    if (binding.bindingId == updatedBinding.bindingId) updatedBinding else binding
                }.filter { binding -> binding.providerConfigId == provider.id }
                val synchronized = ProviderModelSynchronizer.synchronize(
                    config = current,
                    provider = provider,
                    selectedBindings = providerBindings
                ).getOrThrow()
                current.copy(
                    providerModelBindings = synchronized.providerModelBindings,
                    modelRouteVariants = synchronized.modelRouteVariants,
                    compressionPolicyAssignments = retainValidCompressionPolicyAssignments(
                        assignments = current.compressionPolicyAssignments,
                        bindings = synchronized.providerModelBindings,
                        variants = synchronized.modelRouteVariants
                    )
                )
            }
            showNotice(s.modelsModelUpdated(updatedBinding.effectiveName), NoticeKind.SUCCESS)
            true
        } catch (e: Exception) {
            showNotice(s.modelsModelUpdateFailed(e.message ?: s.commonUnknown), NoticeKind.ERROR)
            false
        }
    }

    fun saveCompressionPolicy(
        targetType: CompressionPolicyTargetType,
        targetId: String,
        policy: ModelCompressionPolicy?
    ) {
        configStore.updateConfig { current ->
            val retainedAssignments = current.compressionPolicyAssignments.filterNot { assignment ->
                assignment.targetType == targetType && assignment.targetId == targetId
            }
            current.copy(
                compressionPolicyAssignments = if (policy == null) {
                    retainedAssignments
                } else {
                    retainedAssignments + ModelCompressionPolicyAssignment(targetType, targetId, policy)
                }
            )
        }
    }

    fun testSingleModel(binding: ProviderModelBinding, provider: Provider) {
        scope.launch(Dispatchers.IO) {
            val updatedMap = modelTestStatusesFlow.value.toMutableMap()
            updatedMap[binding.bindingId] = AppViewModel.ModelTestStatus(AppViewModel.ModelTestStatusKind.PENDING)
            modelTestStatusesFlow.value = updatedMap

            val result = ConnectionTester.testProvider(provider, binding)
            val finalMap = modelTestStatusesFlow.value.toMutableMap()
            if (result.success) {
                finalMap[binding.bindingId] = AppViewModel.ModelTestStatus(
                    status = AppViewModel.ModelTestStatusKind.SUCCESS,
                    latencyMs = result.latencyMs
                )
                showNotice(
                    s.modelsModelTestSuccess(binding.effectiveName, result.latencyMs),
                    NoticeKind.SUCCESS
                )
            } else {
                finalMap[binding.bindingId] = AppViewModel.ModelTestStatus(
                    status = AppViewModel.ModelTestStatusKind.ERROR,
                    error = result.error ?: "HTTP ${result.statusCode}"
                )
                showNotice(
                    s.modelsModelTestFailed(
                        binding.effectiveName,
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
        val models = current.providerModelBindings.filter { it.providerConfigId == providerId }
        if (models.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            providerTestingIdsFlow.value = providerTestingIdsFlow.value + providerId
            val pendingMap = modelTestStatusesFlow.value.toMutableMap()
            models.forEach {
                pendingMap[it.bindingId] = AppViewModel.ModelTestStatus(AppViewModel.ModelTestStatusKind.PENDING)
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
                                updatedMap[model.bindingId] = AppViewModel.ModelTestStatus(
                                    status = AppViewModel.ModelTestStatusKind.SUCCESS,
                                    latencyMs = result.latencyMs
                                )
                            } else {
                                updatedMap[model.bindingId] = AppViewModel.ModelTestStatus(
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

    private fun retainValidCompressionPolicyAssignments(
        assignments: List<ModelCompressionPolicyAssignment>,
        bindings: List<ProviderModelBinding>,
        variants: List<ModelRouteVariant>
    ): List<ModelCompressionPolicyAssignment> {
        val bindingIds = bindings.mapTo(mutableSetOf(), ProviderModelBinding::bindingId)
        val variantIds = variants.mapTo(mutableSetOf(), ModelRouteVariant::variantId)
        return assignments.filterNot { assignment ->
            when (assignment.targetType) {
                CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL -> false
                CompressionPolicyTargetType.PROVIDER_MODEL_BINDING -> assignment.targetId !in bindingIds
                CompressionPolicyTargetType.MODEL_ROUTE_VARIANT -> assignment.targetId !in variantIds
            }
        }
    }

    private fun ModelCompressionPolicyAssignment.targetsAny(
        bindingIds: Set<String>,
        variantIds: Set<String>
    ): Boolean = when (targetType) {
        CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL -> false
        CompressionPolicyTargetType.PROVIDER_MODEL_BINDING -> targetId in bindingIds
        CompressionPolicyTargetType.MODEL_ROUTE_VARIANT -> targetId in variantIds
    }
}
