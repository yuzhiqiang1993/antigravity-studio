package com.yuzhiqiang.antigravity.ui.screens.models

data class ModelMetaInfo(
    val name: String,
    val id: String,
    val contextLimit: Long? = null,
    val outputLimit: Long? = null,
    val roles: List<String> = emptyList()
)

