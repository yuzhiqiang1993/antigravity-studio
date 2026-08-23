package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.runtime.Immutable

import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.ui.dialogs.provider.formatTokenDisplay
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel

@Immutable
data class UniversalModelCardUiState(
    val title: String,
    val subtitle: String? = null,
    val brand: ModelBrand,
    val isEnabled: Boolean,
    val onToggleEnabled: () -> Unit,
    val testStatus: AppViewModel.ModelTestStatus? = null,
    val onTest: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val contextLimitText: String,
    val outputLimitText: String?,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
    val reasoningVariants: List<String> = emptyList(),
    val compressionLabel: String,
    val isCompressionCustom: Boolean,
    val onEditCompressionPolicy: () -> Unit,
    val onOpenVisionDetail: (() -> Unit)? = null,
    val onOpenReasoningDetail: (() -> Unit)? = null,
    val onOpenInfoDetail: (() -> Unit)? = null
)


