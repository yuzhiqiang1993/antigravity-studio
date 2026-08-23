package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun ProviderConnectionStep(
    name: String,
    onNameChange: (String) -> Unit,
    protocol: ProviderProtocol,
    onProtocolChange: (ProviderProtocol) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modelsEndpoint: String,
    onModelsEndpointChange: (String) -> Unit,
    generateEndpoint: String,
    onGenerateEndpointChange: (String) -> Unit,
    fetchError: String?,
    isFetching: Boolean,
    modifier: Modifier = Modifier
) {
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showAdvancedEndpoints by remember { mutableStateOf(modelsEndpoint.isNotBlank() || generateEndpoint.isNotBlank()) }

    val protocolOptions = listOf(
        ProviderProtocol.OPENAI_CHAT_COMPLETIONS to "OpenAI · Chat Completions",
        ProviderProtocol.OPENAI_RESPONSES to "OpenAI · Responses API",
        ProviderProtocol.ANTHROPIC_MESSAGES to "Anthropic · Messages API",
        ProviderProtocol.GEMINI_GENERATE_CONTENT to "Google · Gemini generateContent"
    )
    val selectedProtocolLabel = protocolOptions.firstOrNull { it.first == protocol }?.second.orEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "上游服务名称",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "*",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                StudioCustomTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "例如 CLIProxyAPI、公司代理、DeepSeek",
                    enabled = !isFetching
                )
                Text(
                    text = "自定义显示名称，用于在路由和模型列表区分服务来源",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "API 协议",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    StudioSelectField(
                        label = selectedProtocolLabel,
                        isExpanded = protocolMenuExpanded,
                        onClick = { protocolMenuExpanded = true },
                        enabled = !isFetching
                    )
                    DropdownMenu(
                        expanded = protocolMenuExpanded,
                        onDismissRequest = { protocolMenuExpanded = false },
                        shape = RoundedCornerShape(10.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier.widthIn(min = 340.dp, max = 420.dp).padding(4.dp)
                    ) {
                        protocolOptions.forEach { (candidateProtocol, label) ->
                            val isSelected = candidateProtocol == protocol
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isItemHovered by itemInteraction.collectIsHoveredAsState()
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                            val itemBg = when {
                                isSelected -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.65f) else Color(0xFFDBEAFE)
                                isItemHovered -> if (isDark) Color(0xFF3B82F6).copy(alpha = 0.12f) else Color(0xFF2563EB).copy(alpha = 0.07f)
                                else -> Color.Transparent
                            }
                            val itemTextCol = when {
                                isSelected -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(itemBg)
                                    .hoverable(itemInteraction)
                                    .clickable {
                                    protocolMenuExpanded = false
                                    onProtocolChange(candidateProtocol)
                                }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = itemTextCol
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = itemTextCol,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = when (protocol) {
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "适用于 /v1/chat/completions；CLIProxyAPI、Sub2API 及标准兼容网关"
                        ProviderProtocol.ANTHROPIC_MESSAGES -> "适用于 Anthropic 官方 /v1/messages 协议"
                        ProviderProtocol.GEMINI_GENERATE_CONTENT -> "适用于 Google Gemini 官方 generateContent 协议"
                        ProviderProtocol.OPENAI_RESPONSES -> "适用于 OpenAI Responses API 原生结构协议"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "API 地址 (Base URL)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "*",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                StudioCustomTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    placeholder = "例如 https://api.openai.com/v1",
                    enabled = !isFetching
                )
                Text(
                    text = "输入根地址后系统将自动推断补全模型列表与生成响应接口",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "选填",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StudioCustomTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    placeholder = "输入 API Key（无鉴权则留空）",
                    enabled = !isFetching,
                    isPassword = true,
                    showPasswordToggle = true,
                    isPasswordVisible = showApiKey,
                    onPasswordToggle = { showApiKey = !showApiKey }
                )
                Text(
                    text = "如服务无需鉴权可留空",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        val chevronRotation by animateFloatAsState(
            targetValue = if (showAdvancedEndpoints) 90f else 0f,
            animationSpec = tween(AppTokens.Motion.durationShort)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(9.dp)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedEndpoints = !showAdvancedEndpoints }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation }
                        )
                        Text(
                            "高级设置（自定义端点 URL）",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (showAdvancedEndpoints) "收起" else "默认由 Base URL 自动生成",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedEndpoints) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "模型列表接口 (自定义)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StudioCustomTextField(
                                value = modelsEndpoint,
                                onValueChange = onModelsEndpointChange,
                                placeholder = "留空自动推断",
                                enabled = !isFetching
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "生成响应接口 (自定义)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StudioCustomTextField(
                                value = generateEndpoint,
                                onValueChange = onGenerateEndpointChange,
                                placeholder = "留空自动推断",
                                enabled = !isFetching
                            )
                        }
                    }
                }
            }
        }

        if (fetchError != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        fetchError,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 桌面端紧凑标准单行输入框组件 (固定高度 38.dp)
 */
@Composable
private fun StudioCustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    isPassword: Boolean = false,
    showPasswordToggle: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val borderColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isHovered -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1) // Slate 300 明确边框
    }
    val bgColor = when {
        isFocused -> MaterialTheme.colorScheme.surface
        isHovered -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        else -> if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF8FAFC) // 浅灰凹底色，增强可输入区域感知
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .hoverable(interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    }
                    innerTextField()
                }
                if (showPasswordToggle && onPasswordToggle != null) {
                    IconButton(
                        onClick = onPasswordToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

/**
 * 桌面端紧凑下拉选择框组件 (固定高度 38.dp)
 */
@Composable
private fun StudioSelectField(
    label: String,
    isExpanded: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(AppTokens.Motion.durationShort)
    )
    val borderColor = when {
        isExpanded -> MaterialTheme.colorScheme.primary
        isHovered -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    }
    val bgColor = when {
        isExpanded -> MaterialTheme.colorScheme.surface
        isHovered -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        else -> if (isDark) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(8.dp))
            .border(if (isExpanded) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "选择",
                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}
