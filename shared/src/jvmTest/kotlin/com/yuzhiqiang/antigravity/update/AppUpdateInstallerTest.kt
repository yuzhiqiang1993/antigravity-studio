package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.engine.AppUpdateInstaller
import com.yuzhiqiang.antigravity.update.engine.UpdateManifest
import com.yuzhiqiang.antigravity.update.engine.VerifiedUpdateArtifact
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class AppUpdateInstallerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("installer_test_").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testLaunchInstallerFailsOnTamperedArtifact() = runBlocking {
        val file = File(tempDir, "installer.dmg")
        file.writeBytes("unverified-content".toByteArray())

        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "installer.dmg",
            sha256 = "0".repeat(64),
            size = file.length()
        )
        val manifestBytes = Json.encodeToString(UpdateManifest.serializer(), manifest).toByteArray()
        val fakeSig = ByteArray(64) { 0 }

        val artifact = VerifiedUpdateArtifact(
            file = file,
            manifest = manifest,
            manifestBytes = manifestBytes,
            signatureBytes = fakeSig
        )

        val result = AppUpdateInstaller.launchInstaller(artifact)
        assertTrue(result.isFailure, "launchInstaller must fail when signature is invalid")
    }

    @Test
    fun testShowInFolderNonExistentFileReturnsSuccessSafely() = runBlocking {
        val missing = File(tempDir, "does_not_exist.dmg")
        val result = AppUpdateInstaller.showInFolder(missing)
        assertTrue(result.isSuccess)
    }
}
