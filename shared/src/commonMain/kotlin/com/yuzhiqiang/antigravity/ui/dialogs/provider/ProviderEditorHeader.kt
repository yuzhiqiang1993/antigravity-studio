package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.i18n.strings

private data class StepItem(val step: ProviderEditStep, val num: Int, val label: String)

@Composable
fun ProviderEditorHeader(
    isSingleModelMode: Boolean,
    editingSingleModel: UpstreamModel?,
    initialProvider: Provider?,
    currentStep: ProviderEditStep,
    isFetching: Boolean,
    onStepSelect: (ProviderEditStep) -> Unit,
    onClose: () -> Unit
) {
    val s = strings()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            isSingleModelMode -> editingSingleModel?.let { model ->
                                "${s.modelsEditModel} · ${model.displayName ?: model.upstreamModelId}"
                            }.orEmpty()
                            initialProvider != null -> "${s.modelsEditProvider} · ${initialProvider.name}"
                            else -> s.modelsAddProvider
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isSingleModelMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val stepItems = listOf(
                        StepItem(ProviderEditStep.SELECT_PRESET, 1, s.providerStepPreset),
                        StepItem(ProviderEditStep.CONFIG_CONNECTION, 2, s.providerStepConnection),
                        StepItem(ProviderEditStep.SELECT_MODELS, 3, s.providerStepModels)
                    )

                    stepItems.forEach { (step, num, label) ->
                        val isActive = currentStep == step
                        val canNavigate = !isFetching &&
                                step.ordinal <= currentStep.ordinal &&
                                !(initialProvider != null && step == ProviderEditStep.SELECT_PRESET)
                        val nodeModifier = if (canNavigate) {
                            Modifier.clickable { onStepSelect(step) }
                        } else {
                            Modifier
                        }
                        Row(
                            modifier = nodeModifier
                                .clip(RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    num.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    canNavigate -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                        }
                        if (num < 3) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = s.commonClose,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}
