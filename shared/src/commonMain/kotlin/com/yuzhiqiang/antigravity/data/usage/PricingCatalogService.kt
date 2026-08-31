package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

enum class PricingSource(val id: String) {
    CUSTOM("custom"),
    EXTERNAL("external"),
    BUILTIN("builtin"),
    UNMATCHED("unmatched"),
    MIXED("mixed")
}

enum class PricingConfidence {
    HIGH,
    LOW
}

/** 价格解析结果：费率本身之外，还保留来源与匹配可信度供展示层使用。 */
data class PricingResolution(
    val rate: ModelPricingRate,
    val source: PricingSource,
    val confidence: PricingConfidence,
    val matched: Boolean
)

/** 单个价格档位，单位：美元 / 1,000,000 Tokens。 */
@Serializable
data class LongContextPricingRate(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val reasoning: Double = 0.0
)

@Serializable
data class ModelPricingRate(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val reasoning: Double = 0.0,
    val above272k: LongContextPricingRate? = null
)

data class CostCalculationResult(
    val costUsd: Double,
    val savingsUsd: Double,
    val pricingSource: PricingSource = PricingSource.UNMATCHED,
    val pricingMatched: Boolean = false,
    val pricingConfidence: PricingConfidence = PricingConfidence.LOW,
    val lowerBound: Boolean = false,
    val usedLongContextPricing: Boolean = false
)

/**
 * LiteLLM 动态价格目录服务。
 *
 * 价格只按真实模型身份做精确匹配，不根据模型族猜价；调用方应优先传入
 * metadata 中的 modelPricingIds/canonicalId，展示名仅作为最后的兼容候选。
 */
