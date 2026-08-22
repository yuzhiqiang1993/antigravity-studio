package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.VirtualModel

/** 为单个 VirtualModel 选择跨 Provider 的备用入口。 */
@Composable
fun FallbackSelector(
    source: VirtualModel,
    allVirtualModels: List<VirtualModel>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text("Fallback", fontSize = 11.sp)
            TextButton(
                onClick = { expanded = true },
                enabled = candidates.isNotEmpty() || source.fallbackVirtualModelId != null
            ) {
                Text(
                    target?.let { virtual ->
                        virtual.displayName ?: virtual.name.ifBlank { ModelIdentity.catalogKey(virtual) }
                    } ?: if (candidates.isEmpty()) "无可用入口" else "未设置",
                    fontSize = 11.sp
                )
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("不使用 fallback", fontSize = 11.sp) },
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Text(
                            candidate.displayName
                                ?: candidate.name.ifBlank { ModelIdentity.catalogKey(candidate) },
                            fontSize = 11.sp
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(candidate.id)
                    }
                )
            }
        }
    }
}
