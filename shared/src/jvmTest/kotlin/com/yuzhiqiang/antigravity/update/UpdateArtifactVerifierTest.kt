package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.engine.UpdateArtifactVerifier
import com.yuzhiqiang.antigravity.update.engine.UpdateManifest
import com.yuzhiqiang.antigravity.update.engine.VerifiedUpdateArtifact
import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import kotlinx.serialization.json.Json

class UpdateArtifactVerifierTest {

    private lateinit var keyPair: KeyPair
    private lateinit var publicKeyPem: String
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        keyPair = kpg.generateKeyPair()
        val pubEncoded = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n$pubEncoded\n-----END PUBLIC KEY-----"
        tempDir = createTempDirectory("verifier_test_").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sign(data: ByteArray, kp: KeyPair = keyPair): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(kp.private)
        sig.update(data)
        return sig.sign()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testBundledPublicKeyIsLoadableAndValid() {
        // Bundled key in resources must load without throwing
        val resourceStream = UpdateArtifactVerifier::class.java.getResourceAsStream("/update/release-signing-public-key.pem")
        assertNotNull(resourceStream, "Bundled public key resource must exist")
        val pem = resourceStream.bufferedReader().use { it.readText() }
        assertTrue(pem.contains("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.contains("-----END PUBLIC KEY-----"))
    }

    @Test
    fun testVerifySignatureSuccessAndFailures() {
        val data = "test-manifest-data-12345".toByteArray(Charsets.UTF_8)
        val signature = sign(data)

        // Valid signature
        assertTrue(UpdateArtifactVerifier.verifySignature(data, signature, publicKeyPem))

        // Tampered data
        val tamperedData = "test-manifest-data-12346".toByteArray(Charsets.UTF_8)
        assertFalse(UpdateArtifactVerifier.verifySignature(tamperedData, signature, publicKeyPem))

        // Corrupted signature bytes
        val corruptedSig = signature.copyOf()
        corruptedSig[0] = (corruptedSig[0].toInt() xor 0xFF).toByte()
        assertFalse(UpdateArtifactVerifier.verifySignature(data, corruptedSig, publicKeyPem))

        // Truncated signature
        val truncatedSig = signature.copyOf(16)
        assertFalse(UpdateArtifactVerifier.verifySignature(data, truncatedSig, publicKeyPem))

        // Signature signed by different keypair
        val otherKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherSig = sign(data, otherKp)
        assertFalse(UpdateArtifactVerifier.verifySignature(data, otherSig, publicKeyPem))

        // Malformed PEM
        assertFalse(UpdateArtifactVerifier.verifySignature(data, signature, "invalid-pem-content"))
    }

    @Test
    fun testParseAndVerifyManifestSuccess() {
        val assetName = "antigravity-studio-1.0.0-macos-arm64.dmg"
        val version = "1.0.0"
        val payload = "dummy dmg content bytes".toByteArray(Charsets.UTF_8)
        val sha256 = sha256Hex(payload)
        val size = payload.size.toLong()

        val manifestJson = """
            {
                "version": "$version",
                "assetName": "$assetName",
                "sha256": "$sha256",
                "size": $size
            }
        """.trimIndent()
        val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
        val signatureBytes = sign(manifestBytes)

        val asset = ReleaseAsset(name = assetName, sizeBytes = size)
        val manifest = UpdateArtifactVerifier.parseAndVerifyManifest(
            asset = asset,
            expectedVersion = version,
            manifestBytes = manifestBytes,
            signatureBytes = signatureBytes,
            publicKeyPem = publicKeyPem
        )

        assertEquals(version, manifest.version)
        assertEquals(assetName, manifest.assetName)
        assertEquals(sha256.lowercase(), manifest.sha256)
        assertEquals(size, manifest.size)
    }

    @Test
    fun testParseAndVerifyManifestVersionNormalization() {
        val assetName = "app-linux-x64.tar.gz"
        val payload = "some content".toByteArray()
        val sha256 = sha256Hex(payload)

        // Manifest has 'v' prefix, expected does not
        val manifestJson1 = """
            {
                "version": "v2.1.0",
                "assetName": "$assetName",
                "sha256": "$sha256",
                "size": 12
            }
        """.trimIndent()
        val bytes1 = manifestJson1.toByteArray(Charsets.UTF_8)
        val sig1 = sign(bytes1)
        val asset = ReleaseAsset(name = assetName, sizeBytes = 12)

        val result1 = UpdateArtifactVerifier.parseAndVerifyManifest(
            asset = asset,
            expectedVersion = "2.1.0",
            manifestBytes = bytes1,
            signatureBytes = sig1,
            publicKeyPem = publicKeyPem
        )
        assertEquals("v2.1.0", result1.version)

        // Manifest has no 'v', expected has 'V'
        val manifestJson2 = """
            {
                "version": "2.1.0",
                "assetName": "$assetName",
                "sha256": "$sha256",
                "size": 12
            }
        """.trimIndent()
        val bytes2 = manifestJson2.toByteArray(Charsets.UTF_8)
        val sig2 = sign(bytes2)

        val result2 = UpdateArtifactVerifier.parseAndVerifyManifest(
            asset = asset,
            expectedVersion = "V2.1.0",
            manifestBytes = bytes2,
            signatureBytes = sig2,
            publicKeyPem = publicKeyPem
        )
        assertEquals("2.1.0", result2.version)
    }

    @Test
    fun testParseAndVerifyManifestInvalidSignatureThrows() {
        val assetName = "app.dmg"
        val manifestJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"${"0".repeat(64)}","size":100}"""
        val bytes = manifestJson.toByteArray(Charsets.UTF_8)
        val invalidSig = ByteArray(64) { 0 }
        val asset = ReleaseAsset(name = assetName, sizeBytes = 100)

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(
                asset = asset,
                expectedVersion = "1.0.0",
                manifestBytes = bytes,
                signatureBytes = invalidSig,
                publicKeyPem = publicKeyPem
            )
        }
        assertTrue(ex.message!!.contains("signature is invalid"))
    }

    @Test
    fun testParseAndVerifyManifestAssetNameMismatchThrows() {
        val manifestJson = """{"version":"1.0.0","assetName":"other-app.dmg","sha256":"${"a".repeat(64)}","size":100}"""
        val bytes = manifestJson.toByteArray(Charsets.UTF_8)
        val sig = sign(bytes)
        val asset = ReleaseAsset(name = "expected-app.dmg", sizeBytes = 100)

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(
                asset = asset,
                expectedVersion = "1.0.0",
                manifestBytes = bytes,
                signatureBytes = sig,
                publicKeyPem = publicKeyPem
            )
        }
        assertTrue(ex.message!!.contains("asset name mismatch"))
    }

    @Test
    fun testParseAndVerifyManifestVersionMismatchThrows() {
        val assetName = "app.dmg"
        val manifestJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"${"a".repeat(64)}","size":100}"""
        val bytes = manifestJson.toByteArray(Charsets.UTF_8)
        val sig = sign(bytes)
        val asset = ReleaseAsset(name = assetName, sizeBytes = 100)

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(
                asset = asset,
                expectedVersion = "1.1.0",
                manifestBytes = bytes,
                signatureBytes = sig,
                publicKeyPem = publicKeyPem
            )
        }
        assertTrue(ex.message!!.contains("version mismatch"))
    }

