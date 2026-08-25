package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/** 为单个 VirtualModel 选择跨 Provider 的备用入口（Material 3 下拉菜单规范）。 */
@Composable
fun FallbackSelector(
    source: VirtualModel,
    allVirtualModels: List<VirtualModel>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = com.yuzhiqiang.antigravity.i18n.strings()
    var expanded by remember(source.id) { mutableStateOf(false) }
    val target = allVirtualModels.firstOrNull { virtual ->
        virtual.id == source.fallbackVirtualModelId ||
                source.fallbackVirtualModelId in ModelIdentity.acceptedIds(virtual)
    }
    val candidates = allVirtualModels.filter { virtual ->
        virtual.id != source.id && virtual.enabled
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fallback",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { expanded = true },
                enabled = candidates.isNotEmpty() || source.fallbackVirtualModelId != null,
                contentPadding = PaddingValues(horizontal = AppTokens.Spacing.xs, vertical = 0.dp)
            ) {
                Text(
                    text = target?.let { virtual ->
                        virtual.displayName ?: virtual.name.ifBlank { ModelIdentity.catalogKey(virtual) }
                    } ?: if (candidates.isEmpty()) s.modelsNoModels else s.modelsNotSet,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        StudioDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            StudioDropdownMenuItem(
                text = s.modelsNoFallback,
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            candidates.forEach { candidate ->
                StudioDropdownMenuItem(
                    text = candidate.displayName ?: candidate.name.ifBlank { ModelIdentity.catalogKey(candidate) },
                    onClick = {
                        expanded = false
                        onSelected(candidate.id)
                    }
                )
            }
        }
    }
}
