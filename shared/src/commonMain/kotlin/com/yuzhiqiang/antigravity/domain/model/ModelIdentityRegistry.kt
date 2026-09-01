package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelObservation(
    @SerialName("requested_model_id") val requestedModelId: String? = null,
    @SerialName("variant_id") val variantId: String? = null,
    @SerialName("catalog_model_id") val catalogModelId: String? = null,
    @SerialName("runtime_model_id") val runtimeModelId: String? = null,
    @SerialName("binding_id") val bindingId: String? = null,
    @SerialName("provider_config_id") val providerConfigId: String? = null,
    @SerialName("provider_model_id") val providerModelId: String? = null,
    @SerialName("response_model_id") val responseModelId: String? = null,
    @SerialName("provider_vendor") val providerVendor: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("reasoning_level") val reasoningLevel: ReasoningLevel? = null
)

@Serializable
data class ModelIdentityEvidence(
    @SerialName("source") val source: ModelIdentitySource,
    @SerialName("value") val value: String
)

@Serializable
data class ResolvedModelIdentity(
    @SerialName("status") val status: ModelIdentityStatus,
    @SerialName("canonical_model_id") val canonicalModelId: String? = null,
    @SerialName("base_model_id") val baseModelId: String? = null,
    @SerialName("binding_id") val bindingId: String? = null,
    @SerialName("variant_id") val variantId: String? = null,
    @SerialName("catalog_model_id") val catalogModelId: String? = null,
    @SerialName("runtime_model_id") val runtimeModelId: String? = null,
    @SerialName("provider_config_id") val providerConfigId: String? = null,
    @SerialName("provider_model_id") val providerModelId: String? = null,
    @SerialName("response_model_id") val responseModelId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("reasoning_profile") val reasoningProfile: ReasoningProfile? = null,
    @SerialName("pricing_model_ids") val pricingModelIds: List<String> = emptyList(),
    @SerialName("evidence") val evidence: List<ModelIdentityEvidence> = emptyList()
) {
    val groupingKey: String
        get() = when {
            canonicalModelId != null -> "canonical:$canonicalModelId"
            bindingId != null -> "binding:$bindingId"
            variantId != null -> "variant:$variantId"
            !responseModelId.isNullOrBlank() -> "unresolved-response:${providerConfigId.orEmpty()}:$responseModelId"
            !providerModelId.isNullOrBlank() -> "unresolved-provider:${providerConfigId.orEmpty()}:$providerModelId"
            !catalogModelId.isNullOrBlank() -> "unresolved-catalog:$catalogModelId"
            !runtimeModelId.isNullOrBlank() -> "unresolved-runtime:$runtimeModelId"
            else -> "unresolved:unknown"
        }
}

/**
 * 官方目录、BYOK Binding 与宿主 Route Variant 的不可变精确索引。
 *
 * Registry 只接受显式 ID 和已注册 alias；displayName 不进入任何索引。
 */
