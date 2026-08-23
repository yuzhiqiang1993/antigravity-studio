package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.runtime.Immutable

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel

@Immutable
data class GroupedOfficialModel(
    val baseName: String,
    val baseItem: OfficialCatalogModel,
    val variants: List<OfficialModelVariant>
)
