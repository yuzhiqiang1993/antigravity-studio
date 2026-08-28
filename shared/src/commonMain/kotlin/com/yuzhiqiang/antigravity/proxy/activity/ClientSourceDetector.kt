package com.yuzhiqiang.antigravity.proxy.activity

import io.ktor.server.application.*
import io.ktor.server.request.*

object ClientSourceDetector {
    const val CLIENT_IDE = "Antigravity IDE"
    const val CLIENT_APP = "Antigravity App"
    const val CLIENT_CLI = "Antigravity CLI"

    fun detect(call: ApplicationCall): String {
        // 1. 显式请求头
        val explicitHeader = call.request.header("X-Antigravity-Client")
            ?: call.request.header("X-Client-Type")
            ?: call.request.header("X-Client-Name")
        if (!explicitHeader.isNullOrBlank()) {
            val lower = explicitHeader.lowercase()
            return when {
                "ide" in lower || "vscode" in lower || "visual_studio_code" in lower -> CLIENT_IDE
                "app" in lower || "desktop" in lower || "electron" in lower -> CLIENT_APP
                "cli" in lower || "agy" in lower || "terminal" in lower -> CLIENT_CLI
                else -> explicitHeader.trim()
            }
        }

        // 2. User-Agent 智能匹配
        val ua = call.request.header("User-Agent")?.lowercase().orEmpty()
        val hasCodeiumCsrf = call.request.header("x-codeium-csrf-token") != null
        val hasConnectProto = call.request.header("connect-protocol-version") != null

        if (ua.contains("agy") || ua.contains("antigravity-cli") || ua.contains("curl") || ua.contains("python") || ua.contains("go-http-client")) {
            return CLIENT_CLI
        }

        if (ua.contains("antigravityapp") || ua.contains("antigravity-app")) {
            return CLIENT_APP
        }

        if (ua.contains("vscode") || ua.contains("codeium") || ua.contains("antigravity") ||
            hasCodeiumCsrf || hasConnectProto || ua.contains("node-fetch") || ua.contains("axios")
        ) {
            return CLIENT_IDE
        }

        return if (ua.isNotBlank()) ua.substringBefore(' ').take(24) else CLIENT_IDE
    }
}
