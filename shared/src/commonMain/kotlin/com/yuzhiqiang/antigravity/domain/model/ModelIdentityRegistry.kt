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
            ?: return resolveKnownProviderFamily(
                observation = observation,
                evidence = evidenceCandidates.flatMap { it.evidence }
            ) ?: unresolved(
                observation,
                ModelIdentityStatus.UNRESOLVED,
                evidenceCandidates.flatMap { it.evidence })
        val binding = record.bindingId?.let(bindings::get)
        val variant = record.variantId?.let(variants::get)
        val canonicalModelId = record.canonicalModelId ?: providerCanonicalModelId(
            providerVendor = record.providerVendor ?: observation.providerVendor,
            providerModelId = observation.responseModelId
                ?: observation.providerModelId
                ?: record.providerModelId
        )
        val canonical = canonicalModelId?.let { canonicalModels[normalized(it)] }
        val pricingIds = buildList {
            addAll(canonical?.pricingAliases.orEmpty())
            addAll(binding?.aliases.orEmpty().filter { it.kind == ModelAliasKind.PRICING }.map(ModelAlias::value))
            canonicalModelId?.let(::add)
        }.map(String::trim).filter(String::isNotEmpty).distinct()
        val rawDisplayName = canonical?.displayName
            ?: variant?.displayName
            ?: binding?.displayName
            ?: record.displayName
            ?: observation.displayName
            ?: observation.responseModelId
            ?: observation.providerModelId
            ?: observation.catalogModelId
            ?: observation.runtimeModelId
            ?: "Unknown"
        return ResolvedModelIdentity(
            status = if (canonicalModelId != null) {
                ModelIdentityStatus.RESOLVED
            } else {
                ModelIdentityStatus.UNRESOLVED
            },
            canonicalModelId = canonicalModelId,
            baseModelId = canonical?.baseModelId ?: record.baseModelId,
            bindingId = record.bindingId,
            variantId = record.variantId,
            catalogModelId = record.catalogModelId ?: observation.catalogModelId,
            runtimeModelId = record.runtimeModelId ?: observation.runtimeModelId,
            providerConfigId = record.providerConfigId ?: observation.providerConfigId,
            providerModelId = record.providerModelId ?: observation.providerModelId,
            responseModelId = observation.responseModelId,
            displayName = if (canonicalModelId != null) {
                ModelIdentity.stripDisplayLevelSuffix(rawDisplayName)
            } else {
                rawDisplayName
            },
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
        val records = index[key].orEmpty()
        val evidence = listOf(ModelIdentityEvidence(source, value.orEmpty()))
        return Candidate(selectRecord(records), evidence)
    }

    private fun scoped(
        providerConfigId: String?,
        modelId: String?,
        source: ModelIdentitySource
    ): Candidate? {
        val provider = normalized(providerConfigId) ?: return null
        val model = normalized(modelId) ?: return null
        val records = recordsByScopedProviderId[ScopedModelId(provider, model)].orEmpty()
        return Candidate(selectRecord(records), listOf(ModelIdentityEvidence(source, modelId.orEmpty())))
    }

    private fun uniqueUnscoped(modelId: String?, source: ModelIdentitySource): Candidate? {
        val key = normalized(modelId) ?: return null
        val records = providerResponseCandidates(key)
            .asSequence()
            .map(recordsByProviderId::get)
            .filterNotNull()
            .firstOrNull(List<IdentityRecord>::isNotEmpty)
            .orEmpty()
        return Candidate(
            selectRecord(records),
            listOf(ModelIdentityEvidence(source, modelId.orEmpty()))
        )
    }

    private fun intersection(catalogModelId: String?, runtimeModelId: String?): Candidate? {
        val catalog = normalized(catalogModelId) ?: return null
        val runtime = normalized(runtimeModelId) ?: return null
        val catalogRecords = recordsByCatalogId[catalog].orEmpty().map { it.identityKey }.toSet()
        val records = recordsByRuntimeId[runtime].orEmpty()
            .filter { it.identityKey in catalogRecords }
        return Candidate(
            selectRecord(records),
            listOf(
                ModelIdentityEvidence(ModelIdentitySource.REGISTERED_ALIAS, catalogModelId.orEmpty()),
                ModelIdentityEvidence(ModelIdentitySource.REGISTERED_ALIAS, runtimeModelId.orEmpty())
            )
        )
    }

    private fun providerResponseCandidates(modelId: String): List<String> = buildList {
        add(modelId)
        add(ModelIdentity.modelFamilyBase(modelId))
        add(modelId.replace(INTERNAL_RESPONSE_SUFFIX, ""))
    }.filter(String::isNotEmpty).distinct()

    private fun selectRecord(records: List<IdentityRecord>): IdentityRecord? {
        if (records.isEmpty()) return null
        if (records.size == 1) return records.single()

        val canonicalIds = records.mapNotNull { normalized(it.canonicalModelId) }.distinct()
        if (canonicalIds.size != 1 || records.any { it.canonicalModelId == null }) return null

        val first = records.first()
        return first.copy(
            bindingId = records.map(IdentityRecord::bindingId).distinct().singleOrNull(),
            variantId = records.map(IdentityRecord::variantId).distinct().singleOrNull(),
            catalogModelId = records.map(IdentityRecord::catalogModelId).distinct().singleOrNull(),
            runtimeModelId = records.map(IdentityRecord::runtimeModelId).distinct().singleOrNull(),
            providerConfigId = records.map(IdentityRecord::providerConfigId).distinct().singleOrNull(),
            providerModelId = records.map(IdentityRecord::providerModelId).distinct().singleOrNull(),
            providerVendor = records.map(IdentityRecord::providerVendor).distinct().singleOrNull(),
            displayName = ModelIdentity.stripDisplayLevelSuffix(first.displayName.orEmpty())
                .takeIf(String::isNotEmpty),
            reasoningProfile = records.map(IdentityRecord::reasoningProfile).distinct().singleOrNull()
        )
    }

    private fun resolveKnownProviderFamily(
        observation: ModelObservation,
        evidence: List<ModelIdentityEvidence>
    ): ResolvedModelIdentity? {
        val explicitModelId = observation.responseModelId ?: observation.providerModelId
        val observedModelId = explicitModelId
            ?: observation.displayName?.let(::modelIdFromDisplayName)
            ?: return null
        val provider = providerNamespaceFromModelId(observedModelId)
            ?: providerNamespace(observation.providerVendor)
            ?: return null
        val canonicalLeaf = normalizeProviderModelId(observedModelId) ?: return null
        val canonicalModelId = "$provider/$canonicalLeaf"
        return ResolvedModelIdentity(
            status = ModelIdentityStatus.RESOLVED,
            canonicalModelId = canonicalModelId,
            runtimeModelId = observation.runtimeModelId,
            providerConfigId = observation.providerConfigId,
            providerModelId = observation.providerModelId,
            responseModelId = observation.responseModelId,
            displayName = canonicalDisplayName(
                observation.displayName
                    ?: observation.responseModelId
                    ?: observation.providerModelId
                    ?: canonicalLeaf
            ),
            pricingModelIds = listOf(canonicalModelId),
            evidence = (evidence + ModelIdentityEvidence(
                if (explicitModelId != null) {
                    ModelIdentitySource.PROVIDER_RESPONSE
                } else {
                    ModelIdentitySource.UNKNOWN
                },
                observedModelId
            )).distinct()
        )
    }

    private fun modelIdFromDisplayName(value: String): String? = ModelIdentity
        .stripDisplayLevelSuffix(value)
        .trim()
        .lowercase()
        .replace('.', '-')
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .takeIf(String::isNotEmpty)

    private fun canonicalDisplayName(value: String): String {
        val cleaned = ModelIdentity.stripDisplayLevelSuffix(value)
            .replace(Regex("(?i)^models/"), "")
            .replace(INTERNAL_RESPONSE_SUFFIX, "")
        val parts = cleaned.split('-', '_', ' ').filter(String::isNotBlank)
        val formatted = parts.map { part ->
            when {
                part.equals("gpt", ignoreCase = true) -> "GPT"
                part.equals("claude", ignoreCase = true) -> "Claude"
                part.equals("gemini", ignoreCase = true) -> "Gemini"
                part.equals("grok", ignoreCase = true) -> "Grok"
                part.matches(Regex("\\d+(?:\\.\\d+)?")) -> part
                else -> part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
        }
        return formatted.joinToString(" ").replace(Regex("(?<=\\d) (?=\\d)"), ".")
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
        val providerVendor: String?,
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
                    providerVendor = binding.providerVendor,
                    displayName = variant.displayName,
                    reasoningProfile = variant.reasoningProfile
                )
            }

            officialModels.forEach { model ->
                val canonicalModelId = officialCanonicalModelId(model)
                val bindingKey = canonicalModelId
                    ?: model.tierGroupIds.sorted().firstOrNull()?.let { "tier:$it" }
                    ?: "catalog:${model.catalogModelId}"
                records += IdentityRecord(
                    canonicalModelId = canonicalModelId,
                    baseModelId = model.baseModelId,
                    bindingId = "official:$bindingKey",
                    variantId = model.catalogModelId,
                    catalogModelId = model.catalogModelId,
                    runtimeModelId = model.runtimeModelId,
                    providerConfigId = OFFICIAL_PROVIDER_CONFIG_ID,
                    providerModelId = model.providerModelId,
                    providerVendor = model.providerVendor,
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
            records.forEach { record ->
                normalized(record.canonicalModelId)?.let { canonicalId ->
                    unscoped += canonicalId to record
                    canonicalId.substringAfterLast('/')
                        .takeIf { leaf -> leaf != canonicalId && leaf.isNotEmpty() }
                        ?.let { leaf -> unscoped += leaf to record }
                }
            }

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
        private val INTERNAL_RESPONSE_SUFFIX = Regex("-(?:control|safety(?:-[a-z0-9]+)*|exp(?:-[a-z0-9]+)*)$")

        private fun officialCanonicalModelId(model: OfficialCatalogModel): String? {
            model.canonicalModelId?.trim()?.takeIf(String::isNotEmpty)?.let { canonicalModelId ->
                providerCanonicalModelId(model.providerVendor, canonicalModelId)?.let { return it }
            }
            model.providerModelId?.let { providerModelId ->
                providerCanonicalModelId(model.providerVendor, providerModelId)?.let { return it }
            }
            val normalizedCatalogId = ModelIdentity.normalizeModelId(model.catalogModelId)
            val familyBase = ModelIdentity.modelFamilyBase(normalizedCatalogId)
            if (familyBase == normalizedCatalogId) return null
            return providerCanonicalModelId(
                providerVendor = model.providerVendor,
                providerModelId = familyBase
            )
        }

        private fun providerCanonicalModelId(providerVendor: String?, providerModelId: String?): String? {
            val provider = providerNamespace(providerVendor) ?: return null
            val normalizedModelId = normalized(providerModelId) ?: return null
            if (normalizedModelId.contains('/')) return normalizedModelId
            val model = normalizeProviderModelId(normalizedModelId) ?: return null
            return "$provider/$model"
        }

        private fun normalizeProviderModelId(value: String): String? {
            val normalized = normalized(value)
                ?.takeUnless { modelId ->
                    modelId.startsWith("model_placeholder_") || modelId.startsWith("model_chat_")
                }
                ?: return null
            val deploymentNormalized = when {
                normalized.endsWith("@default") -> normalized.removeSuffix("@default")
                '@' in normalized -> normalized.replace('@', '-')
                else -> normalized
            }
            val base = ModelIdentity.modelFamilyBase(
                deploymentNormalized
                    .replace(Regex("^gemini-(\\d+)p(\\d+)-"), "gemini-$1.$2-")
                    .replace(INTERNAL_RESPONSE_SUFFIX, "")
            )
            return if (!base.startsWith("claude-")) {
                base.replace(Regex("(?<=\\d)-(?=\\d)"), ".")
            } else {
                base
            }
        }

        private fun providerNamespaceFromModelId(modelId: String): String? {
            val model = normalized(modelId) ?: return null
            return when {
                model.startsWith("gemini-") -> "google"
                model.startsWith("gpt-") || model.startsWith("o1-") || model.startsWith("o3-") -> "openai"
                model.startsWith("claude-") -> "anthropic"
                model.startsWith("grok-") -> "xai"
                else -> null
            }
        }

        private fun providerNamespace(value: String?): String? {
            val normalized = value
                ?.trim()
                ?.lowercase()
                ?.replace('_', '-')
                ?.takeIf(String::isNotEmpty)
                ?: return null
            return when {
                normalized == "api-provider-google-gemini" || normalized == "google-gemini" -> "google"
                normalized.startsWith("model-provider-") -> normalized
                    .removePrefix("model-provider-")
                    .takeIf(String::isNotEmpty)

                normalized.startsWith("api-provider-") -> null
                else -> normalized
            }
        }

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
