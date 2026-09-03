package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.account.*
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

class SmartSwitchCoordinatorTest {

    private lateinit var tempDir: File
    private lateinit var accountStore: AccountStore
    private lateinit var configStore: ConfigStore
    private lateinit var hotSwitchCoordinator: HotSwitchCoordinator

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("smart_switch_test_", "_dir").apply {
            delete()
            mkdirs()
        }
        accountStore = AccountStore(customRootDir = tempDir)
        configStore = ConfigStore(customRootDir = tempDir)
        hotSwitchCoordinator = HotSwitchCoordinator(accountStore)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testWorkflowLeaseManager() = runBlocking {
        val inhibitorDir = File(tempDir, "inhibitors").apply { mkdirs() }
        WorkflowLeaseManager.customInhibitorDir = inhibitorDir
        try {
            assertFalse(WorkflowLeaseManager.isLocked())

            val lease1 = WorkflowLeaseManager.acquireLease(10_000L)
            assertTrue(WorkflowLeaseManager.isLocked())
            val files = inhibitorDir.listFiles()?.filter { it.name.endsWith(".json") }
            assertEquals(1, files?.size)

            WorkflowLeaseManager.releaseLease(lease1)
            assertFalse(WorkflowLeaseManager.isLocked())
            val remainingFiles = inhibitorDir.listFiles()?.filter { it.name.endsWith(".json") }
            assertEquals(0, remainingFiles?.size)
        } finally {
            WorkflowLeaseManager.customInhibitorDir = null
        }
    }

    @Test
    fun testModelQuotaRounding() {
        val quota1 = ModelQuotaInfo("claude", "Claude", remainingFraction = 0.496)
        assertEquals(50, quota1.percentage)

        val quota2 = ModelQuotaInfo("claude", "Claude", remainingFraction = 0.494)
        assertEquals(49, quota2.percentage)
    }

    @Test
    fun testSmartSwitchDisabledByDefault() = runBlocking {
        val coordinator = SmartSwitchCoordinator(
            accountStore = accountStore,
            configStore = configStore,
            hotSwitchCoordinator = hotSwitchCoordinator,
            quotasProvider = { emptyMap() }
        )

        val outcome = coordinator.trySmartSwitchOn429("claude-3-7-sonnet")
        assertFalse(outcome.triggered)
        assertTrue(outcome.reason?.contains("未启用") == true)
    }

    @Test
    fun testSmartSwitchSelectsHighestQuotaCandidateOn429() = runBlocking {
        // 启用智能切号
        configStore.updateConfig {
            it.copy(
                smartSwitchConfig = SmartSwitchConfig(
                    enabled = true,
                    triggerThresholdPercent = 5,
                    strategy = SmartSwitchStrategy.HIGHEST_QUOTA_FIRST,
                    cooldownSeconds = 0 // 测试中不等待冷却
                )
            )
        }

        val account1 = AccountInfo(
            id = "acc_1",
            profile = AccountProfile("depleted@antigravity.ai"),
            tokens = OAuthTokens("a1", "r1", System.currentTimeMillis() / 1000L + 3600L),
            isActive = true
        )
        val account2Low = AccountInfo(
            id = "acc_2_low",
            profile = AccountProfile("low@antigravity.ai"),
            tokens = OAuthTokens("a2", "r2", System.currentTimeMillis() / 1000L + 3600L),
            isActive = false
        )
        val account3High = AccountInfo(
            id = "acc_3_high",
            profile = AccountProfile("high@antigravity.ai"),
            tokens = OAuthTokens("a3", "r3", System.currentTimeMillis() / 1000L + 3600L),
            isActive = false
        )

        accountStore.upsertAccount(account1)
        accountStore.upsertAccount(account2Low)
        accountStore.upsertAccount(account3High)

        val mockQuotas = mapOf(
            "acc_1" to AccountQuotaSnapshot(
                accountId = "acc_1",
                email = "depleted@antigravity.ai",
                models = listOf(ModelQuotaInfo("claude-3-7-sonnet", "Claude 3.7", remainingFraction = 0.0))
            ),
            "acc_2_low" to AccountQuotaSnapshot(
                accountId = "acc_2_low",
                email = "low@antigravity.ai",
                models = listOf(ModelQuotaInfo("claude-3-7-sonnet", "Claude 3.7", remainingFraction = 0.20))
            ),
            "acc_3_high" to AccountQuotaSnapshot(
                accountId = "acc_3_high",
                email = "high@antigravity.ai",
                models = listOf(ModelQuotaInfo("claude-3-7-sonnet", "Claude 3.7", remainingFraction = 0.95))
            )
        )

        val coordinator = SmartSwitchCoordinator(
            accountStore = accountStore,
            configStore = configStore,
            hotSwitchCoordinator = hotSwitchCoordinator,
            quotasProvider = { mockQuotas }
        )

        val outcome = coordinator.trySmartSwitchOn429("claude-3-7-sonnet")
        assertFalse(outcome.triggered)
        assertTrue(outcome.requiresUserAction)
        assertEquals("acc_3_high", outcome.targetAccount?.id)
        assertEquals("depleted@antigravity.ai", accountStore.currentActiveAccount()?.email)
    }

    @Test
    fun testRoundRobinSmartSwitch() = runBlocking {
        configStore.updateConfig {
            it.copy(
                smartSwitchConfig = SmartSwitchConfig(
                    enabled = true,
                    triggerThresholdPercent = 5,
                    strategy = SmartSwitchStrategy.ROUND_ROBIN,
                    cooldownSeconds = 0
                )
            )
        }

        val account1 = AccountInfo(
            id = "acc_1",
            profile = AccountProfile("depleted@antigravity.ai"),
            tokens = OAuthTokens("a1", "r1", System.currentTimeMillis() / 1000L + 3600L),
            isActive = true
        )
        val account2 = AccountInfo(
            id = "acc_2",
            profile = AccountProfile("two@antigravity.ai"),
            tokens = OAuthTokens("a2", "r2", System.currentTimeMillis() / 1000L + 3600L),
            isActive = false
        )
        val account3 = AccountInfo(
            id = "acc_3",
            profile = AccountProfile("three@antigravity.ai"),
            tokens = OAuthTokens("a3", "r3", System.currentTimeMillis() / 1000L + 3600L),
            isActive = false
        )

        accountStore.upsertAccount(account1)
        accountStore.upsertAccount(account2)
        accountStore.upsertAccount(account3)

        val coordinator = SmartSwitchCoordinator(
            accountStore = accountStore,
            configStore = configStore,
            hotSwitchCoordinator = hotSwitchCoordinator,
            quotasProvider = { emptyMap() }
        )

        val outcome1 = coordinator.trySmartSwitchOn429(null)
        val outcome2 = coordinator.trySmartSwitchOn429(null)

        val selectedIds = listOf(outcome1.targetAccount?.id, outcome2.targetAccount?.id)
        assertTrue(selectedIds.contains("acc_2"))
        assertTrue(selectedIds.contains("acc_3"))
        assertNotEquals(outcome1.targetAccount?.id, outcome2.targetAccount?.id)
    }
}
