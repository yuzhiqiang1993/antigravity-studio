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

    /**
     * 根据当前运行平台智能选择最匹配的安装包下载链接。
     * 若未找到平台专用包，则回退至 GitHub Release 网页链接。
     */
    fun resolvePlatformDownloadUrl(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val osArch = System.getProperty("os.arch", "").lowercase()
        val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")

        val matchedAsset = when {
            os.contains("mac") || os.contains("darwin") -> {
                // 优先寻找对应架构的 dmg/pkg，若无则寻找通用 dmg/pkg
                assets.firstOrNull { asset ->
                    val name = asset.name.lowercase()
                    (name.endsWith(".dmg") || name.endsWith(".pkg")) &&
                            (if (isArm64) name.contains("aarch64") || name.contains("arm64") else name.contains("x64") || name.contains("x86_64"))
                } ?: assets.firstOrNull { asset ->
                    val name = asset.name.lowercase()
                    name.endsWith(".dmg") || name.endsWith(".pkg")
                }
            }
            os.contains("win") -> {
                assets.firstOrNull { asset ->
                    val name = asset.name.lowercase()
                    (name.endsWith(".msi") || name.endsWith(".exe")) &&
                            (if (isArm64) name.contains("arm64") else name.contains("x64") || name.contains("x86_64") || !name.contains("arm"))
                } ?: assets.firstOrNull { asset ->
                    val name = asset.name.lowercase()
                    name.endsWith(".msi") || name.endsWith(".exe") || name.endsWith(".zip")
                }
            }
            else -> {
                // Linux / 其他
                assets.firstOrNull { asset ->
                    val name = asset.name.lowercase()
                    name.endsWith(".deb") || name.endsWith(".rpm") || name.endsWith(".appimage") || name.endsWith(".tar.gz")
                }
            }
        }

        return matchedAsset?.downloadUrl?.takeIf { it.isNotBlank() } ?: htmlUrl
    }
}
