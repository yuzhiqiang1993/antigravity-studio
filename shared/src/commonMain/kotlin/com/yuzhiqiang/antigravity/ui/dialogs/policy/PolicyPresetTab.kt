package com.yuzhiqiang.antigravity.ui.dialogs.policy

import com.yuzhiqiang.antigravity.i18n.Strings

internal enum class PolicyPresetTab(val minCapacity: Long?) {
    DEFAULT(null),
    CONTEXT_256K(256_000L),
    CONTEXT_372K(372_000L),
    CONTEXT_500K(500_000L),
    CONTEXT_1M(1_000_000L),
    CUSTOM(null);

    fun label(s: Strings): String = when (this) {
        DEFAULT -> s.policyPresetDefault
        CONTEXT_256K -> "256K"
        CONTEXT_372K -> "372K"
        CONTEXT_500K -> "500K"
        CONTEXT_1M -> "1M"
        CUSTOM -> s.policyPresetCustom
    }
}