class PricingCatalogService(
    private val httpClient: HttpClient? = null,
    private val customRootDir: File? = null
) {
    companion object {
        const val DEFAULT_PRICING_URL = "https://models.dev/api.json"
        const val LONG_CONTEXT_THRESHOLD_TOKENS = 272_000L
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val rootDir: File by lazy { customRootDir ?: AppDataPaths.rootDir() }
    private val cacheFile: File by lazy { File(rootDir, "pricing_catalog.json") }

    // 动态目录与自定义目录分开保存，准确报告 pricing source。
    private var pricingCatalog: Map<String, ModelPricingRate> = emptyMap()
    private var pricingUnqualified: Map<String, ModelPricingRate> = emptyMap()
    private var customOverrides: Map<String, ModelPricingRate> = emptyMap()
    private var customUnqualified: Map<String, ModelPricingRate> = emptyMap()
    private var lastFetchedAt: Long = 0L

    init {
        loadFromLocalCache()
    }

    suspend fun setCustomPricingSource(customPath: String?) = withContext(Dispatchers.IO) {
        customOverrides = emptyMap()
        customUnqualified = emptyMap()
        val path = customPath?.trim().orEmpty()
        if (path.isBlank()) return@withContext

        try {
            val file = File(path)
            if (file.isFile) {
                customOverrides = parsePricingJson(file.readText(Charsets.UTF_8))
                customUnqualified = buildUnqualifiedIndex(customOverrides)
            }
        } catch (_: Exception) {
            customOverrides = emptyMap()
        }
    }

    suspend fun refreshCatalog(force: Boolean = false, remoteUrl: String = DEFAULT_PRICING_URL): Result<Unit> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && (now - lastFetchedAt) < CACHE_TTL_MS && pricingCatalog.isNotEmpty()) {
                return@withContext Result.success(Unit)
            }

            val client = httpClient ?: HttpClient()
            val ownsClient = httpClient == null
            try {
                val response = client.get(remoteUrl)
                if (response.status.value !in 200..299) {
                    throw IllegalStateException("Pricing catalog HTTP ${response.status.value}")
                }
                val body = response.bodyAsText()
                val parsed = parsePricingJson(body)
                if (parsed.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Parsed pricing catalog is empty"))
                }
                pricingCatalog = parsed
                pricingUnqualified = buildUnqualifiedIndex(parsed)
                lastFetchedAt = now
                saveToLocalCache(body)
                Result.success(Unit)
            } catch (error: Exception) {
                loadFromLocalCache()
                Result.failure(error)
            } finally {
                if (ownsClient) client.close()
            }
        }

    /**
     * 按优先级解析费率：自定义 > 远端动态目录 (models.dev) > 未匹配零费率。
     * 不维护内置硬编码价格，避免由于版本变动展示错误价格。
     */
    fun resolvePricing(
        modelId: String,
        displayName: String? = null,
        modelPricingIds: List<String> = emptyList(),
        modelCanonicalId: String? = null
    ): PricingResolution {
        val candidates = generateLookupCandidates(
            *modelPricingIds.toTypedArray(),
            modelCanonicalId,
            modelId,
            displayName
        ).filterNot(UsageModelIdentityResolver::isOpaqueModelReference)

        // 1. 用户自定义配置
        for (candidate in candidates) {
            customOverrides[candidate]?.let {
                return PricingResolution(it, PricingSource.CUSTOM, PricingConfidence.HIGH, matched = true)
            }
            unqualifiedCandidate(candidate)?.let {
                customUnqualified[it]?.let { rate ->
                    return PricingResolution(rate, PricingSource.CUSTOM, PricingConfidence.HIGH, matched = true)
                }
            }
        }

        // 2. 远端动态价格大盘（models.dev）
        for (candidate in candidates) {
            pricingCatalog[candidate]?.let {
                return PricingResolution(it, PricingSource.EXTERNAL, PricingConfidence.HIGH, matched = true)
            }
            unqualifiedCandidate(candidate)?.let {
                pricingUnqualified[it]?.let { rate ->
                    return PricingResolution(rate, PricingSource.EXTERNAL, PricingConfidence.HIGH, matched = true)
                }
            }
        }

        // 3. 未匹配到任何价格，返回未匹配零费率
        return PricingResolution(
            rate = ModelPricingRate(),
            source = PricingSource.UNMATCHED,
            confidence = PricingConfidence.LOW,
            matched = false
        )
    }

    /** 兼容旧调用方；需要展示计费来源时请使用 resolvePricing。 */
    fun resolveRate(
        modelId: String,
        displayName: String? = null,
        modelPricingIds: List<String> = emptyList(),
        modelCanonicalId: String? = null
    ): ModelPricingRate = resolvePricing(modelId, displayName, modelPricingIds, modelCanonicalId).rate

    fun calculateCostAndSavings(
        input: Long,
        output: Long,
        cacheRead: Long,
        cacheWrite: Long,
        reasoning: Long,
        modelId: String,
        displayName: String? = null,
        modelPricingIds: List<String> = emptyList(),
        modelCanonicalId: String? = null,
        missingUsageFields: Collection<String> = emptyList()
    ): CostCalculationResult {
        val pricing = resolvePricing(modelId, displayName, modelPricingIds, modelCanonicalId)
        val rate = pricing.rate
        val useLongContextPricing = input > LONG_CONTEXT_THRESHOLD_TOKENS && rate.above272k != null
        val effectiveRate = if (useLongContextPricing) {
            rate.above272k?.toModelRate() ?: rate
        } else {
            rate
        }
        val safeInput = input.coerceAtLeast(0L)
        val safeOutput = output.coerceAtLeast(0L)
        val safeCacheRead = cacheRead.coerceAtLeast(0L)
        val safeCacheWrite = cacheWrite.coerceAtLeast(0L)
        val safeReasoning = reasoning.coerceAtLeast(0L)
        val cost = (
                safeInput * effectiveRate.input +
                        safeOutput * effectiveRate.output +
                        safeCacheRead * effectiveRate.cacheRead +
                        safeCacheWrite * effectiveRate.cacheWrite +
                        safeReasoning * effectiveRate.reasoning
                ) / 1_000_000.0
        val savings = if (effectiveRate.input > effectiveRate.cacheRead && safeCacheRead > 0L) {
            safeCacheRead * (effectiveRate.input - effectiveRate.cacheRead) / 1_000_000.0
        } else {
            0.0
        }
        return CostCalculationResult(
            costUsd = maxOf(0.0, cost),
            savingsUsd = maxOf(0.0, savings),
            pricingSource = pricing.source,
            pricingMatched = pricing.matched,
            pricingConfidence = pricing.confidence,
            lowerBound = missingUsageFields.isNotEmpty(),
            usedLongContextPricing = useLongContextPricing
        )
    }

    internal fun generateLookupCandidates(vararg inputs: String?): List<String> {
        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun add(value: String?) {
            val candidate = value?.trim()?.lowercase().orEmpty()
            if (candidate.isNotEmpty() && seen.add(candidate)) results += candidate
        }

        for (input in inputs) {
            val raw = input?.trim()?.lowercase().orEmpty()
            if (raw.isEmpty()) continue
            add(raw)
            val slug = raw
                .replace("（", "(")
                .replace("）", ")")
                .replace(Regex("[\\s_]+"), "-")
                .replace(Regex("-+"), "-")
            add(slug)

            // 剥离日期后缀（如 -20250219, -20241022, -2024-08-06）
            val strippedDate = slug.replace(Regex("[-_]20\\d{2}[-_]?\\d{2}[-_]?\\d{2}$"), "")
            if (strippedDate.isNotEmpty() && strippedDate != slug) {
                add(strippedDate)
            }

            // 剥离版本与实验修饰后缀（如 -latest, -preview, -exp）
            val strippedVariant = (if (strippedDate.isNotEmpty()) strippedDate else slug)
                .replace(Regex("[-_](latest|preview|exp|experiment|online|chat|instruct)$"), "")
            if (strippedVariant.isNotEmpty()) {
                add(strippedVariant)
            }
        }
        return results
    }

    private fun parsePricingJson(rawJson: String): Map<String, ModelPricingRate> {
        val result = mutableMapOf<String, ModelPricingRate>()
        try {
            val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return result

            fun processModelEntry(key: String, objectValue: JsonObject, providerPrefix: String? = null) {
                val mode = stringValue(objectValue, "mode")
                if (mode != null && mode != "chat" && mode != "completion") return

                // 1. 优先解析 models.dev 标准的 cost 对象（以百万 Token 美元为单位）
                val costObj = objectValue["cost"] as? JsonObject
                val rate: ModelPricingRate? = if (costObj != null) {
                    val input = numberValue(costObj, "input") ?: 0.0
                    val output = numberValue(costObj, "output") ?: 0.0
                    if (input > 0.0 || output > 0.0) {
                        val cacheRead = numberValue(costObj, "cache_read") ?: (input * 0.1)
                        val cacheWrite = numberValue(costObj, "cache_write") ?: cacheRead
                        val reasoning = numberValue(costObj, "reasoning") ?: output
                        ModelPricingRate(
                            input = input,
                            output = output,
                            cacheRead = cacheRead,
                            cacheWrite = cacheWrite,
                            reasoning = reasoning,
                            above272k = parseLongContextRateFromCostObj(costObj, input, output, cacheRead, cacheWrite, reasoning)
                        )
                    } else null
                } else {
                    // 2. 兼容扁平结构 / LiteLLM 逐 Token 格式
                    val inputToken = numberValue(objectValue, "input_cost_per_token", "inputCostPerToken")
                    val outputToken = numberValue(objectValue, "output_cost_per_token", "outputCostPerToken")
                    val inputM = numberValue(objectValue, "input")
                    val outputM = numberValue(objectValue, "output")
                    if (inputM != null || outputM != null) {
                        val input = inputM ?: 0.0
                        val output = outputM ?: 0.0
                        val cacheRead = numberValue(objectValue, "cache_read", "cache") ?: (input * 0.1)
                        val cacheWrite = numberValue(objectValue, "cache_write", "cacheWrite") ?: cacheRead
                        val reasoning = numberValue(objectValue, "reasoning") ?: output
                        ModelPricingRate(
                            input = input,
                            output = output,
                            cacheRead = cacheRead,
                            cacheWrite = cacheWrite,
                            reasoning = reasoning
                        )
                    } else if (inputToken != null || outputToken != null) {
                        val input = (inputToken ?: 0.0) * 1_000_000.0
                        val output = (outputToken ?: 0.0) * 1_000_000.0
                        val cacheRead = numberValue(objectValue, "cache_read_input_token_cost", "cacheReadCostPerToken")?.let { it * 1_000_000.0 } ?: (input * 0.1)
                        val cacheWrite = numberValue(objectValue, "cache_creation_input_token_cost", "cacheWriteCostPerToken")?.let { it * 1_000_000.0 } ?: cacheRead
                        val reasoning = numberValue(objectValue, "output_cost_per_reasoning_token", "reasoning_cost_per_token")?.let { it * 1_000_000.0 } ?: output
                        ModelPricingRate(
                            input = input,
                            output = output,
                            cacheRead = cacheRead,
                            cacheWrite = cacheWrite,
                            reasoning = reasoning,
                            above272k = parseLongContextRate(objectValue, input / 1_000_000.0, output / 1_000_000.0, cacheRead / 1_000_000.0, cacheWrite / 1_000_000.0, reasoning / 1_000_000.0)
                        )
                    } else null
                }

                if (rate != null) {
                    val fullKey = if (!providerPrefix.isNullOrBlank()) "$providerPrefix/$key" else key
                    val normalizedFull = fullKey.trim().lowercase()
                    result[normalizedFull] = rate
                    for (candidate in generateLookupCandidates(normalizedFull)) {
                        result.putIfAbsent(candidate, rate)
                    }
                    if (providerPrefix == null) {
                        val shortKey = key.trim().lowercase()
                        result.putIfAbsent(shortKey, rate)
                        for (candidate in generateLookupCandidates(shortKey)) {
                            result.putIfAbsent(candidate, rate)
                        }
                    }
                }
            }

            for ((topKey, topValue) in root) {
                val objectValue = topValue as? JsonObject ?: continue
                val modelsObj = objectValue["models"] as? JsonObject
                if (modelsObj != null) {
                    val providerId = stringValue(objectValue, "id") ?: topKey
                    for ((modelKey, modelValue) in modelsObj) {
                        val modelSpec = modelValue as? JsonObject ?: continue
                        processModelEntry(modelKey, modelSpec, providerId)
                    }
                } else {
                    processModelEntry(topKey, objectValue)
                }
            }
        } catch (_: Exception) {
            // 解析失败不影响现有目录
        }
        return result
    }

    private fun parseLongContextRateFromCostObj(
        costObj: JsonObject,
        baseInput: Double,
        baseOutput: Double,
        baseCacheRead: Double,
        baseCacheWrite: Double,
        baseReasoning: Double
    ): LongContextPricingRate? {
        val over200k = costObj["context_over_200k"] as? JsonObject
        if (over200k != null) {
            val input = numberValue(over200k, "input") ?: baseInput
            val output = numberValue(over200k, "output") ?: baseOutput
            val cacheRead = numberValue(over200k, "cache_read") ?: baseCacheRead
            val cacheWrite = numberValue(over200k, "cache_write") ?: baseCacheWrite
            val reasoning = numberValue(over200k, "reasoning") ?: baseReasoning
            return LongContextPricingRate(input, output, cacheRead, cacheWrite, reasoning)
        }
        return null
    }

    private fun parseLongContextRate(
        objectValue: JsonObject,
        input: Double,
        output: Double,
        cacheRead: Double?,
        cacheWrite: Double?,
        reasoning: Double?
    ): LongContextPricingRate? {
        val longInput = numberValue(
            objectValue,
            "input_cost_per_token_above_272k_tokens",
            "inputCostPerTokenAbove272k"
        )
        val longOutput = numberValue(
            objectValue,
            "output_cost_per_token_above_272k_tokens",
            "outputCostPerTokenAbove272k"
        )
        val longCacheRead = numberValue(
            objectValue,
            "cache_read_input_token_cost_above_272k_tokens",
            "cacheReadCostPerTokenAbove272k"
        )
        val longCacheWrite = numberValue(
            objectValue,
            "cache_creation_input_token_cost_above_272k_tokens",
            "cacheWriteCostPerTokenAbove272k"
        )
        val longReasoning = numberValue(
            objectValue,
            "output_cost_per_reasoning_token_above_272k_tokens",
            "reasoningCostPerTokenAbove272k"
        )
        val hasLongContext = listOf(longInput, longOutput, longCacheRead, longCacheWrite, longReasoning)
            .any { it != null }
        if (!hasLongContext) return null

        val effectiveInput = longInput ?: input
        val effectiveOutput = longOutput ?: output
        val effectiveCacheRead = longCacheRead ?: cacheRead ?: input * 0.1
        val effectiveCacheWrite = longCacheWrite ?: cacheWrite ?: longCacheRead ?: cacheRead ?: effectiveInput
        val effectiveReasoning = longReasoning ?: reasoning ?: effectiveOutput
        return LongContextPricingRate(
            input = effectiveInput * 1_000_000.0,
            output = effectiveOutput * 1_000_000.0,
            cacheRead = effectiveCacheRead * 1_000_000.0,
            cacheWrite = effectiveCacheWrite * 1_000_000.0,
            reasoning = effectiveReasoning * 1_000_000.0
        )
    }

    private fun numberValue(objectValue: JsonObject, vararg keys: String): Double? {
        for (key in keys) {
            val raw = objectValue[key]?.jsonPrimitive?.contentOrNull ?: continue
            val number = raw.toDoubleOrNull()
            if (number != null && number.isFinite() && number >= 0.0) return number
        }
        return null
    }

    private fun stringValue(objectValue: JsonObject, key: String): String? =
        objectValue[key]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun loadFromLocalCache() {
        try {
            if (!cacheFile.isFile) return
            val parsed = parsePricingJson(cacheFile.readText(Charsets.UTF_8))
            if (parsed.isNotEmpty()) {
                pricingCatalog = parsed
                pricingUnqualified = buildUnqualifiedIndex(parsed)
                lastFetchedAt = cacheFile.lastModified()
            }
        } catch (_: Exception) {
            // 保留内置离线价格。
        }
    }

    private fun unqualifiedCandidate(candidate: String): String? =
        candidate.substringAfterLast('/', missingDelimiterValue = candidate)
            .takeIf { it != candidate }

    /** 仅保留目录中唯一的无 provider 前缀别名；同名不同价时保持未匹配。 */
    private fun buildUnqualifiedIndex(catalog: Map<String, ModelPricingRate>): Map<String, ModelPricingRate> {
        val owners = mutableMapOf<String, ModelPricingRate?>()
        val ambiguous = mutableSetOf<String>()
        for ((key, rate) in catalog) {
            val shortKey = key.substringAfterLast('/')
            if (shortKey.isBlank() || shortKey in ambiguous) continue
            val previous = owners[shortKey]
            if (!owners.containsKey(shortKey)) {
                owners[shortKey] = rate
            } else if (previous != rate) {
                owners.remove(shortKey)
                ambiguous += shortKey
            }
        }
        return owners.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    }

    private fun saveToLocalCache(content: String) {
        try {
            AtomicFileWriter.writeText(
                target = cacheFile,
                content = content,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY
            )
        } catch (_: Exception) {
            // 网络结果仍可在本次进程内使用。
        }
    }

    private fun LongContextPricingRate.toModelRate(): ModelPricingRate = ModelPricingRate(
        input = input,
        output = output,
        cacheRead = cacheRead,
        cacheWrite = cacheWrite,
        reasoning = reasoning
    )
}
