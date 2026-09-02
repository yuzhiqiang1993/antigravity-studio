package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

sealed interface DownloadProgress {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressRatio: Float,
        val speedBytesPerSec: Long
    ) : DownloadProgress

    data class Completed(val artifact: VerifiedUpdateArtifact) : DownloadProgress
}

/** 仅从受信任的 GitHub HTTPS 资产链下载并校验更新。 */
object AppUpdateDownloader {
    private const val BUFFER_SIZE = 8192
    private const val MAX_REDIRECTS = 5
    private val allowedHosts = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun download(asset: ReleaseAsset, version: String, targetFile: File): Flow<DownloadProgress> = flow {
        require(asset.name.isNotBlank() && targetFile.name == asset.name) { "Invalid update asset name" }
        require(!targetFile.exists()) { "Target update file already exists" }
        targetFile.parentFile?.mkdirs()

        val manifestUrl = siblingUrl(asset.downloadUrl, asset.name + UpdateArtifactVerifier.MANIFEST_SUFFIX)
        val signatureUrl = siblingUrl(asset.downloadUrl, asset.name + UpdateArtifactVerifier.SIGNATURE_SUFFIX)
        val manifestBytes = downloadBytes(manifestUrl)
        val signatureBytes = decodeSignature(downloadBytes(signatureUrl))
        val manifest = UpdateArtifactVerifier.parseAndVerifyManifest(
            asset = asset,
            expectedVersion = version,
            manifestBytes = manifestBytes,
            signatureBytes = signatureBytes
        )
        require(asset.sizeBytes <= 0L || asset.sizeBytes == manifest.size) { "Release asset size mismatch" }

        val partFile = File(targetFile.parentFile, ".${targetFile.name}.${UUID.randomUUID()}.part")
        try {
            downloadToPart(asset.downloadUrl, partFile) { downloaded, total, speed ->
                require(downloaded <= manifest.size) { "Downloaded asset exceeds manifest size" }
                emit(
                    DownloadProgress.Progress(
                        bytesDownloaded = downloaded,
                        totalBytes = manifest.size,
                        progressRatio = if (manifest.size > 0) downloaded.toFloat() / manifest.size else 1f,
                        speedBytesPerSec = speed
                    )
                )
            }
            UpdateArtifactVerifier.verifyArtifact(partFile, manifest, expectedName = manifest.assetName)
            moveAtomically(partFile, targetFile)
            emit(DownloadProgress.Completed(VerifiedUpdateArtifact(targetFile, manifest, manifestBytes, signatureBytes)))
        } finally {
            partFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    fun resolveTargetFile(assetName: String): File {
        require(assetName.isNotBlank() && File(assetName).name == assetName) { "Invalid update asset name" }
        val userHome = System.getProperty("user.home") ?: "."
        val downloadsDir = File(userHome, "Downloads")
        val targetDir = if (downloadsDir.isDirectory && downloadsDir.canWrite()) {
            downloadsDir
        } else {
            File(System.getProperty("java.io.tmpdir"), "antigravity_updates")
        }
        targetDir.mkdirs()
        return File(targetDir, assetName)
    }

    internal fun isAllowedAssetUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                allowedHosts.any { uri.host?.equals(it, ignoreCase = true) == true }
    }.getOrDefault(false)

    private suspend fun downloadToPart(
        url: String,
        partFile: File,
        onProgress: suspend (Long, Long, Long) -> Unit
    ) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var connection: HttpURLConnection? = null
        try {
            connection = openTrustedConnection(url)
            val total = connection.contentLengthLong
            input = connection.inputStream
            output = FileOutputStream(partFile, false)
            val buffer = ByteArray(BUFFER_SIZE)
            var downloaded = 0L
            var intervalBytes = 0L
            var lastTime = System.currentTimeMillis()
            while (true) {
                if (!coroutineContext.isActive) throw CancellationException("Download cancelled")
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                downloaded += read
                intervalBytes += read
                val now = System.currentTimeMillis()
                if (now - lastTime >= 250) {
                    val delta = now - lastTime
                    onProgress(downloaded, total, if (delta > 0) intervalBytes * 1000 / delta else 0L)
                    intervalBytes = 0L
                    lastTime = now
                }
            }
            output.fd.sync()
            onProgress(downloaded, total, 0L)
        } catch (error: Exception) {
            partFile.delete()
            throw error
        } finally {
            runCatching { output?.close() }
            runCatching { input?.close() }
            connection?.disconnect()
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = openTrustedConnection(url)
        return try {
            connection.inputStream.use { input ->
                val bytes = input.readNBytes(1024 * 1024 + 1)
                require(bytes.size <= 1024 * 1024) { "Update sidecar is too large" }
                bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openTrustedConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { hop ->
            require(isAllowedAssetUrl(currentUrl)) { "Untrusted update asset URL" }
            val connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AntigravityStudio/UpdateClient")
            when (val status = connection.responseCode) {
                in 200..299 -> return connection
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    require(hop < MAX_REDIRECTS) { "Too many update redirects" }
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect location missing")
                    currentUrl = URI(currentUrl).resolve(location).toString()
                    connection.disconnect()
                }
                else -> {
                    val message = connection.responseMessage
                    connection.disconnect()
                    error("HTTP $status: $message")
                }
            }
        }
        error("Too many update redirects")
    }

    private fun siblingUrl(downloadUrl: String, siblingName: String): String {
        require(isAllowedAssetUrl(downloadUrl)) { "Untrusted update asset URL" }
        val uri = URI(downloadUrl)
        return URI(uri.scheme, null, uri.host, -1, uri.path.substringBeforeLast('/') + "/$siblingName", null, null).toString()
    }

    private fun decodeSignature(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.US_ASCII).trim()
        return if (text.matches(Regex("[A-Za-z0-9+/=\\r\\n]+"))) {
            runCatching { Base64.getMimeDecoder().decode(text) }.getOrDefault(bytes)
        } else bytes
    }

    private fun moveAtomically(source: File, target: File) {
        require(!target.exists()) { "Target update file already exists" }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }
}
