package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.i18n.strings

@Composable
fun ProviderEditorFooter(
    currentStep: ProviderEditStep,
    isSingleModelMode: Boolean,
    initialProvider: Provider?,
    isFetching: Boolean,
    isDebugFetching: Boolean,
    isDebugMode: Boolean,
    name: String,
    baseUrl: String,
    selectedModelCount: Int,
    onPrevStep: () -> Unit,
    onDebugCatalog: () -> Unit,
    onSkipFetch: () -> Unit,
    onFetchModels: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val s = strings()

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth(
                ).height(58.dp)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isSingleModelMode && currentStep == ProviderEditStep.CONFIG_CONNECTION && initialProvider == null) {
                    TextButton(
                        enabled = !isFetching && !isDebugFetching,
                        onClick = onPrevStep,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(s.providerPrevStep, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                } else if (!isSingleModelMode && currentStep == ProviderEditStep.SELECT_MODELS) {
                    TextButton(
                        enabled = !isFetching && !isDebugFetching,
                        onClick = onPrevStep,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(s.providerPrevStep, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentStep == ProviderEditStep.SELECT_MODELS && !isSingleModelMode) {
                    Text(
                        text = if (selectedModelCount > 0) s.providerFilterSelected(selectedModelCount) else s.providerNoModelsEmpty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDebugMode && currentStep != ProviderEditStep.SELECT_PRESET) {
                    OutlinedButton(
                        enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isFetching && !isDebugFetching,
                        onClick = onDebugCatalog,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isDebugFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = if (isDebugFetching) s.modelsFetchingModels else s.providerViewModelsResponse,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp)
                            )
                        }
                    }
                }

                if (currentStep == ProviderEditStep.CONFIG_CONNECTION) {
                    OutlinedButton(
                        enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isFetching && !isDebugFetching,
                        onClick = onSkipFetch,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(s.providerSkipFetchManualAdd, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
                    }
                }

                OutlinedButton(
                    enabled = !isFetching && !isDebugFetching,
                    onClick = onCancel,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(s.commonCancel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }

                if (currentStep == ProviderEditStep.CONFIG_CONNECTION) {
                    Button(
                        enabled = name.isNotBlank() && baseUrl.isNotBlank() && !isFetching && !isDebugFetching,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        onClick = onFetchModels,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        if (isFetching) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(s.modelsFetchingModels, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(s.modelsFetchModels, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else if (currentStep == ProviderEditStep.SELECT_MODELS) {
                    Button(
                        enabled = selectedModelCount > 0,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(s.commonSave, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}
