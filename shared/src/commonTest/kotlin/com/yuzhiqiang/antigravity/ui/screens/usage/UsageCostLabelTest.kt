package com.yuzhiqiang.antigravity.ui.screens.usage

import com.yuzhiqiang.antigravity.i18n.StringsZh
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageCostLabelTest {
    @Test
    fun matchedPricingShowsCostOrLowerBound() {
        assertEquals(
            "≥$1.2",
            usageBucketCostLabel(
                costUsd = 1.25,
                pricingMatched = true,
                costLowerBound = true,
                s = StringsZh
            )
        )
        assertEquals(
            "$1.2",
            usageBucketCostLabel(
                costUsd = 1.25,
                pricingMatched = true,
                costLowerBound = false,
                s = StringsZh
            )
        )
    }

    @Test
    fun unmatchedPricingDisplaysZero() {
        assertEquals(
            "$0",
            usageBucketCostLabel(
                costUsd = 0.0,
                pricingMatched = false,
                costLowerBound = false,
                s = StringsZh
            )
        )
        assertEquals(
            "$0",
            usageBucketCostLabel(
                costUsd = 1.25,
                pricingMatched = false,
                costLowerBound = false,
                s = StringsZh
            )
        )
    }
}
