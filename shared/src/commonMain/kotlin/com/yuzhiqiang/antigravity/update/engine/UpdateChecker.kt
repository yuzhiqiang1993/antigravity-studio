package com.yuzhiqiang.antigravity.update.engine

import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 远程版本更新检查服务，与 GitHub Releases 交互并比对版本差异。
 */
object UpdateChecker {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 检查远程是否有可用新版本。
     *
     * @param currentVersion 当前应用版本（默认读取 AppVersion.CURRENT）
     * @param targetEndpoint 自定义检测接口地址（为空则默认使用 GitHub latest release 接口）
     * @return Result<ReleaseInfo?> 若有更新则包装 ReleaseInfo；已是最新版则包装 null；失败则为 failure
     */
    suspend fun checkUpdate(
        currentVersion: String = AppVersion.CURRENT,
        targetEndpoint: String = AppVersion.RELEASES_API_URL
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URI(targetEndpoint).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "AntigravityStudio/$currentVersion")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorMsg = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                connection.disconnect()
                throw IllegalStateException("HTTP $responseCode: ${errorMsg ?: connection.responseMessage}")
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
            connection.disconnect()

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
}
