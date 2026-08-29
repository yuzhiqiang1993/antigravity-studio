package com.yuzhiqiang.antigravity.ui.dialogs.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.Strings
import com.yuzhiqiang.antigravity.ui.utils.copyToClipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun HeadersDisplayBlock(
    title: String,
    headers: Map<String, String>,
    onCopy: () -> Unit,
    s: Strings
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title (${headers.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = s.activityDetailCopyHeaders,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                headers.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 120.dp, max = 220.dp)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PayloadDisplayBlock(
    title: String,
    payload: String,
    onCopy: () -> Unit,
    s: Strings
) {
    var isFormatted by remember { mutableStateOf(true) }
    val isJsonLikely = remember(payload) {
        val trimmed = payload.trim()
        (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }
    val displayedText = remember(payload, isFormatted) {
        if (isFormatted && isJsonLikely) {
            formatJsonIfPossible(payload)
        } else {
            payload
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isJsonLikely) {
                    TextButton(
                        onClick = { isFormatted = !isFormatted },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = if (isFormatted) s.activityDetailRawText else s.activityDetailFormatJson,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                }
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = s.activityDetailCopyBody,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = displayedText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun copyHeadersToClipboard(
    headers: Map<String, String>,
    onCopyNotice: (String) -> Unit,
    s: Strings
) {
    val text = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    if (copyToClipboard(text)) {
        onCopyNotice(s.commonCopied)
    }
}

private val prettyJsonInstance = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
}

fun formatJsonIfPossible(raw: String): String {
    return try {
        val element = prettyJsonInstance.parseToJsonElement(raw)
        prettyJsonInstance.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        raw
    }
}
