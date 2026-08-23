package com.yuzhiqiang.antigravity.ui.screens.models

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel

data class GroupedOfficialModel(
    val baseName: String,
    val baseItem: OfficialCatalogModel,
    val variants: List<OfficialModelVariant>
)
