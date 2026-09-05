package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import java.nio.file.Files

/** 隔离系统环境与全局收据；清理阶段绝不调用生产 reset。 */
internal class HostTestEnvironment : AutoCloseable {
    val root = Files.createTempDirectory("host-test-").toFile()
    var endpoint: String? = null
    var environmentWrites = 0
        private set
    var environmentClears = 0
        private set

    private val originalReader = HostOwnershipStore.environmentReader
    private val originalWriter = HostOwnershipStore.environmentWriter
    private val originalClearer = HostOwnershipStore.environmentClearer
    private val originalReceiptRoot = HostOwnershipStore.receiptRootOverride
    private val originalUserHome = System.getProperty("user.home")

    init {
        HostOwnershipStore.environmentReader = { Result.success(endpoint) }
        HostOwnershipStore.environmentWriter = {
            environmentWrites++
            endpoint = it
            true
        }
        HostOwnershipStore.environmentClearer = {
            environmentClears++
            endpoint = null
            true
        }
        HostOwnershipStore.receiptRootOverride = root.resolve("receipts")
        CliHostManager.configFileOverride = root.resolve("cli-settings.json")
        System.setProperty("user.home", root.absolutePath)
    }

    override fun close() {
        try {
            root.deleteRecursively()
        } finally {
            CliHostManager.configFileOverride = null
            HostOwnershipStore.environmentReader = originalReader
            HostOwnershipStore.environmentWriter = originalWriter
            HostOwnershipStore.environmentClearer = originalClearer
            HostOwnershipStore.receiptRootOverride = originalReceiptRoot
            if (originalUserHome == null) System.clearProperty("user.home")
            else System.setProperty("user.home", originalUserHome)
        }
    }
}
