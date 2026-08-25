package com.yuzhiqiang.antigravity.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioAccountCard
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.components.StudioTextField
import com.yuzhiqiang.antigravity.ui.components.StudioTooltip
import com.yuzhiqiang.antigravity.ui.dialogs.AddAccountDialog
import com.yuzhiqiang.antigravity.ui.dialogs.QuotaRefreshConfigDialog
import com.yuzhiqiang.antigravity.ui.dialogs.SmartSwitchDialog
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioDesignTokens

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private enum class AccountsViewMode {
    GRID,
    LIST
}

private enum class AccountsSortMode {
    DEFAULT,
    QUOTA_DESC,
    EMAIL_ASC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val accounts by viewModel.accounts.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    val cliActiveEmail by viewModel.cliActiveEmail.collectAsState(initial = null)
    val ideActiveEmail by viewModel.ideActiveEmail.collectAsState(initial = null)
    val quotas by viewModel.accountQuotas.collectAsState()
    val isRefreshingQuotas by viewModel.isRefreshingQuotas.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
    val config by viewModel.config.collectAsState()
    val refreshingAccountIds by viewModel.refreshingAccountIds.collectAsState(initial = emptySet())



    LaunchedEffect(Unit) {
        viewModel.syncHostAccounts()
    }

    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(AccountsViewMode.GRID) }



    var sortMode by remember { mutableStateOf(AccountsSortMode.DEFAULT) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSmartSwitchDialog by remember { mutableStateOf(false) }
    var showQuotaRefreshConfigDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountInfo?>(null) }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val scrollState = rememberScrollState()

    // 过滤与排序 (将 App/CLI 和 IDE 活跃账号固定置顶在前两位)
    val displayAccounts = remember(accounts, searchQuery, sortMode, quotas, activeAccount, cliActiveEmail, ideActiveEmail) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) {
            accounts
        } else {
            accounts.filter { acc ->
                acc.email.lowercase().contains(query) ||
                        acc.displayName.lowercase().contains(query)
            }
        }

        fun hostActiveRank(acc: AccountInfo): Int {
            val isIde = !ideActiveEmail.isNullOrBlank() && acc.email.equals(ideActiveEmail, ignoreCase = true)
            val isCli = !cliActiveEmail.isNullOrBlank() && acc.email.equals(cliActiveEmail, ignoreCase = true)

            return when {
                isIde && isCli -> 0 // 双端共同活跃排第 1 位
                isCli -> 1          // App/CLI 活跃固定置顶前排
                isIde -> 2          // IDE 活跃固定置顶前排
                else -> 3           // 普通账号排在后续
            }
        }

        when (sortMode) {
            AccountsSortMode.DEFAULT -> {
                list.sortedWith(
                    compareBy<AccountInfo> { hostActiveRank(it) }
                        .thenByDescending { it.isActive }
                        .thenBy { it.addedAt }
                )
            }
            AccountsSortMode.QUOTA_DESC -> {
                list.sortedWith(
                    compareBy<AccountInfo> { hostActiveRank(it) }
                        .thenByDescending { quotas[it.id]?.lowestQuotaPct() ?: 0 }
                )
            }
            AccountsSortMode.EMAIL_ASC -> {
                list.sortedWith(
                    compareBy<AccountInfo> { hostActiveRank(it) }
                        .thenBy { it.email.lowercase() }
                )
            }
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.Spacing.pageHorizontal, vertical = AppTokens.Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 顶部主标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Surface(
                shape = RoundedCornerShape(AppTokens.Radius.pill),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() && displayAccounts.size != accounts.size) {
                        "${displayAccounts.size}/${accounts.size}"
                    } else {
                        "${accounts.size}"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. 现代 MD3 顶栏操作栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.card),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.8f)
            ),
            shadowElevation = AppTokens.Elevation.level1
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StudioDesignTokens.Padding.topBarHorizontal, vertical = StudioDesignTokens.Padding.topBarVertical),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧工具组: [搜索框] + [排序 Chip 胶囊]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
                ) {
                    // 搜索输入框 (Studio 沉稳精致设计)
                    StudioSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "按邮箱快速检索...",
                        modifier = Modifier
                            .width(StudioDesignTokens.Sizes.searchFieldWidth)
                            .height(StudioDesignTokens.Sizes.searchFieldHeight)
                    )

                    // 智能排序 Chip
                    StudioTooltip(text = if (sortMode == AccountsSortMode.QUOTA_DESC) "当前按剩余配额从高到低排序 (点击切换为默认排序)" else "按账号剩余配额从高到低排序") {
                        FilterChip(
                            selected = sortMode == AccountsSortMode.QUOTA_DESC,
                            onClick = {
                                sortMode = if (sortMode == AccountsSortMode.QUOTA_DESC) {
                                    AccountsSortMode.DEFAULT
                                } else {
                                    AccountsSortMode.QUOTA_DESC
                                }
                            },
                            label = {
                                Text(
                                    text = "按配额排序",
                                    fontSize = StudioDesignTokens.TextSize.label,
                                    fontWeight = if (sortMode == AccountsSortMode.QUOTA_DESC) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize)
                                )
                            },
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                            modifier = Modifier.height(StudioDesignTokens.Sizes.chipHeight),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = sortMode == AccountsSortMode.QUOTA_DESC,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // 右侧功能图标组: [MD3 添加按钮] + [辅助图标组]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm - 2.dp)
                ) {
                    // MD3 主按钮: 添加账号 (Studio 统一 Primary 品牌色)
                    StudioTooltip(text = "添加单个 Refresh Token 或批量导入多个凭据") {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.height(StudioDesignTokens.Sizes.topButtonHeight),
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                            contentPadding = PaddingValues(horizontal = AppTokens.Spacing.content, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize)
                            )
                            Spacer(modifier = Modifier.width(AppTokens.Spacing.xs))
                            Text(
                                text = "添加账号",
                                fontSize = StudioDesignTokens.TextSize.body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                        // 辅助功能胶囊工具条: [刷新] [脱敏] [导出] [智能切号]
                        Surface(
                            shape = RoundedCornerShape(StudioDesignTokens.CornerRadius.sm),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.height(StudioDesignTokens.Sizes.topButtonHeight)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                // 刷新全量配额
                                StudioTooltip(text = "立即并发刷新所有账号的最新配额水位") {
                                    IconButton(
                                        onClick = { viewModel.refreshAllQuotas() },
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.topIconButtonSize)
                                    ) {
                                        if (isRefreshingQuotas) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(StudioDesignTokens.Sizes.cardActionIconSize),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = "刷新全部配额",
                                                modifier = Modifier.size(StudioDesignTokens.Sizes.topIconInnerSize),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // 额度自动刷新设置
                                StudioTooltip(text = "配置配额自动刷新周期 (当前: 活跃 ${config.quotaActiveIntervalSeconds}s / 其他 ${config.quotaBackgroundIntervalSeconds / 60}min)") {
                                    IconButton(
                                        onClick = { showQuotaRefreshConfigDialog = true },
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.topIconButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Timer,
                                            contentDescription = "额度自动刷新设置",
                                            modifier = Modifier.size(StudioDesignTokens.Sizes.topIconInnerSize),
                                            tint = if (config.quotaAutoRefreshEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 隐私脱敏开关
                                StudioTooltip(text = if (isPrivacyMode) "关闭脱敏，显示完整邮箱地址" else "开启隐私脱敏，隐藏邮箱敏感字符") {
                                    IconButton(
                                        onClick = { viewModel.togglePrivacyMode() },
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.topIconButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = if (isPrivacyMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = "隐私模式",
                                            modifier = Modifier.size(StudioDesignTokens.Sizes.topIconInnerSize),
                                            tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 导出备份
                                StudioTooltip(text = "将全部账号凭据以 JSON 格式导出到剪贴板") {
                                    IconButton(
                                        onClick = {
                                            val exportedJson = viewModel.exportAccountsJson()
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                StringSelection(exportedJson),
                                                null
                                            )
                                            viewModel.showNotice("已将全部账号备份复制到剪贴板", NoticeKind.SUCCESS)
                                        },
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.topIconButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileUpload,
                                            contentDescription = "导出备份",
                                            modifier = Modifier.size(StudioDesignTokens.Sizes.topIconInnerSize),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 智能切号策略
                                StudioTooltip(text = "配置低配额/限流(429)时的自动故障转移切号策略") {
                                    IconButton(
                                        onClick = { showSmartSwitchDialog = true },
                                        modifier = Modifier.size(StudioDesignTokens.Sizes.topIconButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Bolt,
                                            contentDescription = "智能切号策略",
                                            modifier = Modifier.size(StudioDesignTokens.Sizes.topIconInnerSize),
                                            tint = if (config.smartSwitchConfig.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // 3. 底部可滚动区域 (账号列表 + 刷新状态栏)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 账号卡片 桌面端智能响应式自适应网格 (Responsive Adaptive Grid: 2 ~ 5 列自适应)
            if (accounts.isEmpty()) {
                EmptyAccountsCard(onAddClick = { showAddDialog = true })
            } else if (displayAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到与「$searchQuery」匹配的账号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidth = maxWidth
                    // 动态计算最佳列数 (正常桌面窗口默认一行显示 3 个卡片)
                    val columns = if (viewMode == AccountsViewMode.GRID) {
                        when {
                            availableWidth >= 1480.dp -> 4 // 宽屏/4K: 4 列
                            availableWidth >= 880.dp  -> 3 // 正常桌面窗口: 一行显示 3 个
                            availableWidth >= 580.dp  -> 2 // 分屏窗口: 2 列
                            else -> 1                      // 紧凑窄窗口: 1 列
                        }
                    } else {
                        1 // 列表模式始终单列全宽
                    }

                    val accountRows = displayAccounts.chunked(columns)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        accountRows.forEach { rowAccounts ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = androidx.compose.animation.core.spring(
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
                                        )
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                for (i in 0 until columns) {
                                    if (i < rowAccounts.size) {
                                        val acc = rowAccounts[i]
                                        val matchesIde = !ideActiveEmail.isNullOrBlank() && acc.email.equals(ideActiveEmail, ignoreCase = true)
                                        val matchesCli = !cliActiveEmail.isNullOrBlank() && acc.email.equals(cliActiveEmail, ignoreCase = true)

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .animateContentSize(
                                                    animationSpec = androidx.compose.animation.core.spring(
                                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
                                                    )
                                                )
                                        ) {
                                            StudioAccountCard(
                                                account = acc,
                                                quotaSnapshot = quotas[acc.id],
                                                isRefreshing = refreshingAccountIds.contains(acc.id),
                                                isPrivacyMode = isPrivacyMode,
                                                isIdeActive = matchesIde,
                                                isCliActive = matchesCli,
                                                onSetActive = { viewModel.setActiveAccount(acc.id) },
                                                onRefresh = {
                                                    viewModel.refreshSingleAccountQuota(acc.id)
                                                    viewModel.refreshAccountTokens(acc.email)
                                                },
                                                onCopyToken = {
                                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                        StringSelection(acc.tokens.refreshToken),
                                                        null
                                                    )
                                                    viewModel.showNotice("已复制 Refresh Token", NoticeKind.SUCCESS)
                                                },
                                                onDelete = { accountToDelete = acc }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }

    if (showQuotaRefreshConfigDialog) {
        QuotaRefreshConfigDialog(
            viewModel = viewModel,
            onDismiss = { showQuotaRefreshConfigDialog = false }
        )
    }

    if (showSmartSwitchDialog) {
        SmartSwitchDialog(
            viewModel = viewModel,
            onDismiss = { showSmartSwitchDialog = false }
        )
    }

    accountToDelete?.let { acc ->
        viewModel.showConfirmDialog(
            AppViewModel.ConfirmDialogState(
                title = "删除账号",
                message = "确定要从 Studio 移除账号「${acc.email}」吗？移除后将不再自动续期与监控额度。",
                confirmLabel = "确定删除",
                isDestructive = true,
                onConfirm = {
                    viewModel.removeAccount(acc.id)
                    accountToDelete = null
                }
            )
        )
    }
}

@Composable
private fun EmptyAccountsCard(onAddClick: () -> Unit) {
    val s = strings()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PeopleOutline,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = s.accountsEmptyState,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = s.accountsEmptyDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.accountsAddAccount)
            }
        }
    }
}
