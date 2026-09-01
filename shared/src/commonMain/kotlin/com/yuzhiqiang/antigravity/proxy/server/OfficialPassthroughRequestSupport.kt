package com.yuzhiqiang.antigravity.proxy.server

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod

internal object OfficialPassthroughRequestSupport {
    fun debugBody(rawBody: ByteArray, isDebug: Boolean): String? {
        return if (isDebug && rawBody.isNotEmpty()) rawBody.decodeToString() else null
    }

    fun applyRequest(
        builder: HttpRequestBuilder,
        call: ApplicationCall,
        rawBody: ByteArray
    ) {
        builder.method = call.request.httpMethod
        builder.header(HttpHeaders.AcceptEncoding, "identity")
        if (rawBody.isNotEmpty() && builder.method != HttpMethod.Head) {
            builder.contentType(
                call.request.headers[HttpHeaders.ContentType]
                    ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                    ?: ContentType.Application.Json
            )
            builder.setBody(rawBody)
        } else if (builder.method == HttpMethod.Post ||
            builder.method == HttpMethod.Put ||
            builder.method == HttpMethod.Patch
        ) {
            builder.contentType(ContentType.Application.Json)
            builder.setBody(ByteArray(0))
        }
        copyRequestHeaders(call, builder)
    }

    fun applyCatalogRequest(
        builder: HttpRequestBuilder,
        call: ApplicationCall,
        rawBody: String
    ) {
        builder.contentType(ContentType.Application.Json)
        builder.header(HttpHeaders.AcceptEncoding, "identity")
        builder.setBody(rawBody)
        copyRequestHeaders(call, builder)
    }

    private fun copyRequestHeaders(call: ApplicationCall, builder: HttpRequestBuilder) {
        call.request.headers.forEach { name, values ->
            if (!OfficialPassthroughHttpSupport.isInternalHeader(name)) {
                values.forEach { value -> builder.header(name, value) }
            }
        }
    }
}

private const val REDACTED_HEADER_VALUE = "<redacted>"

private val SENSITIVE_DEBUG_HEADERS = setOf(
    "authorization",
    "proxy-authorization",
    "cookie",
    "set-cookie",
    "x-api-key",
    "api-key",
    "x-goog-api-key",
    "anthropic-api-key"
)

internal fun sanitizeDebugHeaders(headers: Headers): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    headers.forEach { key, values ->
        map[key] = if (key.lowercase() in SENSITIVE_DEBUG_HEADERS) {
            REDACTED_HEADER_VALUE
        } else {
            values.joinToString(", ")
        }
    }
    return map
}

internal fun extractRequestHeaders(call: ApplicationCall): Map<String, String> =
    sanitizeDebugHeaders(call.request.headers)

internal fun extractResponseHeaders(headers: Headers): Map<String, String> =
    sanitizeDebugHeaders(headers)
