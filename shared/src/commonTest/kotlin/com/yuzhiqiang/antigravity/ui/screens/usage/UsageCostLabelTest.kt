package com.yuzhiqiang.antigravity.ui.screens.usage

import com.yuzhiqiang.antigravity.i18n.StringsZh
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageCostLabelTest {
    @Test
    fun partialPricingShowsKnownCostAsLowerBound() {
        assertEquals(
            "≥$1.2",
            usageBucketCostLabel(
                costUsd = 1.25,
                pricingMatched = false,
                costLowerBound = false,
                s = StringsZh
            )
        )
    }

    @Test
    fun fullyUnmatchedPricingRemainsUnavailable() {
        assertEquals(
            "—",
            usageBucketCostLabel(
                costUsd = 0.0,
                pricingMatched = false,
                costLowerBound = false,
                s = StringsZh
            )
        )
    }
}
