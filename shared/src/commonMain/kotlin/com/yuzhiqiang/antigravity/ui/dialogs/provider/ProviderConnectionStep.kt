package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

    val protocolOptions = listOf(
        ProviderProtocol.OPENAI_CHAT_COMPLETIONS to "OpenAI · Chat Completions",
        ProviderProtocol.OPENAI_RESPONSES to "OpenAI · Responses",
        ProviderProtocol.ANTHROPIC_MESSAGES to "Anthropic · Messages API",
        ProviderProtocol.GEMINI_GENERATE_CONTENT to "Google · Gemini generateContent"
    )
    val selectedProtocolLabel = protocolOptions.firstOrNull { it.first == protocol }?.second.orEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                enabled = !isFetching,
                label = { Text("上游服务名称") },
                placeholder = { Text("例如 CLIProxyAPI、公司代理、DeepSeek 官方") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(AppTokens.Radius.medium),
                colors = fieldColors,
                singleLine = true
            )

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedProtocolLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isFetching,
                    label = { Text("API 协议") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = "选择协议",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isFetching) { protocolMenuExpanded = true },
                    shape = RoundedCornerShape(AppTokens.Radius.medium),
                    colors = fieldColors,
                    singleLine = true
                )
                DropdownMenu(
                    expanded = protocolMenuExpanded,
                    onDismissRequest = { protocolMenuExpanded = false },
                    modifier = Modifier.widthIn(min = 300.dp, max = 380.dp)
                ) {
                    protocolOptions.forEach { (candidateProtocol, label) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                protocolMenuExpanded = false
                                onProtocolChange(candidateProtocol)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                enabled = !isFetching,
                label = { Text("API 地址 (Base URL)") },
                placeholder = { Text("例如 https://api.openai.com/v1") },
                modifier = Modifier.weight(1.15f),
                shape = RoundedCornerShape(AppTokens.Radius.medium),
                colors = fieldColors,
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                enabled = !isFetching,
                label = { Text("API Key (选填)") },
                placeholder = { Text("输入 API Key（无鉴权则留空）") },
                modifier = Modifier.weight(0.85f),
                shape = RoundedCornerShape(AppTokens.Radius.medium),
                colors = fieldColors,
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        enabled = !isFetching,
                        onClick = { showApiKey = !showApiKey }
                    ) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = when (protocol) {
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "适用于 /v1/chat/completions；CLIProxyAPI、Sub2API 及主流 OpenAI 兼容网关。"
                        ProviderProtocol.ANTHROPIC_MESSAGES -> "适用于 Anthropic /v1/messages 协议。"
                        ProviderProtocol.GEMINI_GENERATE_CONTENT -> "适用于 Google Gemini generateContent 协议。"
                        ProviderProtocol.OPENAI_RESPONSES -> "适用于 OpenAI Responses API；请求与工具调用使用 input 事件模型。"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(AppTokens.Radius.medium)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "高级设置（自定义端点 URL）",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = modelsEndpoint,
                    onValueChange = onModelsEndpointChange,
                    enabled = !isFetching,
                    label = { Text("模型列表接口 (自定义)") },
                    placeholder = { Text("留空自动推断") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    colors = fieldColors,
                    singleLine = true
                )
                OutlinedTextField(
                    value = generateEndpoint,
                    onValueChange = onGenerateEndpointChange,
                    enabled = !isFetching,
                    label = { Text("生成响应接口 (自定义)") },
                    placeholder = { Text("留空自动推断") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    colors = fieldColors,
                    singleLine = true
                )
            }
        }

        if (fetchError != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    fetchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
