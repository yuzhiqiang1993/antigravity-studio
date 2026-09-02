package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdateManifest(
    val version: String,
    val assetName: String,
    val sha256: String,
    val size: Long
)

data class VerifiedUpdateArtifact(
    val file: File,
    val manifest: UpdateManifest,
    val manifestBytes: ByteArray,
    val signatureBytes: ByteArray
)

/** 校验发布方签名以及安装包与 manifest 的完整绑定。 */
object UpdateArtifactVerifier {
    const val MANIFEST_SUFFIX = ".manifest.json"
    const val SIGNATURE_SUFFIX = ".manifest.json.sig"
    private const val PUBLIC_KEY_RESOURCE = "/update/release-signing-public-key.pem"
    private val json = Json { ignoreUnknownKeys = false }

    fun parseAndVerifyManifest(
        asset: ReleaseAsset,
        expectedVersion: String,
        manifestBytes: ByteArray,
        signatureBytes: ByteArray,
        publicKeyPem: String = loadPublicKeyPem()
    ): UpdateManifest {
        require(verifySignature(manifestBytes, signatureBytes, publicKeyPem)) { "Update manifest signature is invalid" }
        val manifest = json.decodeFromString<UpdateManifest>(manifestBytes.toString(Charsets.UTF_8))
        require(manifest.assetName == asset.name) { "Manifest asset name mismatch" }
        require(normalizeVersion(manifest.version) == normalizeVersion(expectedVersion)) { "Manifest version mismatch" }
        require(manifest.size >= 0L) { "Manifest size is invalid" }
        require(manifest.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Manifest SHA-256 is invalid" }
        return manifest.copy(sha256 = manifest.sha256.lowercase())
    }

    fun verifyArtifact(file: File, manifest: UpdateManifest, expectedName: String = file.name) {
        require(file.isFile) { "Update artifact is missing" }
        require(expectedName == manifest.assetName) { "Downloaded asset name mismatch" }
        require(file.length() == manifest.size) { "Downloaded asset size mismatch" }
        require(sha256(file).equals(manifest.sha256, ignoreCase = true)) { "Downloaded asset SHA-256 mismatch" }
    }

    fun verifyVerifiedArtifact(artifact: VerifiedUpdateArtifact) {
        require(verifySignature(artifact.manifestBytes, artifact.signatureBytes, loadPublicKeyPem())) {
            "Update manifest signature is invalid"
        }
        val reparsed = json.decodeFromString<UpdateManifest>(artifact.manifestBytes.toString(Charsets.UTF_8))
        require(reparsed.copy(sha256 = reparsed.sha256.lowercase()) == artifact.manifest) { "Stored manifest changed" }
        verifyArtifact(artifact.file, artifact.manifest)
    }

    fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKeyPem: String): Boolean = runCatching {
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(decodePem(publicKeyPem, "PUBLIC KEY"))
        )
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(data)
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun loadPublicKeyPem(): String = UpdateArtifactVerifier::class.java
        .getResourceAsStream(PUBLIC_KEY_RESOURCE)
        ?.bufferedReader(Charsets.US_ASCII)
        ?.use { it.readText() }
        ?: error("Bundled update signing public key is missing")

    private fun normalizeVersion(version: String): String = version.trim().removePrefix("v").removePrefix("V")

    private fun decodePem(pem: String, type: String): ByteArray = Base64.getMimeDecoder().decode(
        pem.replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "")
            .filterNot(Char::isWhitespace)
    )
}
