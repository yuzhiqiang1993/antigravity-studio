package com.yuzhiqiang.antigravity.services.auth

import kotlin.test.Test
import kotlin.test.assertContentEquals
import com.yuzhiqiang.antigravity.data.storage.OfficialCredentialsStore
import java.io.File
import kotlin.test.assertFalse

class SystemCredentialSnapshotTest {

    @Test
    fun presentSnapshotRedactsAndClearsSecret() {
        val original = "sensitive-secret".toByteArray()
        val snapshot = SystemCredentialSnapshot.Present(
            backend = SystemCredentialBackend.MACOS_KEYCHAIN,
            secret = original.copyOf()
        )

        assertFalse(snapshot.toString().contains("sensitive-secret"))
        snapshot.close()
        assertContentEquals(ByteArray(original.size), snapshot.secret)
    }

    @Test
    fun originalStateClearsAllSensitiveByteSnapshots() {
        val sharedBytes = "shared-secret".toByteArray()
        val jetskiBytes = "jetski-secret".toByteArray()
        val systemBytes = "system-secret".toByteArray()
        val state = OriginalState(
            ideSnapshot = null,
            appDbSnapshot = null,
            sharedCredentialsSnapshot = OfficialCredentialsStore.Snapshot(
                listOf(
                    OfficialCredentialsStore.FileSnapshot(
                        file = File("shared.json"),
                        existed = true,
                        originalBytes = sharedBytes
                    )
                )
            ),
            jetskiTokenSnapshot = FileSnapshot(
                file = File("jetski.json"),
                existed = true,
                originalBytes = jetskiBytes
            ),
            systemCredentialSnapshot = SystemCredentialSnapshot.Present(
                backend = SystemCredentialBackend.MACOS_KEYCHAIN,
                secret = systemBytes
            )
        )

        state.close()

        assertContentEquals(ByteArray(sharedBytes.size), sharedBytes)
        assertContentEquals(ByteArray(jetskiBytes.size), jetskiBytes)
        assertContentEquals(ByteArray(systemBytes.size), systemBytes)
    }
}
