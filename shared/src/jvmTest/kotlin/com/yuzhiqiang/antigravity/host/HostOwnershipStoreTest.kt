package com.yuzhiqiang.antigravity.host.ownership

import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import com.yuzhiqiang.antigravity.host.model.ClientIntegrationState
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.EnvironmentOwner.APP
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.EnvironmentOwner.CLI
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostOwnershipStoreTest {
    private lateinit var root: File
    private var previousRoot: File? = null
    private lateinit var previousReader: () -> Result<String?>
    private lateinit var previousWriter: (String) -> Boolean
    private lateinit var previousClearer: () -> Boolean
    private var environment: String? = null
    private var reads = 0
    private var writes = 0
    private var clears = 0
    private val endpoint = "http://127.0.0.1:8330"
    private val launchFile: File get() = File(root, "host-launch-ownership.json")
    private val legacyFile: File get() = File(root, AppDataPaths.ENVIRONMENT_RECEIPT_FILE_NAME)

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("host-ownership-test").toFile()
        previousRoot = HostOwnershipStore.receiptRootOverride
        previousReader = HostOwnershipStore.environmentReader
        previousWriter = HostOwnershipStore.environmentWriter
        previousClearer = HostOwnershipStore.environmentClearer
        HostOwnershipStore.receiptRootOverride = root
        HostOwnershipStore.environmentReader = { reads++; Result.success(environment) }
        HostOwnershipStore.environmentWriter = { value -> writes++; environment = value; true }
        HostOwnershipStore.environmentClearer = { clears++; environment = null; true }
        environment = null
        reads = 0
        writes = 0
        clears = 0
    }

    @AfterTest
    fun tearDown() {
        HostOwnershipStore.receiptRootOverride = previousRoot
        HostOwnershipStore.environmentReader = previousReader
        HostOwnershipStore.environmentWriter = previousWriter
        HostOwnershipStore.environmentClearer = previousClearer
        root.deleteRecursively()
    }

    @Test
    fun sharedEnvironmentDoesNotEnableEitherLaunchIntegration() {
        environment = endpoint
        assertEquals(endpoint, HostOwnershipStore.sharedEnvironmentEndpoint().getOrThrow())
        for (owner in listOf(APP, CLI)) {
            val inspect = HostOwnershipStore.inspectLaunchIntegration(owner, 8330)
            assertEquals(ClientIntegrationState.OFFICIAL, inspect.state)
            assertNull(inspect.configuredEndpoint)
            assertFalse(inspect.endpointMatches)
            assertFalse(inspect.canDisable)
        }
        assertEquals(1, reads)
        assertEquals(0, writes + clears)
    }

    @Test
    fun launchOwnersKeepIndependentEndpointsAndDisableIndependently() {
        environment = "https://external.example"
        HostOwnershipStore.enableLaunchIntegration(APP, 8330).getOrThrow()
        assertEquals(ClientIntegrationState.OFFICIAL, HostOwnershipStore.inspectLaunchIntegration(CLI, 8330).state)
        HostOwnershipStore.enableLaunchIntegration(CLI, 8331).getOrThrow()
        HostOwnershipStore.enableLaunchIntegration(APP, 8332).getOrThrow()
        assertEquals("http://127.0.0.1:8332", HostOwnershipStore.configuredLaunchEndpoint(APP).getOrThrow())
        assertEquals("http://127.0.0.1:8331", HostOwnershipStore.configuredLaunchEndpoint(CLI).getOrThrow())
        val raw = Json.parseToJsonElement(launchFile.readText()).jsonObject
        assertEquals(setOf("schema_version", "appEndpoint", "cliEndpoint"), raw.keys)
        assertEquals(JsonPrimitive(1), raw["schema_version"])
        HostOwnershipStore.disableLaunchIntegration(APP).getOrThrow()
        assertNull(HostOwnershipStore.configuredLaunchEndpoint(APP).getOrThrow())
        assertEquals("http://127.0.0.1:8331", HostOwnershipStore.configuredLaunchEndpoint(CLI).getOrThrow())
        HostOwnershipStore.disableLaunchIntegration(CLI).getOrThrow()
        HostOwnershipStore.disableLaunchIntegration(CLI).getOrThrow()
        assertFalse(launchFile.exists())
        assertEquals("https://external.example", environment)
        assertEquals(0, reads + writes + clears)
    }

    @Test
    fun launchInspectReportsManagedOrMismatchWithDisableAvailable() {
        HostOwnershipStore.enableLaunchIntegration(APP, 8330).getOrThrow()
        val managed = HostOwnershipStore.inspectLaunchIntegration(APP, 8330)
        assertEquals(ClientIntegrationState.MANAGED, managed.state)
        assertEquals(endpoint, managed.configuredEndpoint)
        assertTrue(managed.endpointMatches)
        assertTrue(managed.canDisable)
        val mismatch = HostOwnershipStore.inspectLaunchIntegration(APP, 8331)
        assertEquals(ClientIntegrationState.MISMATCH, mismatch.state)
        assertFalse(mismatch.endpointMatches)
        assertTrue(mismatch.canDisable)
    }

    @Test
    fun invalidPortDoesNotOverwriteExistingLaunchConfiguration() {
        HostOwnershipStore.enableLaunchIntegration(APP, 8330).getOrThrow()
        for (port in listOf(-1, 0, 65536)) {
            assertTrue(HostOwnershipStore.enableLaunchIntegration(APP, port).isFailure)
        }
        assertEquals(endpoint, HostOwnershipStore.configuredLaunchEndpoint(APP).getOrThrow())
    }

    @Test
    fun malformedOrUnsupportedLaunchReceiptIsUnavailableAndPreserved() {
        for (content in listOf("not-json", """{"schema_version":2}""", """{"schema_version":1,"appEndpoint":"https://external.example"}""")) {
            launchFile.writeText(content)
            val inspect = HostOwnershipStore.inspectLaunchIntegration(APP, 8330)
            assertEquals(ClientIntegrationState.UNAVAILABLE, inspect.state)
            assertFalse(inspect.canDisable)
            assertTrue(HostOwnershipStore.configuredLaunchEndpoint(APP).isFailure)
            assertTrue(HostOwnershipStore.enableLaunchIntegration(APP, 8330).isFailure)
            assertTrue(HostOwnershipStore.disableLaunchIntegration(APP).isFailure)
            assertEquals(content, launchFile.readText())
        }
        assertEquals(0, reads + writes + clears)
    }

    @Test
    fun unreadableLaunchReceiptIsUnavailable() {
        launchFile.mkdirs()
        assertEquals(ClientIntegrationState.UNAVAILABLE, HostOwnershipStore.inspectLaunchIntegration(APP, 8330).state)
        assertTrue(HostOwnershipStore.enableLaunchIntegration(APP, 8330).isFailure)
    }

    @Test
    fun launchReceiptSymlinkIsRejectedWithoutChangingTarget() {
        if (System.getProperty("os.name", "").lowercase().contains("win")) return
        val target = File(root, "target.json").apply { writeText("""{"schema_version":1}""") }
        Files.createSymbolicLink(launchFile.toPath(), target.toPath())
        assertEquals(ClientIntegrationState.UNAVAILABLE, HostOwnershipStore.inspectLaunchIntegration(APP, 8330).state)
        assertTrue(Files.isSymbolicLink(launchFile.toPath()))
        assertEquals("""{"schema_version":1}""", target.readText())
    }

    @Test
    fun launchOperationsNeverMigrateLegacyEnvironmentAutomatically() {
        environment = endpoint
        writeLegacyReceipt("https://original.example")
        HostOwnershipStore.inspectLaunchIntegration(APP, 8330)
        HostOwnershipStore.configuredLaunchEndpoint(APP).getOrThrow()
        HostOwnershipStore.enableLaunchIntegration(APP, 8330).getOrThrow()
        HostOwnershipStore.disableLaunchIntegration(APP).getOrThrow()
        assertTrue(legacyFile.exists())
        assertEquals(endpoint, environment)
        assertEquals(0, reads + writes + clears)
    }

    @Test
    fun migrationWithoutReceiptDoesNotTouchEnvironment() {
        environment = endpoint
        HostOwnershipStore.migrateLegacyEnvironment().getOrThrow()
        assertEquals(endpoint, environment)
        assertEquals(0, reads + writes + clears)
    }

    @Test
    fun migrationRestoresOriginalValueExactlyIncludingLoopbackOrEmptyValue() {
        for (original in listOf("https://original.example", "http://localhost:9000", "  https://original.example  ", "")) {
            environment = endpoint
            writeLegacyReceipt(original)
            HostOwnershipStore.migrateLegacyEnvironment().getOrThrow()
            assertEquals(original, environment)
            assertFalse(legacyFile.exists())
        }
        assertEquals(4, writes)
        assertEquals(0, clears)
    }

    @Test
    fun migrationUnsetsManagedEnvironmentWhenThereWasNoOriginalValue() {
        environment = endpoint
        writeLegacyReceipt(null)
        HostOwnershipStore.enableLaunchIntegration(CLI, 8331).getOrThrow()
        HostOwnershipStore.migrateLegacyEnvironment().getOrThrow()
        assertNull(environment)
        assertEquals(1, clears)
        assertFalse(legacyFile.exists())
        assertEquals("http://127.0.0.1:8331", HostOwnershipStore.configuredLaunchEndpoint(CLI).getOrThrow())
        HostOwnershipStore.migrateLegacyEnvironment().getOrThrow()
        assertEquals(1, clears)
    }

    @Test
    fun migrationPreservesExternallyChangedOrRemovedEnvironment() {
        for (external in listOf("https://external.example", "http://127.0.0.1:9999", null)) {
            environment = external
            writeLegacyReceipt("https://original.example")
            HostOwnershipStore.migrateLegacyEnvironment().getOrThrow()
            assertEquals(external, environment)
            assertFalse(legacyFile.exists())
        }
        assertEquals(0, writes + clears)
    }

    @Test
    fun failedMigrationReadPreservesLegacyReceipt() {
        writeLegacyReceipt(null)
        HostOwnershipStore.environmentReader = { Result.failure(IllegalStateException("读取失败")) }
        assertTrue(HostOwnershipStore.sharedEnvironmentEndpoint().isFailure)
        assertTrue(HostOwnershipStore.migrateLegacyEnvironment().isFailure)
        assertTrue(legacyFile.exists())
        assertEquals(0, writes + clears)
    }

    @Test
    fun failedMigrationWriteOrClearPreservesLegacyReceipt() {
        environment = endpoint
        writeLegacyReceipt("https://original.example")
        HostOwnershipStore.environmentWriter = { false }
        assertTrue(HostOwnershipStore.migrateLegacyEnvironment().isFailure)
        assertTrue(legacyFile.exists())
        assertEquals(endpoint, environment)
        writeLegacyReceipt(null)
        HostOwnershipStore.environmentClearer = { throw IllegalStateException("清理失败") }
        assertTrue(HostOwnershipStore.migrateLegacyEnvironment().isFailure)
        assertTrue(legacyFile.exists())
        assertEquals(endpoint, environment)
    }

    @Test
    fun malformedLegacyReceiptIsPreservedWithoutTouchingEnvironment() {
        legacyFile.writeText("broken")
        assertTrue(HostOwnershipStore.migrateLegacyEnvironment().isFailure)
        assertEquals("broken", legacyFile.readText())
        assertEquals(0, reads + writes + clears)
    }

    @Test
    fun compatibilityEnvironmentApiUsesFakeHooksAndIsolatedReceiptRoot() {
        HostOwnershipStore.enableEnvironment(APP, 8330).getOrThrow()
        assertEquals(endpoint, environment)
        assertTrue(legacyFile.exists())
        assertTrue(HostOwnershipStore.isEnvironmentConfigured(APP, 8330))
        HostOwnershipStore.forceResetEnvironment().getOrThrow()
        assertNull(environment)
        assertFalse(legacyFile.exists())
    }

    private fun writeLegacyReceipt(original: String?) {
        legacyFile.writeText(buildJsonObject {
            put("schema_version", JsonPrimitive(1))
            put("managedEndpoint", JsonPrimitive(endpoint))
            put("originalEndpoint", original?.let(::JsonPrimitive) ?: JsonNull)
            put("appOwner", JsonPrimitive(true))
            put("cliOwner", JsonPrimitive(true))
        }.toString())
    }
}
