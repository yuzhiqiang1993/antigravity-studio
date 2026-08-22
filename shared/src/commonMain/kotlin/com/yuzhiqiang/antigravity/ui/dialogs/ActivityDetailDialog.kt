package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ActivityLog
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.BadgeTone
import com.yuzhiqiang.antigravity.ui.components.StatusBadge
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ActivityDetailDialog(
    log: ActivityLog,
    onDismiss: () -> Unit,
    onCopyNotice: (String) -> Unit
) {
    val s = strings()
    val statusTone = when {
        log.statusCode in 200..299 -> BadgeTone.SUCCESS
        log.statusCode in 300..499 -> BadgeTone.WARNING
        else -> BadgeTone.ERROR
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTokens.Spacing.card),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                        Text(
                            text = "请求详情",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = log.id,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    StatusBadge(
                        text = "HTTP ${log.statusCode}",
                        tone = statusTone,
                        showDot = false
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Detail Items
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                ) {
                    DetailRow("请求方法", log.method.uppercase())
                    DetailRow("请求路径", log.path)
                    DetailRow("响应耗时", "${log.durationMs} ms")
                    DetailRow("请求时间", formatFullTime(log.timestamp))
                    DetailRow("路由模式", if (log.isOfficialPassthrough) "官方透传 (Passthrough)" else "自定义路由 (BYOK)")
                    log.modelId?.let { DetailRow("目标模型", it) }
                    log.providerName?.let { DetailRow("服务商", it) }

                    if (log.fallbackAttempted) {
                        DetailRow("备用路由尝试", if (log.fallbackSucceeded) "已成功回退" else "回退失败")
                    }

                    if (log.errorMessage != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)) {
                            Text(
                                text = "错误信息",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppTokens.Radius.small))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(AppTokens.Radius.small))
                                    .padding(AppTokens.Spacing.content)
                            ) {
                                Text(
                                    text = log.errorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val jsonString = """
                                {
                                  "id": "${log.id}",
                                  "method": "${log.method}",
                                  "path": "${log.path}",
                                  "statusCode": ${log.statusCode},
                                  "durationMs": ${log.durationMs},
                                  "isOfficialPassthrough": ${log.isOfficialPassthrough},
                                  "modelId": "${log.modelId.orEmpty()}",
                                  "providerName": "${log.providerName.orEmpty()}",
                                  "errorMessage": "${log.errorMessage.orEmpty()}"
                                }
                            """.trimIndent()
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(jsonString), null)
                            onCopyNotice(s.commonCopied)
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.md, vertical = AppTokens.Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(AppTokens.Size.iconSmall)
                        )
                        Spacer(Modifier.width(AppTokens.Spacing.xs))
                        Text(
                            text = "复制 JSON",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text(
                            text = s.commonClose,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFullTime(timestamp: Long): String {
    return try {
        val dt = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        "--"
    }
}
