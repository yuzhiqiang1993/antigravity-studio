package com.yuzhiqiang.antigravity.proxy.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.net.URI

internal object OfficialPassthroughRouting {
    fun officialUrl(
        path: String,
        query: String,
        actualPortProvider: () -> Int
    ): Result<String> {
        val endpoint = System.getenv("ANTIGRAVITY_CLOUD_CODE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "https://daily-cloudcode-pa.googleapis.com"
        val parsedEndpoint = runCatching { URI(endpoint) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Official Cloud Code endpoint is invalid"))
        if (parsedEndpoint.scheme !in setOf("http", "https") || parsedEndpoint.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Official Cloud Code endpoint must be an absolute HTTP(S) URL"))
        }
        if (!parsedEndpoint.query.isNullOrBlank() ||
            !parsedEndpoint.fragment.isNullOrBlank() ||
            !parsedEndpoint.userInfo.isNullOrBlank()
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Official Cloud Code endpoint cannot contain embedded credentials, query or fragment"
                )
            )
        }
        val isLoopback = parsedEndpoint.host.equals("127.0.0.1", ignoreCase = true) ||
                parsedEndpoint.host.equals("localhost", ignoreCase = true) ||
                parsedEndpoint.host == "::1"
        if (parsedEndpoint.scheme.equals("http", ignoreCase = true) && !isLoopback) {
            return Result.failure(IllegalArgumentException("非回环官方 Cloud Code 地址必须使用 HTTPS"))
        }
        if (isLocalProxyEndpoint(endpoint, actualPortProvider)) {
            return Result.failure(
                IllegalStateException("Official Cloud Code endpoint points to the local proxy; refusing recursive passthrough")
            )
        }
        val suffix = if (query.isBlank()) path else "$path?$query"
        return Result.success(endpoint.trimEnd('/') + suffix)
    }

    fun rewriteOfficialUrls(
        body: String,
        call: ApplicationCall,
        actualPortProvider: () -> Int
    ): String {
        val scheme = call.request.headers["X-Forwarded-Proto"] ?: "http"
        val hostHeader = call.request.headers[HttpHeaders.Host]
        val proxyTarget = if (!hostHeader.isNullOrBlank()) {
            "$scheme://$hostHeader"
        } else {
            "http://127.0.0.1:" + actualPortProvider()
        }
        return body
            .replace("https://daily-cloudcode-pa.googleapis.com", proxyTarget)
            .replace("https://cloudcode-pa.googleapis.com", proxyTarget)
    }

    fun isTextualContentType(contentType: ContentType): Boolean {
        return contentType.contentType.equals("text", ignoreCase = true) ||
                contentType.contentType.equals("application", ignoreCase = true) &&
                contentType.contentSubtype.lowercase() in setOf(
            "json", "javascript", "xml", "x-www-form-urlencoded", "grpc+json"
        )
    }

    private fun isLocalProxyEndpoint(endpoint: String, actualPortProvider: () -> Int): Boolean {
        return try {
            val uri = URI(endpoint)
            val host = uri.host?.lowercase()
            val localHost = host == "127.0.0.1" || host == "localhost" || host == "::1"
            localHost && (uri.port == -1 || uri.port == actualPortProvider())
        } catch (_: Exception) {
            false
        }
    }
}
