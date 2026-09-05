package com.yuzhiqiang.antigravity.update.model

import com.yuzhiqiang.antigravity.BuildInfo

/**
 * 统一管理 Antigravity Studio 客户端版本元数据。
 */
object AppVersion {
    const val CURRENT = BuildInfo.VERSION_NAME
    const val VERSION_CODE = BuildInfo.VERSION_CODE
    const val GITHUB_OWNER = "yuzhiqiang1993"
    const val GITHUB_REPO = "antigravity-studio"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val LATEST_METADATA_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/latest.json"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
}
