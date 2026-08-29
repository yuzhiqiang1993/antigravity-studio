package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.data.presets.PresetCategory
import com.yuzhiqiang.antigravity.data.presets.PresetProviderTemplate
import com.yuzhiqiang.antigravity.data.presets.PresetTagType
import com.yuzhiqiang.antigravity.data.presets.ProviderPresets
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun ProviderPresetStep(
    selectedPresetId: String?,
    onSelectPreset: (PresetProviderTemplate) -> Unit,
    onSelectCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var presetCategory by remember { mutableStateOf(PresetCategory.ALL) }
    var presetSearchQuery by remember { mutableStateOf("") }
    val isZh = com.yuzhiqiang.antigravity.i18n.I18nManager.currentLanguage == com.yuzhiqiang.antigravity.i18n.AppLanguage.ZH_CN

    val filteredPresets = remember(presetCategory, presetSearchQuery, isZh) {
        val list = if (presetCategory == PresetCategory.ALL) {
            ProviderPresets.allPresets
        } else {
            ProviderPresets.allPresets.filter { presetCategory in it.categories }
        }
        if (presetSearchQuery.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(presetSearchQuery, ignoreCase = true) ||
                        it.nameEn.contains(presetSearchQuery, ignoreCase = true) ||
                        it.defaultBaseUrl.contains(presetSearchQuery, ignoreCase = true)
            }
        }
    }

    val s = com.yuzhiqiang.antigravity.i18n.strings()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    PresetCategory.ALL to s.providerPresetCategoryAll,
                    PresetCategory.AGGREGATOR to s.providerPresetCategoryAggregator,
                    PresetCategory.RECOMMENDED to s.providerPresetCategoryRecommended,
                    PresetCategory.OFFICIAL to s.providerPresetCategoryOfficial,
                    PresetCategory.LOCAL_CUSTOM to s.providerPresetCategoryLocalCustom
                ).forEach { (cat, label) ->
                    val selected = presetCategory == cat
                    Box(
                        modifier = Modifier
                            .then(
                                if (selected) {
                                    Modifier
                                        .shadow(1.dp, RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { presetCategory = cat }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            StudioSearchField(
                value = presetSearchQuery,
                onValueChange = { presetSearchQuery = it },
                placeholder = s.providerSearchPlaceholder,
                modifier = Modifier.width(220.dp)
            )
        }

       LazyVerticalGrid(
           columns = GridCells.Fixed(5),
           verticalArrangement = Arrangement.spacedBy(9.5.dp),
           horizontalArrangement = Arrangement.spacedBy(9.5.dp),
           contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
           modifier = Modifier.fillMaxSize()
       ) {
           gridItems(filteredPresets, key = { it.id }) { preset ->
               val isSelected = selectedPresetId == preset.id
               val isCustom = preset.isCustomCard
               val shape = RoundedCornerShape(10.dp)

                val cardModifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(shape)
                    .then(
                        when {
                            isCustom -> Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                        )
                                    ),
                                    shape
                                )
                                .dashedBorder(
                                    width = 1.3.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                    cornerRadius = 10.dp,
                                    dashLength = 4.5.dp,
                                    gapLength = 3.5.dp
                                )
                            isSelected -> Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                            else -> Modifier
                                .background(MaterialTheme.colorScheme.surface, shape)
                                .dashedBorder(
                                    width = 1.2.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                    cornerRadius = 10.dp,
                                    dashLength = 4.5.dp,
                                    gapLength = 3.5.dp
                                )
                        }
                    )
                    .clickable {
                        if (isCustom || preset.id == "custom_openai") {
                            onSelectCustom()
                        } else {
                            onSelectPreset(preset)
                        }
                    }
                    .padding(horizontal = 12.dp)

                Row(
                    modifier = cardModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.5.dp))
                            .background(preset.iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.displayIconChar(isZh),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = preset.iconColor
                        )
                    }

                    Text(
                        text = if (preset.isCustomCard || preset.id == "custom_openai") s.providerPresetCustomName else preset.displayName(isZh),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
