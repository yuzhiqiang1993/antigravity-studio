package com.yuzhiqiang.antigravity.update.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 下载进度数据模型
 */
sealed interface DownloadProgress {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressRatio: Float,
        val speedBytesPerSec: Long
    ) : DownloadProgress

    data class Completed(
        val targetFile: File
    ) : DownloadProgress
}

/**
 * 远程安装包下载器，支持 302 重定向、实时速率与进度流
 */
object AppUpdateDownloader {

    private const val BUFFER_SIZE = 8192
    private const val MAX_REDIRECTS = 5

    /**
     * 流式下载指定 URL 资产到本地文件
     */
    fun download(
        downloadUrl: String,
        targetFile: File
    ): Flow<DownloadProgress> = flow {
        var currentUrl = downloadUrl
        var redirects = 0
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            while (redirects < MAX_REDIRECTS) {
                val url = URI(currentUrl).toURL()
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "AntigravityStudio/UpdateClient")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect location missing")
                    currentUrl = location
                    connection.disconnect()
                    redirects++
                } else if (status in 200..299) {
                    break
                } else {
                    throw IllegalStateException("HTTP $status: ${connection.responseMessage}")
                }
            }

            val conn = connection ?: throw IllegalStateException("Failed to establish connection")
            val totalBytes = conn.contentLengthLong

            // 确保父目录存在
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            inputStream = conn.inputStream
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = 0L
            var lastEmittedTime = System.currentTimeMillis()
            var bytesSinceLastSpeedCalc = 0L
            var currentSpeed = 0L

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) {
                    throw CancellationException("Download cancelled")
                }

                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                bytesSinceLastSpeedCalc += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastEmittedTime
                if (delta >= 250) {
                    if (delta > 0) {
                        currentSpeed = (bytesSinceLastSpeedCalc * 1000) / delta
                    }
                    val ratio = if (totalBytes > 0) {
                        (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                    } else {
                        -1f // 未知长度
                    }

                    emit(
                        DownloadProgress.Progress(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            progressRatio = ratio,
                            speedBytesPerSec = currentSpeed
                        )
                    )

                    lastEmittedTime = now
                    bytesSinceLastSpeedCalc = 0L
                }
            }

            outputStream.flush()
            emit(DownloadProgress.Completed(targetFile))

        } catch (e: Exception) {
            // 下载异常时清理残留文件
            if (targetFile.exists()) {
                try { targetFile.delete() } catch (_: Exception) {}
            }
            throw e
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 针对指定 Release 资产生成系统下载目录下的推荐保存路径
     */
    fun resolveTargetFile(assetName: String): File {
        val userHome = System.getProperty("user.home") ?: "."
        val downloadsDir = File(userHome, "Downloads")
        val targetDir = if (downloadsDir.exists() && downloadsDir.isDirectory && downloadsDir.canWrite()) {
            downloadsDir
        } else {
            File(System.getProperty("java.io.tmpdir"), "antigravity_updates")
        }
        targetDir.mkdirs()
        return File(targetDir, assetName)
    }
}
