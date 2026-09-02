package com.yuzhiqiang.antigravity.update.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseAsset(
    @SerialName("name")
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
    @SerialName("size")
    val sizeBytes: Long = 0L,
    @SerialName("content_type")
    val contentType: String = ""
)

@Serializable
data class ReleaseInfo(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("prerelease")
    val prerelease: Boolean = false,
    @SerialName("draft")
    val draft: Boolean = false,
    @SerialName("assets")
    val assets: List<ReleaseAsset> = emptyList()
) {
    /**
     * 获取规范化的纯净版本号（例如去除前缀 'v'）。
     */
    val cleanVersion: String
        get() = tagName.trim().removePrefix("v").removePrefix("V")

    /** 精确选择当前平台和架构的安装包；不匹配时返回 null，绝不回退到 Release 网页。 */
    fun resolvePlatformAsset(
        osName: String = System.getProperty("os.name", ""),
        osArch: String = System.getProperty("os.arch", "")
    ): ReleaseAsset? {
        val os = osName.lowercase()
        val arch = osArch.lowercase()
        val platform = when {
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("win") -> "windows"
            os.contains("linux") -> "linux"
            else -> return null
        }
        val architecture = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("x86_64") || arch.contains("amd64") || arch == "x64" -> "x64"
            else -> return null
        }
        val suffixes = when (platform) {
            "macos" -> listOf(".dmg", ".pkg")
            "windows" -> listOf(".exe", ".msi")
            else -> listOf(".deb", ".rpm", ".appimage", ".tar.gz")
        }
        return assets.firstOrNull { asset ->
            val assetName = asset.name.lowercase()
            asset.downloadUrl.isNotBlank() &&
                    assetName.contains("-$platform-$architecture") &&
                    suffixes.any(assetName::endsWith)
        }
    }

    fun resolvePlatformDownloadUrl(
        osName: String = System.getProperty("os.name", ""),
        osArch: String = System.getProperty("os.arch", "")
    ): String? = resolvePlatformAsset(osName, osArch)?.downloadUrl
}
