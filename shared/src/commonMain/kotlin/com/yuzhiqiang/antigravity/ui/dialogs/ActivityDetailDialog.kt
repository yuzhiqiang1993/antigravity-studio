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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.dialogs.activity.*
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.utils.calculateCacheHitRate
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import com.yuzhiqiang.antigravity.ui.utils.formatDuration
import com.yuzhiqiang.antigravity.ui.utils.formatHitRate
import com.yuzhiqiang.antigravity.ui.utils.formatTokens
import com.yuzhiqiang.antigravity.ui.utils.formatTpot
import com.yuzhiqiang.antigravity.ui.utils.formatTps
import com.yuzhiqiang.antigravity.ui.utils.getCacheHitRateColor
import com.yuzhiqiang.antigravity.ui.utils.getDurationLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.getFirstTokenLatencyTier
import com.yuzhiqiang.antigravity.ui.utils.toColor

@Composable
fun ActivityDetailDialog(
    log: ActivityLog,
    onDismiss: () -> Unit,
    onCopyNotice: (String) -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    isDebugMode: Boolean = false
) {
    val s = strings()
    val statusTone = when {
        log.isPending -> BadgeTone.INFO
        log.statusCode in 200..299 -> BadgeTone.SUCCESS
        log.statusCode in 300..499 -> BadgeTone.WARNING
        else -> BadgeTone.ERROR
    }
    val statusBadgeText = if (log.isPending) s.activityPending else "HTTP ${log.statusCode}"
    val hasDebugData =
        isDebugMode || log.requestHeaders != null || log.requestBody != null || log.responseHeaders != null || log.responseBody != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(760.dp)
                .heightIn(min = 420.dp, max = 760.dp),
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
                            if (isDebugMode) {
                                StatusBadge(
                                    text = "DEBUG",
                                    tone = BadgeTone.INFO
                                )
                            }
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
                        log.queueWaitMs?.let {
                            DetailItemRow(s.activityDetailQueueWait, formatDuration(it))
                        }
                        log.firstByteMs?.let { ttfb ->
                            DetailItemRow(
                                s.activityDetailFirstByte,
                                formatDuration(ttfb),
                                highlightColor = getFirstTokenLatencyTier(ttfb)
                                    .toColor(MaterialTheme.colorScheme.secondary)
                            )
                        }
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
                        log.clientSource?.let { DetailItemRow(s.activityDetailClientSource, it) }
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

                    // 2. 速度与流式指标 (若存在)
                    val hasSpeedInfo = log.tokensPerSecond != null || log.timePerOutputTokenMs != null ||
                        log.generationDurationMs != null || log.maxChunkGapMs != null || log.stallCount > 0 ||
                        log.lastTokenMs != null || log.stallDurationMs != null
                    if (hasSpeedInfo) {
                        DetailSectionCard(title = s.activityDetailSpeedSection) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TokenMetricBadge(
                                    label = s.activityDetailTps,
                                    value = log.tokensPerSecond?.let(::formatTps) ?: "—",
                                    customValueColor = log.tokensPerSecond?.let { MaterialTheme.colorScheme.primary },
                                    modifier = Modifier.weight(1f)
                                )
                                TokenMetricBadge(
                                    label = s.activityDetailTpot,
                                    value = log.timePerOutputTokenMs?.let(::formatTpot) ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                TokenMetricBadge(
                                    label = s.activityDetailGenerationDuration,
                                    value = log.generationDurationMs?.let(::formatDuration) ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                TokenMetricBadge(
                                    label = s.activityDetailMaxChunkGap,
                                    value = log.maxChunkGapMs?.let(::formatDuration) ?: "—",
                                    customValueColor = log.maxChunkGapMs?.let {
                                        if (log.stallCount > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (log.stallCount > 0) {
                                DetailItemRow(
                                    s.activityDetailStallCount,
                                    s.activityStallsCount(log.stallCount),
                                    highlightColor = MaterialTheme.colorScheme.error
                                )
                            }
                            log.stallDurationMs?.takeIf { it > 0L }?.let {
                                DetailItemRow(s.activityDetailStallDuration, formatDuration(it))
                            }
                            log.lastTokenMs?.let {
                                DetailItemRow(s.activityDetailLastToken, formatDuration(it))
                            }
                        }
                    }

                    // 3. Token 消耗统计 (若存在)
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

                    // 4. 错误详情与异常响应体 (完整无脱敏)
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

                    // 5. Debug 详细报文（请求头、请求体、响应头、响应体）
                    if (hasDebugData) {
                        DetailSectionCard(
                            title = s.activityDetailDebugSection,
                            headerColor = MaterialTheme.colorScheme.primary
                        ) {
                            // 5.1 请求头 (Request Headers)
                            if (!log.requestHeaders.isNullOrEmpty()) {
                                HeadersDisplayBlock(
                                    title = s.activityDetailRequestHeaders,
                                    headers = log.requestHeaders,
                                    onCopy = {
                                        copyHeadersToClipboard(log.requestHeaders, onCopyNotice, s)
                                    },
                                    s = s
                                )
                            }

                            // 5.2 请求数据 (Request Body)
                            if (!log.requestBody.isNullOrBlank()) {
                                PayloadDisplayBlock(
                                    title = s.activityDetailRequestBody,
                                    payload = log.requestBody,
                                    onCopy = {
                                        if (copyToClipboard(log.requestBody)) {
                                            onCopyNotice(s.commonCopied)
                                        }
                                    },
                                    s = s
                                )
                            }

                            // 5.3 响应头 (Response Headers)
                            if (!log.responseHeaders.isNullOrEmpty()) {
                                HeadersDisplayBlock(
                                    title = s.activityDetailResponseHeaders,
                                    headers = log.responseHeaders,
                                    onCopy = {
                                        copyHeadersToClipboard(log.responseHeaders, onCopyNotice, s)
                                    },
                                    s = s
                                )
                            }

                            // 5.4 响应数据 (Response Body)
                            if (!log.responseBody.isNullOrBlank()) {
                                PayloadDisplayBlock(
                                    title = s.activityDetailResponseBody,
                                    payload = log.responseBody,
                                    onCopy = {
                                        if (copyToClipboard(log.responseBody)) {
                                            onCopyNotice(s.commonCopied)
                                        }
                                    },
                                    s = s
                                )
                            }

                            if (log.requestHeaders.isNullOrEmpty() && log.requestBody.isNullOrBlank() &&
                                log.responseHeaders.isNullOrEmpty() && log.responseBody.isNullOrBlank()
                            ) {
                                Text(
                                    text = s.activityDetailEmptyPayload,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                val jsonString = log.toJsonString()
                                if (copyToClipboard(jsonString)) {
                                    onCopyNotice(s.commonCopied)
                                }
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
                                    if (copyToClipboard(log.errorMessage)) {
                                        onCopyNotice(s.commonCopied)
                                    }
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