class ModelIdentityRegistry private constructor(
    private val canonicalModels: Map<String, CanonicalModel>,
    private val bindings: Map<String, ProviderModelBinding>,
    private val variants: Map<String, ModelRouteVariant>,
    private val recordsByBindingId: Map<String, List<IdentityRecord>>,
    private val recordsByVariantId: Map<String, List<IdentityRecord>>,
    private val recordsByCatalogId: Map<String, List<IdentityRecord>>,
    private val recordsByRuntimeId: Map<String, List<IdentityRecord>>,
    private val recordsByScopedProviderId: Map<ScopedModelId, List<IdentityRecord>>,
    private val recordsByProviderId: Map<String, List<IdentityRecord>>
) {
    fun resolve(observation: ModelObservation): ResolvedModelIdentity {
        val evidenceCandidates = listOfNotNull(
            exact(observation.variantId, recordsByVariantId, ModelIdentitySource.ROUTE_SNAPSHOT),
            exact(observation.bindingId, recordsByBindingId, ModelIdentitySource.ROUTE_SNAPSHOT),
            scoped(
                observation.providerConfigId,
                observation.responseModelId,
                ModelIdentitySource.PROVIDER_RESPONSE
            ),
            scoped(
                observation.providerConfigId,
                observation.providerModelId,
                ModelIdentitySource.PROVIDER_CATALOG
            ),
            intersection(observation.catalogModelId, observation.runtimeModelId),
            exact(observation.catalogModelId, recordsByCatalogId, ModelIdentitySource.REGISTERED_ALIAS),
            exact(observation.runtimeModelId, recordsByRuntimeId, ModelIdentitySource.REGISTERED_ALIAS),
            uniqueUnscoped(observation.responseModelId, ModelIdentitySource.PROVIDER_RESPONSE),
            uniqueUnscoped(observation.providerModelId, ModelIdentitySource.PROVIDER_CATALOG)
        )

        val resolved = evidenceCandidates.mapNotNull { candidate -> candidate.record }.distinctBy { it.identityKey }
        if (resolved.size > 1) {
            return unresolved(observation, ModelIdentityStatus.CONFLICT, evidenceCandidates.flatMap { it.evidence })
        }
        val record = resolved.singleOrNull()
            ?: return unresolved(
                observation,
                ModelIdentityStatus.UNRESOLVED,
                evidenceCandidates.flatMap { it.evidence })
        val binding = record.bindingId?.let(bindings::get)
        val variant = record.variantId?.let(variants::get)
        val canonical = record.canonicalModelId?.let { canonicalModelId ->
            canonicalModels[normalized(canonicalModelId)]
        }
        val pricingIds = buildList {
            addAll(canonical?.pricingAliases.orEmpty())
            addAll(binding?.aliases.orEmpty().filter { it.kind == ModelAliasKind.PRICING }.map(ModelAlias::value))
            record.canonicalModelId?.let(::add)
        }.map(String::trim).filter(String::isNotEmpty).distinct()
        return ResolvedModelIdentity(
            status = ModelIdentityStatus.RESOLVED,
            canonicalModelId = record.canonicalModelId,
            baseModelId = canonical?.baseModelId ?: record.baseModelId,
            bindingId = record.bindingId,
            variantId = record.variantId,
            catalogModelId = record.catalogModelId ?: observation.catalogModelId,
            runtimeModelId = record.runtimeModelId ?: observation.runtimeModelId,
            providerConfigId = record.providerConfigId ?: observation.providerConfigId,
            providerModelId = record.providerModelId ?: observation.providerModelId,
            responseModelId = observation.responseModelId,
            displayName = canonical?.displayName
                ?: variant?.displayName
                ?: binding?.displayName
                ?: record.displayName
                ?: observation.displayName
                ?: observation.responseModelId
                ?: observation.providerModelId
                ?: observation.catalogModelId
                ?: observation.runtimeModelId
                ?: "Unknown",
            reasoningProfile = variant?.reasoningProfile ?: record.reasoningProfile,
            pricingModelIds = pricingIds,
            evidence = evidenceCandidates.flatMap { it.evidence }.distinct()
        )
    }

    private fun exact(
        value: String?,
        index: Map<String, List<IdentityRecord>>,
        source: ModelIdentitySource
    ): Candidate? {
        val key = normalized(value) ?: return null
        val records = index[key].orEmpty().distinctBy { it.identityKey }
        val evidence = listOf(ModelIdentityEvidence(source, value.orEmpty()))
        return Candidate(records.singleOrNull(), evidence)
    }

    private fun scoped(
        providerConfigId: String?,
        modelId: String?,
        source: ModelIdentitySource
    ): Candidate? {
        val provider = normalized(providerConfigId) ?: return null
        val model = normalized(modelId) ?: return null
        val records = recordsByScopedProviderId[ScopedModelId(provider, model)]
            .orEmpty()
            .distinctBy { it.identityKey }
        return Candidate(records.singleOrNull(), listOf(ModelIdentityEvidence(source, modelId.orEmpty())))
    }

    private fun uniqueUnscoped(modelId: String?, source: ModelIdentitySource): Candidate? {
        val key = normalized(modelId) ?: return null
        val records = recordsByProviderId[key].orEmpty().distinctBy { it.identityKey }
        return Candidate(
            records.singleOrNull(),
            listOf(ModelIdentityEvidence(source, modelId.orEmpty()))
        )
    }

    private fun intersection(catalogModelId: String?, runtimeModelId: String?): Candidate? {
        val catalog = normalized(catalogModelId) ?: return null
        val runtime = normalized(runtimeModelId) ?: return null
        val catalogRecords = recordsByCatalogId[catalog].orEmpty().map { it.identityKey }.toSet()
        val records = recordsByRuntimeId[runtime].orEmpty()
            .filter { it.identityKey in catalogRecords }
            .distinctBy { it.identityKey }
        return Candidate(
            records.singleOrNull(),
            listOf(
                ModelIdentityEvidence(ModelIdentitySource.REGISTERED_ALIAS, catalogModelId.orEmpty()),
                ModelIdentityEvidence(ModelIdentitySource.REGISTERED_ALIAS, runtimeModelId.orEmpty())
            )
        )
    }

    private fun unresolved(
        observation: ModelObservation,
        status: ModelIdentityStatus,
        evidence: List<ModelIdentityEvidence>
    ): ResolvedModelIdentity = ResolvedModelIdentity(
        status = status,
        catalogModelId = observation.catalogModelId,
        runtimeModelId = observation.runtimeModelId,
        providerConfigId = observation.providerConfigId,
        providerModelId = observation.providerModelId,
        responseModelId = observation.responseModelId,
        displayName = observation.displayName
            ?: observation.responseModelId
            ?: observation.providerModelId
            ?: observation.catalogModelId
            ?: observation.runtimeModelId
            ?: "Unknown",
        evidence = evidence.distinct()
    )

    private data class Candidate(
        val record: IdentityRecord?,
        val evidence: List<ModelIdentityEvidence>
    )

    private data class ScopedModelId(val providerConfigId: String, val modelId: String)

    private data class IdentityRecord(
        val canonicalModelId: String?,
        val baseModelId: String?,
        val bindingId: String?,
        val variantId: String?,
        val catalogModelId: String?,
        val runtimeModelId: String?,
        val providerConfigId: String?,
        val providerModelId: String?,
        val displayName: String?,
        val reasoningProfile: ReasoningProfile?
    ) {
        val identityKey: String
            get() = canonicalModelId?.let { "canonical:$it" }
                ?: bindingId?.let { "binding:$it" }
                ?: variantId?.let { "variant:$it" }
                ?: "catalog:${catalogModelId.orEmpty()}"
    }

    companion object {
        fun empty(): ModelIdentityRegistry = from(AppConfig(), emptyList())

        fun from(
            config: AppConfig,
            officialModels: List<OfficialCatalogModel> = emptyList()
        ): ModelIdentityRegistry {
            val canonicalModels = config.canonicalModels.associateBy { normalized(it.canonicalModelId).orEmpty() }
            val bindings = config.providerModelBindings.associateBy(ProviderModelBinding::bindingId)
            val variants = config.modelRouteVariants.associateBy(ModelRouteVariant::variantId)
            val records = mutableListOf<IdentityRecord>()

            config.modelRouteVariants.forEach { variant ->
                val binding = variant.bindingId?.let(bindings::get) ?: return@forEach
                records += IdentityRecord(
                    canonicalModelId = binding.canonicalModelId,
                    baseModelId = binding.canonicalModelId?.let { canonicalModels[normalized(it)]?.baseModelId },
                    bindingId = binding.bindingId,
                    variantId = variant.variantId,
                    catalogModelId = variant.catalogModelId,
                    runtimeModelId = variant.runtimeModelId,
                    providerConfigId = binding.providerConfigId,
                    providerModelId = binding.providerModelId,
                    displayName = variant.displayName,
                    reasoningProfile = variant.reasoningProfile
                )
            }

            officialModels.forEach { model ->
                val bindingKey = model.canonicalModelId
                    ?: model.tierGroupIds.sorted().firstOrNull()?.let { "tier:$it" }
                    ?: "catalog:${model.catalogModelId}"
                records += IdentityRecord(
                    canonicalModelId = model.canonicalModelId,
                    baseModelId = model.baseModelId,
                    bindingId = "official-binding:$bindingKey",
                    variantId = "official-variant:${model.catalogModelId}",
                    catalogModelId = model.catalogModelId,
                    runtimeModelId = model.runtimeModelId,
                    providerConfigId = OFFICIAL_PROVIDER_CONFIG_ID,
                    providerModelId = model.providerModelId,
                    displayName = model.displayName,
                    reasoningProfile = model.reasoningProfile
                )
            }

            fun index(selector: (IdentityRecord) -> String?): Map<String, List<IdentityRecord>> = records
                .mapNotNull { record -> normalized(selector(record))?.let { it to record } }
                .groupBy({ it.first }, { it.second })

            val scoped = mutableListOf<Pair<ScopedModelId, IdentityRecord>>()
            records.forEach { record ->
                val providerConfigId = normalized(record.providerConfigId) ?: return@forEach
                normalized(record.providerModelId)?.let { providerModelId ->
                    scoped += ScopedModelId(providerConfigId, providerModelId) to record
                }
                record.bindingId?.let(bindings::get)?.aliases.orEmpty()
                    .filter { alias -> alias.kind == ModelAliasKind.PROVIDER_REQUEST || alias.kind == ModelAliasKind.PROVIDER_RESPONSE }
                    .mapNotNull { alias -> normalized(alias.value) }
                    .forEach { alias -> scoped += ScopedModelId(providerConfigId, alias) to record }
            }
            val unscoped = mutableListOf<Pair<String, IdentityRecord>>()
            scoped.forEach { (key, record) -> unscoped += key.modelId to record }

            return ModelIdentityRegistry(
                canonicalModels = canonicalModels,
                bindings = bindings,
                variants = variants,
                recordsByBindingId = index(IdentityRecord::bindingId),
                recordsByVariantId = index(IdentityRecord::variantId),
                recordsByCatalogId = index(IdentityRecord::catalogModelId),
                recordsByRuntimeId = index(IdentityRecord::runtimeModelId),
                recordsByScopedProviderId = scoped.groupBy({ it.first }, { it.second }),
                recordsByProviderId = unscoped.groupBy({ it.first }, { it.second })
            )
        }

        private const val OFFICIAL_PROVIDER_CONFIG_ID = "official-cloud-code"

        private fun normalized(value: String?): String? = value
            ?.trim()
            ?.removePrefix("models/")
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
    }
}

object ModelIdentityRegistryHolder {
    @Volatile
    private var registry: ModelIdentityRegistry = ModelIdentityRegistry.empty()
    private var config: AppConfig = AppConfig()
    private var officialModels: List<OfficialCatalogModel> = emptyList()

    fun snapshot(): ModelIdentityRegistry = registry

    @Synchronized
    fun updateConfig(value: AppConfig) {
        config = value
        registry = ModelIdentityRegistry.from(config, officialModels)
    }

    @Synchronized
    fun updateOfficialModels(value: List<OfficialCatalogModel>) {
        officialModels = value
        registry = ModelIdentityRegistry.from(config, officialModels)
    }
}
