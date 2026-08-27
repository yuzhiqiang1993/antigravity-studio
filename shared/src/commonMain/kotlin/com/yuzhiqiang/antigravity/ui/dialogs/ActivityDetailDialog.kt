package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatTokens
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ActivityDetailDialog(
    log: ActivityLog,
    onDismiss: () -> Unit,
    onCopyNotice: (String) -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null
) {
    val s = strings()
    val isSuccess = log.statusCode in 200..399
    val statusTone = when {
        log.isPending -> BadgeTone.INFO
        log.statusCode in 200..299 -> BadgeTone.SUCCESS
        log.statusCode in 300..499 -> BadgeTone.WARNING
        else -> BadgeTone.ERROR
    }
    val statusBadgeText = if (log.isPending) s.activityPending else "HTTP ${log.statusCode}"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(680.dp)
                .heightIn(min = 400.dp, max = 700.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = s.activityDetailTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(
                                text = statusBadgeText,
                                tone = statusTone,
                                showDot = log.isPending,
                                pulse = log.isPending
                            )
                        }
                        Text(
                            text = log.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s.commonClose,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(AppTokens.Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                ) {
                    // 1. 核心链路卡片
                    DetailSectionCard(title = s.activityDetailRouteSection) {
                        DetailItemRow(s.activityDetailMethod, log.method.uppercase())
                        DetailItemRow(s.activityDetailPath, log.path, isMonospace = true)
                        val durationFormatted = when {
                            log.isPending -> s.activityProcessing
                            log.durationMs >= 1000L -> "${formatDuration(log.durationMs)} (${log.durationMs} ms)"
                            else -> "${log.durationMs} ms"
                        }
                        val durationColor = if (!log.isPending && log.durationMs > 0) {
                            getDurationLatencyTier(log.durationMs).toColor(MaterialTheme.colorScheme.primary)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        DetailItemRow(s.activityDetailDuration, durationFormatted, highlightColor = durationColor)
                        log.firstTokenMs?.let { ttft ->
                            val ttftFormatted = if (ttft >= 1000L) {
                                "${formatDuration(ttft)} ($ttft ms)"
                            } else {
                                "$ttft ms"
                            }
                            val ttftColor = getFirstTokenLatencyTier(ttft).toColor(MaterialTheme.colorScheme.secondary)
                            DetailItemRow(s.activityDetailFirstToken, ttftFormatted, highlightColor = ttftColor)
                        }
                        DetailItemRow(s.activityDetailTimestamp, formatFullTime(log.timestamp))
                        if (log.retryCount > 0) {
                            DetailItemRow(
                                s.activityRetryCount,
                                s.activityRetryBadge(log.retryCount),
                                highlightColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        DetailItemRow(
                            s.activityDetailRouteMode,
                            if (log.isOfficialPassthrough) s.activityDetailPassthroughMode else s.activityDetailForwardMode
                        )
                        log.modelId?.let { DetailItemRow(s.activityDetailTargetModel, it, isMonospace = true) }
                        log.requestedModelId
                            ?.takeIf { it != log.modelId }
                            ?.let { DetailItemRow(s.activityDetailRequestedModel, it, isMonospace = true) }
                        if (!log.isOfficialPassthrough) {
                            log.providerName?.let { DetailItemRow(s.activityDetailProvider, it) }
                        }
                    }

                    // 2. Token 消耗统计 (若存在)
                    val hasTokenInfo = log.totalTokens != null || log.inputTokens != null || log.outputTokens != null
                    if (hasTokenInfo) {
                        DetailSectionCard(title = s.activityDetailTokenSection) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TokenMetricBadge(
                                    s.activityDetailPromptTokens,
                                    log.inputTokens?.let(::formatTokens) ?: "—",
                                    Modifier.weight(1f)
                                )
                                TokenMetricBadge(
                                    s.activityDetailCompletionTokens,
                                    log.outputTokens?.let(::formatTokens) ?: "—",
                                    Modifier.weight(1f)
                                )
                                TokenMetricBadge(
                                    s.activityDetailTotalTokens,
                                    log.totalTokens?.let(::formatTokens) ?: "—",
                                    Modifier.weight(1f),
                                    isTotal = true
                                )
                            }

                            if (log.reasoningTokens != null || log.cacheReadTokens != null || log.cacheWriteTokens != null) {
                                val hitRate =
                                    calculateCacheHitRate(log.cacheReadTokens, log.inputTokens, log.cacheWriteTokens)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TokenMetricBadge(
                                        s.activityDetailReasoningTokens,
                                        log.reasoningTokens?.let(::formatTokens) ?: "—",
                                        Modifier.weight(1f)
                                    )
                                    TokenMetricBadge(
                                        s.activityDetailCacheReadTokens,
                                        log.cacheReadTokens?.let(::formatTokens) ?: "—",
                                        Modifier.weight(1f)
                                    )
                                    TokenMetricBadge(
                                        s.activityDetailCacheWriteTokens,
                                        log.cacheWriteTokens?.let(::formatTokens) ?: "—",
                                        Modifier.weight(1f)
                                    )
                                    if (hitRate != null) {
                                        TokenMetricBadge(
                                            label = s.activityDetailCacheHitRate,
                                            value = formatHitRate(hitRate),
                                            customValueColor = getCacheHitRateColor(hitRate),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 错误详情与异常响应体 (完整无脱敏)
                    if (!log.errorMessage.isNullOrBlank()) {
                        DetailSectionCard(
                            title = s.activityDetailErrorSection,
                            headerColor = MaterialTheme.colorScheme.error
                        ) {
                            log.errorSource?.let { source ->
                                val sourceLabel = when (source) {
                                    "UPSTREAM_RESPONSE" -> s.activityErrorSourceUpstreamResponse
                                    "UPSTREAM_TRANSPORT" -> s.activityErrorSourceUpstreamTransport
                                    "STUDIO_ADAPTER" -> s.activityErrorSourceStudioAdapter
                                    "STUDIO_PROXY" -> s.activityErrorSourceStudioProxy
                                    else -> source
                                }
                                DetailItemRow(
                                    s.activityDetailErrorSource,
                                    sourceLabel,
                                    highlightColor = MaterialTheme.colorScheme.error
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = log.errorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Actions Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val jsonString = buildJsonObject {
                                    put("id", log.id)
                                    put("timestamp", log.timestamp)
                                    put("timeFormatted", formatFullTime(log.timestamp))
                                    put("method", log.method)
                                    put("path", log.path)
                                    put("statusCode", log.statusCode)
                                    put("durationMs", log.durationMs)
                                    put("isPending", log.isPending)
                                    if (log.retryCount > 0) put("retryCount", log.retryCount)
                                    log.firstTokenMs?.let { put("firstTokenMs", it) }
                                    put("isOfficialPassthrough", log.isOfficialPassthrough)
                                    put("modelId", log.modelId)
                                    put("requestedModelId", log.requestedModelId)
                                    put("providerName", log.providerName)
                                    put("inputTokens", log.inputTokens)
                                    put("outputTokens", log.outputTokens)
                                    put("cacheReadTokens", log.cacheReadTokens)
                                    put("cacheWriteTokens", log.cacheWriteTokens)
                                    put("reasoningTokens", log.reasoningTokens)
                                    put("totalTokens", log.totalTokens)
                                    put("errorMessage", log.errorMessage)
                                    put("errorSource", log.errorSource)
                                }.toString()
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                    StringSelection(jsonString),
                                    null
                                )
                                onCopyNotice(s.commonCopied)
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = s.activityDetailCopyJson,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }

                        if (!log.errorMessage.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                        StringSelection(log.errorMessage),
                                        null
                                    )
                                    onCopyNotice(s.commonCopied)
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = s.activityDetailCopyError,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        if (log.errorSource == "UPSTREAM_TRANSPORT" && onOpenNetworkSettings != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenNetworkSettings()
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = s.settingsOpenNetworkSettings,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = s.commonClose,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    headerColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                ),
                color = headerColor
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TokenMetricBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    customValueColor: Color? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isTotal) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = when {
                    customValueColor != null -> customValueColor
                    isTotal -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun formatFullTime(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        "--"
    }
}