    @Test
    fun testParseAndVerifyManifestInvalidSizeThrows() {
        val assetName = "app.dmg"
        val manifestJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"${"a".repeat(64)}","size":-5}"""
        val bytes = manifestJson.toByteArray(Charsets.UTF_8)
        val sig = sign(bytes)
        val asset = ReleaseAsset(name = assetName, sizeBytes = -5)

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(
                asset = asset,
                expectedVersion = "1.0.0",
                manifestBytes = bytes,
                signatureBytes = sig,
                publicKeyPem = publicKeyPem
            )
        }
        assertTrue(ex.message!!.contains("size is invalid"))
    }

    @Test
    fun testParseAndVerifyManifestInvalidSha256Throws() {
        val assetName = "app.dmg"
        val asset = ReleaseAsset(name = assetName, sizeBytes = 100)

        // Invalid characters
        val badCharsJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"${"g".repeat(64)}","size":100}"""
        val bytes1 = badCharsJson.toByteArray(Charsets.UTF_8)
        val sig1 = sign(bytes1)
        assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(asset, "1.0.0", bytes1, sig1, publicKeyPem)
        }

        // Too short (63 chars)
        val shortShaJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"${"a".repeat(63)}","size":100}"""
        val bytes2 = shortShaJson.toByteArray(Charsets.UTF_8)
        val sig2 = sign(bytes2)
        assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(asset, "1.0.0", bytes2, sig2, publicKeyPem)
        }

        // Empty sha256
        val emptyShaJson = """{"version":"1.0.0","assetName":"$assetName","sha256":"","size":100}"""
        val bytes3 = emptyShaJson.toByteArray(Charsets.UTF_8)
        val sig3 = sign(bytes3)
        assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.parseAndVerifyManifest(asset, "1.0.0", bytes3, sig3, publicKeyPem)
        }
    }

    @Test
    fun testVerifyArtifactSuccess() {
        val file = File(tempDir, "app.dmg")
        val content = "valid application payload".toByteArray(Charsets.UTF_8)
        file.writeBytes(content)

        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "app.dmg",
            sha256 = sha256Hex(content),
            size = content.size.toLong()
        )

        // Should succeed without throwing
        UpdateArtifactVerifier.verifyArtifact(file, manifest)
    }

    @Test
    fun testVerifyArtifactMissingFileThrows() {
        val missingFile = File(tempDir, "non_existent.dmg")
        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "non_existent.dmg",
            sha256 = "0".repeat(64),
            size = 0L
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyArtifact(missingFile, manifest)
        }
        assertTrue(ex.message!!.contains("missing"))
    }

    @Test
    fun testVerifyArtifactDirectoryThrows() {
        val subDir = File(tempDir, "sub_dir.dmg")
        subDir.mkdir()
        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "sub_dir.dmg",
            sha256 = "0".repeat(64),
            size = 0L
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyArtifact(subDir, manifest)
        }
        assertTrue(ex.message!!.contains("missing"))
    }

    @Test
    fun testVerifyArtifactNameMismatchThrows() {
        val file = File(tempDir, "actual_name.dmg")
        file.writeBytes("data".toByteArray())
        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "expected_name.dmg",
            sha256 = sha256Hex("data".toByteArray()),
            size = 4L
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyArtifact(file, manifest)
        }
        assertTrue(ex.message!!.contains("asset name mismatch"))
    }

    @Test
    fun testVerifyArtifactSizeMismatchThrows() {
        val file = File(tempDir, "app.dmg")
        file.writeBytes("12345678".toByteArray())
        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "app.dmg",
            sha256 = sha256Hex("12345678".toByteArray()),
            size = 10L // Actual is 8
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyArtifact(file, manifest)
        }
        assertTrue(ex.message!!.contains("size mismatch"))
    }

    @Test
    fun testVerifyArtifactSha256MismatchThrows() {
        val file = File(tempDir, "app.dmg")
        file.writeBytes("actual file content".toByteArray())
        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "app.dmg",
            sha256 = sha256Hex("different file content".toByteArray()),
            size = file.length()
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyArtifact(file, manifest)
        }
        assertTrue(ex.message!!.contains("SHA-256 mismatch"))
    }

    @Test
    fun testVerifyVerifiedArtifactSuccessAndSecondaryTamperDetection() {
        val file = File(tempDir, "app.dmg")
        val content = "verified installer binary data".toByteArray(Charsets.UTF_8)
        file.writeBytes(content)

        val manifest = UpdateManifest(
            version = "1.0.0",
            assetName = "app.dmg",
            sha256 = sha256Hex(content),
            size = content.size.toLong()
        )
        val manifestBytes = Json.encodeToString(UpdateManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)

        val fakeSig = ByteArray(64) { 1 }
        val artifact = VerifiedUpdateArtifact(
            file = file,
            manifest = manifest,
            manifestBytes = manifestBytes,
            signatureBytes = fakeSig
        )

        // With invalid signature against bundled key, verifyVerifiedArtifact must throw
        val ex = assertFailsWith<IllegalArgumentException> {
            UpdateArtifactVerifier.verifyVerifiedArtifact(artifact)
        }
        assertTrue(ex.message!!.contains("signature is invalid"))
    }

    @Test
    fun testSha256Calculation() {
        val file = File(tempDir, "sample.txt")
        val text = "Antigravity Studio SHA-256 Test String"
        file.writeText(text)

        val expectedSha = sha256Hex(text.toByteArray(Charsets.UTF_8))
        val computedSha = UpdateArtifactVerifier.sha256(file)

        assertEquals(expectedSha, computedSha)
    }
}
