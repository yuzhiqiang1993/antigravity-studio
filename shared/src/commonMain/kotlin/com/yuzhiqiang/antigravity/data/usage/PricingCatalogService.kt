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
import kotlinx.serialization.json.JsonArray
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
    val reasoning: Double = 0.0,
    val thresholdTokens: Long = 272_000L
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
 * 价格只按 registry 解析出的 canonical ID 与已注册 pricing ID 做精确匹配，
 * display、日期、variant、provider 补全与 slug 都不参与查询。
 */
class PricingCatalogService(
    private val httpClient: HttpClient? = null,
    private val customRootDir: File? = null
) {
    companion object {
        const val DEFAULT_PRICING_URL = "https://models.dev/api.json"
        const val LONG_CONTEXT_THRESHOLD_TOKENS = 272_000L
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        // 官方第一方与主流公有云提供商白名单
        private val OFFICIAL_PROVIDER_IDS = setOf(
            "openai", "anthropic", "google", "google-vertex", "google-vertex-anthropic",
            "azure", "azure-cognitive-services", "amazon-bedrock", "deepseek", "mistral",
            "meta", "xai", "cohere", "groq", "togetherai", "deepinfra", "cerebras",
            "perplexity", "perplexity-agent", "minimax", "minimax-cn", "minimax-coding-plan",
            "minimax-cn-coding-plan", "kimi-for-coding", "v0", "vercel", "cloudflare-workers-ai"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val rootDir: File by lazy { customRootDir ?: AppDataPaths.rootDir() }
    private val cacheFile: File by lazy { File(rootDir, "pricing_catalog.json") }

    // 动态目录与自定义目录分开保存，准确报告 pricing source。
    private var pricingCatalog: Map<String, ModelPricingRate> = emptyMap()
    private var customOverrides: Map<String, ModelPricingRate> = emptyMap()

    private var lastFetchedAt: Long = 0L

    init {
        loadFromLocalCache()
    }

    suspend fun setCustomPricingSource(customPath: String?) = withContext(Dispatchers.IO) {
        customOverrides = emptyMap()
        val path = customPath?.trim().orEmpty()
        if (path.isBlank()) return@withContext

        try {
            val file = File(path)
            if (file.isFile) {
                customOverrides = parsePricingJson(file.readText(Charsets.UTF_8), isCustomFile = true)
            }
        } catch (_: Exception) {
            customOverrides = emptyMap()
        }
    }

    suspend fun refreshCatalog(force: Boolean = false, remoteUrl: String = DEFAULT_PRICING_URL): Result<Unit> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && (now - lastFetchedAt) < CACHE_TTL_MS && pricingCatalog.isNotEmpty()) {
                com.yuzhiqiang.antigravity.logging.AppLog.d("Usage/Pricing") { "命中内存价格目录缓存，跳过刷新 (已缓存模型数=${pricingCatalog.size})" }
                return@withContext Result.success(Unit)
            }

            com.yuzhiqiang.antigravity.logging.AppLog.d("Usage/Pricing") { "开始从远端拉取最新价格目录: $remoteUrl (force=$force)" }
            val client = httpClient ?: HttpClient {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 4000L
                    connectTimeoutMillis = 3000L
                    socketTimeoutMillis = 4000L
                }
            }
            val ownsClient = httpClient == null
            try {
                kotlinx.coroutines.withTimeout(5000L) {
                    val response = client.get(remoteUrl)
                    if (response.status.value !in 200..299) {
                        throw IllegalStateException("Pricing catalog HTTP ${response.status.value}")
                    }
                    val body = response.bodyAsText()
                    val parsed = parsePricingJson(body, isCustomFile = false)
                    if (parsed.isEmpty()) {
                        throw IllegalStateException("Parsed pricing catalog is empty")
                    }
                    pricingCatalog = parsed
                    lastFetchedAt = now
                    saveToLocalCache(body)
                    com.yuzhiqiang.antigravity.logging.AppLog.i("Usage/Pricing") { "远端官方价格目录拉取成功并已更新本地缓存: 官方模型数=${parsed.size}, 耗时=${System.currentTimeMillis() - now}ms" }
                }
                Result.success(Unit)
            } catch (error: Exception) {
                com.yuzhiqiang.antigravity.logging.AppLog.w(
                    "Usage/Pricing",
                    error
                ) { "远端价格目录更新失败/超时，安全回退至本地磁盘缓存快照: ${error.message}" }
                loadFromLocalCache()
                Result.failure(error)
            } finally {
                if (ownsClient) client.close()
            }
        }

    /**
     * 按优先级解析费率：自定义 > 远端动态目录 (models.dev 官方数据及其本地快照) > 未匹配零费率。
     * 永远只使用远端官方真实数据，绝不硬编码假数据。
     */
    fun resolvePricing(
        canonicalModelId: String?,
        registeredPricingIds: List<String> = emptyList()
    ): PricingResolution {
        val candidates = generateLookupCandidates(
            canonicalModelId,
            *registeredPricingIds.toTypedArray()
        )

        // 1. 用户自定义配置
        for (candidate in candidates) {
            customOverrides[candidate]?.let {
                return PricingResolution(it, PricingSource.CUSTOM, PricingConfidence.HIGH, matched = true)
            }
        }

        // 2. 远端动态价格大盘（models.dev 官方权威数据及其本地磁盘缓存快照）
        for (candidate in candidates) {
            pricingCatalog[candidate]?.let {
                return PricingResolution(it, PricingSource.EXTERNAL, PricingConfidence.HIGH, matched = true)
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

    /** 需要展示计费来源时请使用 [resolvePricing]。 */
    fun resolveRate(
        canonicalModelId: String?,
        registeredPricingIds: List<String> = emptyList()
    ): ModelPricingRate = resolvePricing(canonicalModelId, registeredPricingIds).rate

    fun calculateCostAndSavings(
        input: Long,
        output: Long,
        cacheRead: Long,
        cacheWrite: Long,
        reasoning: Long,
        canonicalModelId: String?,
        registeredPricingIds: List<String> = emptyList(),
        missingUsageFields: Collection<String> = emptyList(),
        unattributed: Long = 0L
    ): CostCalculationResult {
        val pricing = resolvePricing(canonicalModelId, registeredPricingIds)
        val rate = pricing.rate
        val safeInput = input.coerceAtLeast(0L)
        val safeOutput = output.coerceAtLeast(0L)
        val safeCacheRead = cacheRead.coerceAtLeast(0L)
        val safeCacheWrite = cacheWrite.coerceAtLeast(0L)
        val safeReasoning = reasoning.coerceAtLeast(0L)
        val promptTokens = safeInput + safeCacheRead + safeCacheWrite
        val longContextRate = rate.above272k
        val useLongContextPricing = longContextRate != null && promptTokens > longContextRate.thresholdTokens
        val effectiveRate = if (useLongContextPricing) {
            longContextRate?.toModelRate() ?: rate
        } else {
            rate
        }
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
            lowerBound = missingUsageFields.isNotEmpty() || unattributed > 0L,
            usedLongContextPricing = useLongContextPricing
        )
    }

    internal fun generateLookupCandidates(vararg inputs: String?): List<String> = buildList {
        for (input in inputs) {
            val raw = input?.trim()?.takeIf(String::isNotEmpty) ?: continue
            val lower = raw.lowercase()
            add(lower)

            val withoutModels = lower.removePrefix("models/").removePrefix("models-").trim()
            if (withoutModels.isNotEmpty() && withoutModels != lower) {
                add(withoutModels)
            }

            // 1. 符号连字符归一化
            val hyphenated = withoutModels.replace(Regex("[\\s_]+"), "-").replace(Regex("-+"), "-").trim('-')
            if (hyphenated.isNotEmpty()) {
                add(hyphenated)
            }

            // 2. 剥离括号与档位修饰词 (如 (High), (Thinking), (X-High), -high, -tiered 等)
            val withoutParentheses = withoutModels.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
            val cleanTier = stripTierSuffixes(withoutParentheses)
            val cleanHyphenated = cleanTier.replace(Regex("[\\s_]+"), "-").replace(Regex("-+"), "-").trim('-')
            if (cleanHyphenated.isNotEmpty()) {
                add(cleanHyphenated)
            }

            // 3. 厂商前缀剥离与扩展及版本点号连字符互转
            val baseCandidates = mutableListOf<String>()
            if (hyphenated.isNotEmpty()) baseCandidates.add(hyphenated)
            if (cleanHyphenated.isNotEmpty()) baseCandidates.add(cleanHyphenated)
            
            // 兼容点号与连字符互转 (例如 claude 3.5 sonnet -> claude-3-5-sonnet 或 gemini-3-7-flash -> gemini-3.7-flash)
            val dotToHyphen = cleanHyphenated.replace('.', '-')
            if (dotToHyphen.isNotEmpty()) baseCandidates.add(dotToHyphen)
            val hyphenToDot = cleanHyphenated.replace(Regex("(?<=\\d)-(?=\\d)"), ".")
            if (hyphenToDot.isNotEmpty()) baseCandidates.add(hyphenToDot)

            val modelsToExpand = baseCandidates.distinct()

            for (item in modelsToExpand) {
                if (item.contains('/')) {
                    val leaf = item.substringAfterLast('/')
                    if (leaf.isNotEmpty()) add(leaf)
                } else {
                    for (vendor in KNOWN_VENDOR_PREFIXES) {
                        add("$vendor/$item")
                    }
                }

                // 4. 日期版本快照通配 (例如 claude-3-5-sonnet-20241022 -> claude-3-5-sonnet)
                val withoutDate = stripDateSnapshotSuffix(item)
                if (withoutDate != null && withoutDate != item) {
                    add(withoutDate)
                    if (!withoutDate.contains('/')) {
                        for (vendor in KNOWN_VENDOR_PREFIXES) {
                            add("$vendor/$withoutDate")
                        }
                    }
                }
            }
        }
    }.distinct()

    private fun stripTierSuffixes(text: String): String {
        var result = text.trim()
        val suffixes = listOf(
            "-adaptive", "-x-high", "-x_high", "-xhigh", "-medium", "-standard",
            "-auto", "-high", "-max", "-low", "-minimal", "-thinking", "-direct",
            "-tiered", "-preview", "-latest"
        )
        for (suffix in suffixes) {
            if (result.endsWith(suffix, ignoreCase = true)) {
                result = result.substring(0, result.length - suffix.length).trimEnd('-', '_', ' ')
                break
            }
        }
        return result
    }

    private fun stripDateSnapshotSuffix(text: String): String? {
        val dateRegex = Regex("-(?:\\d{4}-\\d{2}-\\d{2}|\\d{8})$")
        return if (dateRegex.containsMatchIn(text)) {
            text.replace(dateRegex, "")
        } else {
            null
        }
    }

    private val KNOWN_VENDOR_PREFIXES = listOf(
        "google", "anthropic", "openai", "deepseek", "meta", "mistral", "xai", "cohere", "minimax"
    )

    private fun parsePricingJson(rawJson: String, isCustomFile: Boolean = false): Map<String, ModelPricingRate> {
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
                            above272k = parseLongContextRateFromCostObj(
                                costObj,
                                input,
                                output,
                                cacheRead,
                                cacheWrite,
                                reasoning
                            )
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
                        val cacheRead = numberValue(
                            objectValue,
                            "cache_read_input_token_cost",
                            "cacheReadCostPerToken"
                        )?.let { it * 1_000_000.0 } ?: (input * 0.1)
                        val cacheWrite = numberValue(
                            objectValue,
                            "cache_creation_input_token_cost",
                            "cacheWriteCostPerToken"
                        )?.let { it * 1_000_000.0 } ?: cacheRead
                        val reasoning = numberValue(
                            objectValue,
                            "output_cost_per_reasoning_token",
                            "reasoning_cost_per_token"
                        )?.let { it * 1_000_000.0 } ?: output
                        ModelPricingRate(
                            input = input,
                            output = output,
                            cacheRead = cacheRead,
                            cacheWrite = cacheWrite,
                            reasoning = reasoning,
                            above272k = parseLongContextRate(
                                objectValue,
                                input / 1_000_000.0,
                                output / 1_000_000.0,
                                cacheRead / 1_000_000.0,
                                cacheWrite / 1_000_000.0,
                                reasoning / 1_000_000.0
                            )
                        )
                    } else null
                }

                if (rate != null) {
                    val fullKey = if (!providerPrefix.isNullOrBlank()) "$providerPrefix/$key" else key
                    val normalizedKey = fullKey.trim().lowercase().removePrefix("models/")
                    result[normalizedKey] = rate
                    if (!providerPrefix.isNullOrBlank()) {
                        val leafKey = key.trim().lowercase().removePrefix("models/")
                        result.putIfAbsent(leafKey, rate)
                    }
                }
            }

            for ((topKey, topValue) in root) {
                val objectValue = topValue as? JsonObject ?: continue
                val npm = stringValue(objectValue, "npm").orEmpty()
                val isOfficialProvider = topKey.lowercase() in OFFICIAL_PROVIDER_IDS || (
                        npm.startsWith("@ai-sdk/") && !npm.contains("compatible") && !npm.contains("openrouter")
                        )

                // 核心规则：仅保留官方 Provider 数据，第三方聚合/中继商数据彻底丢弃
                if (!isCustomFile && !isOfficialProvider && customRootDir == null) {
                    continue
                }

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
        val contextTier = (costObj["tiers"] as? JsonArray)
            ?.asSequence()
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { candidate ->
                val tier = candidate["tier"] as? JsonObject ?: return@firstOrNull false
                stringValue(tier, "type")?.equals("context", ignoreCase = true) == true &&
                        (numberValue(tier, "size") ?: 0.0) >= 200_000.0
            }
        if (contextTier != null) {
            val tier = contextTier["tier"] as? JsonObject
            val thresholdTokens = tier?.let { numberValue(it, "size") }?.toLong()
                ?: LONG_CONTEXT_THRESHOLD_TOKENS
            val input = numberValue(contextTier, "input") ?: baseInput
            val output = numberValue(contextTier, "output") ?: baseOutput
            val cacheRead = numberValue(contextTier, "cache_read") ?: baseCacheRead
            val cacheWrite = numberValue(contextTier, "cache_write") ?: baseCacheWrite
            val reasoning = numberValue(contextTier, "reasoning") ?: baseReasoning
            return LongContextPricingRate(
                input = input,
                output = output,
                cacheRead = cacheRead,
                cacheWrite = cacheWrite,
                reasoning = reasoning,
                thresholdTokens = thresholdTokens
            )
        }

        val over200k = costObj["context_over_200k"] as? JsonObject
        if (over200k != null) {
            val input = numberValue(over200k, "input") ?: baseInput
            val output = numberValue(over200k, "output") ?: baseOutput
            val cacheRead = numberValue(over200k, "cache_read") ?: baseCacheRead
            val cacheWrite = numberValue(over200k, "cache_write") ?: baseCacheWrite
            val reasoning = numberValue(over200k, "reasoning") ?: baseReasoning
            return LongContextPricingRate(
                input = input,
                output = output,
                cacheRead = cacheRead,
                cacheWrite = cacheWrite,
                reasoning = reasoning,
                thresholdTokens = 200_000L
            )
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
            reasoning = effectiveReasoning * 1_000_000.0,
            thresholdTokens = LONG_CONTEXT_THRESHOLD_TOKENS
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
                lastFetchedAt = cacheFile.lastModified()
            }
        } catch (_: Exception) {
            // 保留内存或已加载价格。
        }
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
