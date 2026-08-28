package com.yuzhiqiang.antigravity.proxy.activity

import io.ktor.server.application.*
import io.ktor.server.request.*

object ClientSourceDetector {
    const val CLIENT_IDE = "Antigravity IDE"
    const val CLIENT_APP = "Antigravity App"
    const val CLIENT_CLI = "Antigravity CLI"
    const val CLIENT_UNKNOWN = "Unknown Client"

    fun detect(call: ApplicationCall): String {
        val explicitClient = call.request.header("X-Antigravity-Client")
            ?: call.request.header("X-Client-Type")
            ?: call.request.header("X-Client-Name")
        return detect(
            explicitClient = explicitClient,
            userAgent = call.request.header("User-Agent"),
            hasCodeiumCsrfToken = call.request.header("x-codeium-csrf-token") != null
        )
    }

    internal fun detect(
        explicitClient: String?,
        userAgent: String?,
        hasCodeiumCsrfToken: Boolean = false
    ): String {
        explicitClient?.trim()?.takeIf { it.isNotEmpty() }?.let { client ->
            return normalizeExplicitClient(client)
        }

        val normalizedUserAgent = userAgent?.trim().orEmpty()
        val lowerUserAgent = normalizedUserAgent.lowercase()
        return when {
            lowerUserAgent.contains("antigravity/cli/") ||
                    lowerUserAgent.contains("antigravity-cli") -> CLIENT_CLI

            lowerUserAgent.contains("antigravity/hub/") ||
                    lowerUserAgent.contains("antigravity-app") ||
                    lowerUserAgent.contains("antigravityapp") -> CLIENT_APP

            lowerUserAgent.contains("antigravity/ide/") ||
                    lowerUserAgent.contains("antigravity-ide") ||
                    lowerUserAgent.contains("vscode") ||
                    lowerUserAgent.contains("codeium") ||
                    hasCodeiumCsrfToken -> CLIENT_IDE

            normalizedUserAgent.isNotEmpty() -> normalizedUserAgent.substringBefore(' ').take(MAX_FALLBACK_LENGTH)
            else -> CLIENT_UNKNOWN
        }
    }

    private fun normalizeExplicitClient(client: String): String {
        val lower = client.lowercase()
        return when {
            lower.containsClientToken("ide") ||
                    lower.contains("vscode") ||
                    lower.contains("visual_studio_code") -> CLIENT_IDE

            lower.containsClientToken("app") ||
                    lower.containsClientToken("hub") ||
                    lower.containsClientToken("desktop") ||
                    lower.containsClientToken("electron") -> CLIENT_APP

            lower.containsClientToken("cli") ||
                    lower.containsClientToken("agy") ||
                    lower.containsClientToken("terminal") -> CLIENT_CLI

            else -> client
        }
    }

    private fun String.containsClientToken(token: String): Boolean {
        return split(NON_ALPHANUMERIC_REGEX).any { it == token }
    }

    private const val MAX_FALLBACK_LENGTH = 64
    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
}
