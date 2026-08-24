package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.TokenLimitSource
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.dialogs.ReasoningConfigDraft
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun ProviderModelSelectionStep(
    fetchedModelConfigs: List<CatalogModelConfig>,
    selectedModelIds: Set<String>,
    onSelectedModelIdsChange: (Set<String>) -> Unit,
    isSingleModelMode: Boolean,
    onConfigureReasoning: (String) -> Unit,
    onUpdateModelConfig: (CatalogModelConfig) -> Unit,
    onAddNewModel: ((CatalogModelConfig) -> Unit)? = null,
    currentProvider: () -> Provider,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var modelSearchQuery by remember { mutableStateOf("") }
    var modelFilterTab by remember { mutableStateOf(ModelSelectionFilter.ALL) }
    var showAddManualDialog by remember { mutableStateOf(false) }

    val filteredFetchedModels = remember(fetchedModelConfigs, modelSearchQuery, modelFilterTab, selectedModelIds) {
        fetchedModelConfigs.filter { model ->
            val matchSearch = modelSearchQuery.isBlank() ||
                    model.name.contains(modelSearchQuery, ignoreCase = true) ||
                    model.id.contains(modelSearchQuery, ignoreCase = true)

            val isSelected = selectedModelIds.contains(model.id)
            val matchFilter = when (modelFilterTab) {
                ModelSelectionFilter.ALL -> true
                ModelSelectionFilter.SELECTED -> isSelected
                ModelSelectionFilter.UNSELECTED -> !isSelected
            }

            matchSearch && matchFilter
        }
    }

    val s = com.yuzhiqiang.antigravity.i18n.strings()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (!isSingleModelMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioSearchField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        placeholder = s.providerSearchModelsPlaceholder,
                        modifier = Modifier.width(220.dp)
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTokens.Radius.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val totalCount = fetchedModelConfigs.size
                        val selCount = selectedModelIds.size
                        val unselCount = totalCount - selCount

                        listOf(
                            ModelSelectionFilter.ALL to s.providerFilterAll(totalCount),
                            ModelSelectionFilter.SELECTED to s.providerFilterSelected(selCount),
                            ModelSelectionFilter.UNSELECTED to s.providerFilterUnselected(unselCount)
                        ).forEach { (filter, label) ->
                            val isTabActive = modelFilterTab == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                    .background(
                                        if (isTabActive) MaterialTheme.colorScheme.surface
                                        else Color.Transparent
                                    )
                                    .clickable { modelFilterTab = filter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isTabActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allVisibleIds = filteredFetchedModels.map { it.id }.toSet()
                    val isAllSelected =
                        allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedModelIds }

                    OutlinedButton(
                        onClick = {
                            if (isAllSelected) {
                                onSelectedModelIdsChange(selectedModelIds - allVisibleIds)
                            } else {
                                onSelectedModelIdsChange(selectedModelIds + allVisibleIds)
                            }
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            if (isAllSelected) s.providerUnselectAll else s.providerSelectAll(filteredFetchedModels.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
                        )
                    }

                    Button(
                        onClick = { showAddManualDialog = true },
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = s.providerAddNewModel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        if (filteredFetchedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = when {
                            modelSearchQuery.isNotBlank() -> s.providerNoModelsFound(modelSearchQuery)
                            modelFilterTab != ModelSelectionFilter.ALL -> s.providerNoModelsEmpty
                            else -> s.providerNoModelsEmptyPrompt
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fetchedModelConfigs.isEmpty()) {
                        Button(
                            onClick = { showAddManualDialog = true },
                            shape = RoundedCornerShape(AppTokens.Radius.small),
                            modifier = Modifier.padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = s.providerAddNewModel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredFetchedModels, key = { it.id }) { modelConfig ->
                    val isChecked = selectedModelIds.contains(modelConfig.id)
                    CatalogModelRowCard(
                        config = modelConfig,
                        isChecked = isChecked,
                        isSingleMode = isSingleModelMode,
                        onToggleCheck = {
                            onSelectedModelIdsChange(
                                if (isChecked) selectedModelIds - modelConfig.id else selectedModelIds + modelConfig.id
                            )
                        },
                        onConfigureReasoning = {
                            onConfigureReasoning(modelConfig.id)
                        },
                        onTokenLimitChanged = onUpdateModelConfig,
                        onToolsChanged = onUpdateModelConfig,
                        onVisionChanged = { updatedConfig ->
                            val modalities = updatedConfig.inputModalities
                                .toMutableSet()
                                .apply {
                                    add(ModelModality.TEXT)
                                    if (updatedConfig.isVision) add(ModelModality.IMAGE)
                                    else remove(ModelModality.IMAGE)
                                }
                            val mimeTypes = updatedConfig.inputMimeTypes
                                .toMutableSet()
                                .apply {
                                    if (updatedConfig.isVision && none { it.startsWith("image/", ignoreCase = true) }) {
                                        addAll(listOf("image/png", "image/jpeg", "image/webp"))
                                    }
                                    if (!updatedConfig.isVision) {
                                        removeAll { it.startsWith("image/", ignoreCase = true) }
                                    }
                                }
                            onUpdateModelConfig(
                                updatedConfig.copy(
                                    inputModalities = modalities,
                                    inputMimeTypes = mimeTypes.toList().sorted()
                                )
                            )
                        },
                        onTestModel = {
                            coroutineScope.launch {
                                onUpdateModelConfig(
                                    modelConfig.copy(
                                        isTesting = true,
                                        testStatusText = null,
                                        testErrorMessage = null,
                                        testStatusCode = null,
                                        testLatencyMs = null
                                    )
                                )
                                val tempProvider = currentProvider()
                                val result = ConnectionTester.testProvider(
                                    tempProvider,
                                    modelConfig.id,
                                    imageOnly = modelConfig.isImageGeneration
                                )
                                onUpdateModelConfig(
                                    modelConfig.copy(
                                        isTesting = false,
                                        isTestSuccess = result.success,
                                        testStatusText = if (result.success) "${result.latencyMs}ms" else s.providerTestFailed,
                                        testErrorMessage = result.error,
                                        testStatusCode = result.statusCode.takeIf { it > 0 },
                                        testLatencyMs = result.latencyMs
                                    )
                                )
                            }
                        }

                    )
                }
            }
        }
    }

    if (showAddManualDialog) {
        ManualAddModelDialog(
            existingIds = fetchedModelConfigs.map { it.id }.toSet(),
            onDismiss = { showAddManualDialog = false },
            onConfirm = { customId, customName, customVendor ->
                val newConfig = CatalogModelConfig(
                    id = customId,
                    name = customName.ifBlank { customId },
                    vendor = customVendor.takeIf { it.isNotBlank() },
                    inputTokenLimit = 131_072L,
                    inputTokenLimitSource = TokenLimitSource.CONFIGURED,
                    outputTokenLimit = 4_096L,
                    outputTokenLimitSource = TokenLimitSource.CONFIGURED,
                    isVision = true,
                    inputModalities = setOf(ModelModality.TEXT, ModelModality.IMAGE),
                    outputModalities = setOf(ModelModality.TEXT),
                    inputMimeTypes = listOf("image/png", "image/jpeg", "image/webp"),
                    roles = emptySet(),
                    isImageGeneration = false,
                    compressionPolicy = null,
                    reasoningMappings = emptyMap(),
                    isReasoning = false,
                    reasoningDraft = ReasoningConfigDraft(
                        enabled = false,
                        levels = emptySet<ReasoningLevel>(),
                        customValue = null,
                        thinkingBudget = null,
                        minThinkingBudget = null,
                        mappings = emptyMap()
                    ),
                    isTools = true,
                    isUnavailable = false
                )
                onAddNewModel?.invoke(newConfig)
                showAddManualDialog = false
            }
        )
    }
}

@Composable
private fun ManualAddModelDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, vendor: String) -> Unit
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    var inputId by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var inputVendor by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun trySubmit() {
        val trimmedId = inputId.trim()
        if (trimmedId.isBlank()) {
            errorMessage = s.providerModelIdRequired
            return
        }
        if (existingIds.contains(trimmedId)) {
            errorMessage = s.providerModelAlreadyExists
            return
        }
        onConfirm(trimmedId, inputName.trim(), inputVendor.trim())
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            ),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = s.providerManualAddModelTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Model ID",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "*",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    StudioDialogTextField(
                        value = inputId,
                        onValueChange = {
                            inputId = it
                            if (errorMessage != null) errorMessage = null
                        },
                        placeholder = s.providerModelIdPlaceholder
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = s.providerModelNamePlaceholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StudioDialogTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        placeholder = s.providerModelNamePlaceholder
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = s.providerModelVendorPlaceholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StudioDialogTextField(
                        value = inputVendor,
                        onValueChange = { inputVendor = it },
                        placeholder = s.providerModelVendorPlaceholder
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.small)
                    ) {
                        Text(s.commonCancel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { trySubmit() },
                        enabled = inputId.isNotBlank(),
                        shape = RoundedCornerShape(AppTokens.Radius.small)
                    ) {
                        Text(s.providerAddAndSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp)
            ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

