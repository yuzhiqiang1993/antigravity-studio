package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.runtime.Immutable

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel

@Immutable
data class OfficialModelVariant(
    val label: String,
    val model: OfficialCatalogModel
)
