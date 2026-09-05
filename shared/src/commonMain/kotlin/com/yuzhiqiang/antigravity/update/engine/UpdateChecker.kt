package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

/**
 * 远程版本更新检查服务，通过获取静态 release 元数据（latest.json）比对版本差异。
 */
object UpdateChecker {

    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 用于单元测试或外部重定向抓取实现的拦截器。
     */
    internal var httpFetcher: ((url: String, userAgent: String) -> String)? = null

    /**
     * 检查远程是否有可用新版本。
     *
     * @param currentVersion 当前应用版本（默认读取 AppVersion.CURRENT）
     * @param targetEndpoint 自定义检测元数据地址（默认读取 AppVersion.LATEST_METADATA_URL）
     * @return Result<ReleaseInfo?> 若有更新则包装 ReleaseInfo；已是最新版则包装 null；失败则为 failure
     */
    suspend fun checkUpdate(
        currentVersion: String = AppVersion.CURRENT,
        targetEndpoint: String = AppVersion.LATEST_METADATA_URL
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val userAgent = "AntigravityStudio/$currentVersion"
            val body = httpFetcher?.invoke(targetEndpoint, userAgent)
                ?: fetchUrlWithRedirects(targetEndpoint, userAgent)

            val release = json.decodeFromString<ReleaseInfo>(body)

            // 校验版本号
            val remoteVersion = release.cleanVersion
            if (SemVer.isNewer(remoteVersion, currentVersion)) {
                release
            } else {
                null
            }
        }
    }

    private fun fetchUrlWithRedirects(initialUrl: String, userAgent: String): String {
        var currentUrl = initialUrl
        for (hop in 0 until MAX_REDIRECTS) {
            val connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("User-Agent", userAgent)

            val status = connection.responseCode
            when (status) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    connection.disconnect()
                    return body
                }
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect location missing")
                    currentUrl = URI(currentUrl).resolve(location).toString()
                    connection.disconnect()
                }
                else -> {
                    val errorMsg = try {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    } catch (_: Exception) {
                        null
                    }
                    val msg = connection.responseMessage
                    connection.disconnect()
                    throw IllegalStateException("HTTP $status: ${errorMsg ?: msg}")
                }
            }
        }
        throw IllegalStateException("Too many redirects while fetching update metadata")
    }
}
