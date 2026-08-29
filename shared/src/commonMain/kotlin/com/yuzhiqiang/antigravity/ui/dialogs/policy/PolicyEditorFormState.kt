package com.yuzhiqiang.antigravity.ui.dialogs.policy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay

internal class PolicyEditorFormState(
    val modelId: String,
    initialPolicy: ModelCompressionPolicy?
) {
    val defaultNativeThreshold: Long = defaultNativeThresholdForModel(modelId)
    val defaultNativeLimit: Long = defaultNativeLimitForModel(modelId)
    val defaultNativeReserve: Long = DEFAULT_NATIVE_RESERVE

    var selectedTab by mutableStateOf(detectPresetTab(initialPolicy))
    var maxLimitText by mutableStateOf((initialPolicy?.maxTokenLimit ?: 153_600L).toString())
    var thresholdText by mutableStateOf((initialPolicy?.tokenThreshold ?: 102_400L).toString())
    var reserveText by mutableStateOf((initialPolicy?.maxOutputTokens ?: 30_720L).toString())
    var useSameModel by mutableStateOf(
        initialPolicy?.useLastPlannerModel == true && initialPolicy.strategy == "CHECKPOINT_STRATEGY_SAME_MODEL"
    )

    var thresholdModeByPercent by mutableStateOf(false)
    var limitModeByPercent by mutableStateOf(false)
    var reserveModeByPreset by mutableStateOf(false)

    val isDefaultMode: Boolean get() = selectedTab == PolicyPresetTab.DEFAULT
    val isCustomMode: Boolean get() = selectedTab == PolicyPresetTab.CUSTOM

    val displayThreshold: Long
        get() = if (isDefaultMode) defaultNativeThreshold else (thresholdText.toLongOrNull() ?: 0L)

    val displayLimit: Long
        get() = if (isDefaultMode) defaultNativeLimit else (maxLimitText.toLongOrNull() ?: 0L)

    val displayReserve: Long
        get() = if (isDefaultMode) defaultNativeReserve else (reserveText.toLongOrNull() ?: 0L)


    fun selectPreset(tab: PolicyPresetTab) {
        selectedTab = tab
        when (tab) {
            PolicyPresetTab.DEFAULT -> {}
            PolicyPresetTab.CONTEXT_256K -> {
                thresholdText = "102400"
                maxLimitText = "153600"
                reserveText = "30720"
            }

            PolicyPresetTab.CONTEXT_372K -> {
                thresholdText = "148800"
                maxLimitText = "223200"
                reserveText = "44640"
            }

            PolicyPresetTab.CONTEXT_500K -> {
                thresholdText = "200000"
                maxLimitText = "300000"
                reserveText = "60000"
            }

            PolicyPresetTab.CONTEXT_1M -> {
                thresholdText = "419430"
                maxLimitText = "629145"
                reserveText = "65535"
            }

            PolicyPresetTab.CUSTOM -> {}
        }
    }

    fun calculateValidationError(s: Strings, contextWindow: Long?): String? {
        val curLimit = displayLimit
        val curThreshold = displayThreshold
        val curReserve = displayReserve
        val curSafeLimit = if (contextWindow != null) {
            (contextWindow - curReserve).coerceAtLeast(1L)
        } else {
            null
        }

        return when {
            curLimit <= 0L -> s.policyLimitMustPositive
            curThreshold <= 0L -> s.policyThresholdMustPositive
            curReserve <= 0L -> s.policyReserveMustPositive
            curSafeLimit != null && curLimit > curSafeLimit -> s.policyLimitExceedsSafeLimit(
                formatTokenDisplay(curLimit),
                formatTokenDisplay(curSafeLimit),
                formatTokenDisplay(contextWindow ?: 0L),
                formatTokenDisplay(curReserve)
            )

            contextWindow != null && curLimit > contextWindow -> s.policyLimitExceedsContext(
                formatTokenDisplay(curLimit),
                formatTokenDisplay(contextWindow)
            )

            curThreshold >= curLimit -> s.policyThresholdExceedsLimit(
                formatTokenDisplay(curThreshold),
                formatTokenDisplay(curLimit)
            )

            curThreshold + curReserve > curLimit -> s.policySumExceedsLimit(
                formatTokenDisplay(curThreshold + curReserve),
                formatTokenDisplay(curLimit)
            )

            else -> null
        }
    }

    fun buildPolicyToSave(initialPolicy: ModelCompressionPolicy?): ModelCompressionPolicy? {
        if (isDefaultMode) return null
        val basePolicy = initialPolicy ?: ModelCompressionPolicy()
        return basePolicy.copy(
            tokenThreshold = displayThreshold,
            maxTokenLimit = displayLimit,
            maxOutputTokens = displayReserve,
            useLastPlannerModel = useSameModel,
            strategy = if (useSameModel) "CHECKPOINT_STRATEGY_SAME_MODEL" else "CHECKPOINT_STRATEGY_UNSPECIFIED"
        )
    }

    companion object {
        const val DEFAULT_NATIVE_RESERVE: Long = 16_384L

        fun cleanModelDisplayName(modelDisplayName: String?, modelId: String): String {
            val raw = modelDisplayName?.takeIf { it.isNotBlank() } ?: modelId
            val cleaned = raw
                .replace(Regex("^p_[0-9a-zA-Z]+-"), "")
                .replace(Regex("^upstream-[0-9a-fA-F-]+-?"), "")
            return if (cleaned.isNotBlank()) cleaned else raw
        }

        fun defaultNativeThresholdForModel(modelId: String): Long = when {
            modelId.contains("gemini", ignoreCase = true) -> 140_000L
            modelId.contains("claude", ignoreCase = true) || modelId.contains("sonnet", ignoreCase = true) -> 50_000L
            else -> 140_000L
        }

        fun defaultNativeLimitForModel(modelId: String): Long = when {
            modelId.contains("gemini", ignoreCase = true) -> 256_000L
            modelId.contains("claude", ignoreCase = true) || modelId.contains("sonnet", ignoreCase = true) -> 160_000L
            else -> 256_000L
        }

        fun detectPresetTab(initialPolicy: ModelCompressionPolicy?): PolicyPresetTab = when {
            initialPolicy == null -> PolicyPresetTab.DEFAULT
            initialPolicy.tokenThreshold == 102_400L && initialPolicy.maxTokenLimit == 153_600L -> PolicyPresetTab.CONTEXT_256K
            initialPolicy.tokenThreshold == 148_800L && initialPolicy.maxTokenLimit == 223_200L -> PolicyPresetTab.CONTEXT_372K
            initialPolicy.tokenThreshold == 200_000L && initialPolicy.maxTokenLimit == 300_000L -> PolicyPresetTab.CONTEXT_500K
            initialPolicy.tokenThreshold == 419_430L && initialPolicy.maxTokenLimit == 629_145L -> PolicyPresetTab.CONTEXT_1M
            else -> PolicyPresetTab.CUSTOM
        }
    }
}

@Composable
internal fun rememberPolicyEditorFormState(
    modelId: String,
    initialPolicy: ModelCompressionPolicy?
): PolicyEditorFormState {
    return remember(modelId) {
        PolicyEditorFormState(
            modelId = modelId,
            initialPolicy = initialPolicy
        )
    }
}
