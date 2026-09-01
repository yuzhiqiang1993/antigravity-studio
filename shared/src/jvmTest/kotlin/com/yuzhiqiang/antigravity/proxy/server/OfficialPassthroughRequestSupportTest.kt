package com.yuzhiqiang.antigravity.proxy.server

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialPassthroughRequestSupportTest {

    @Test
    fun sanitizeDebugHeadersRedactsCredentialsAndCookies() {
        val headers = Headers.build {
            append(HttpHeaders.Authorization, "Bearer secret")
            append(HttpHeaders.ProxyAuthorization, "Basic secret")
            append(HttpHeaders.Cookie, "session=secret")
            append(HttpHeaders.SetCookie, "session=secret")
            append("X-Api-Key", "api-secret")
            append("Api-Key", "api-secret")
            append("X-Goog-Api-Key", "google-secret")
            append("Anthropic-Api-Key", "anthropic-secret")
            append("X-Trace-Id", "trace-1")
            append("X-Trace-Id", "trace-2")
        }

        val sanitized = sanitizeDebugHeaders(headers)

        fun value(name: String): String? = sanitized.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value

        assertEquals("<redacted>", value(HttpHeaders.Authorization))
        assertEquals("<redacted>", value(HttpHeaders.ProxyAuthorization))
        assertEquals("<redacted>", value(HttpHeaders.Cookie))
        assertEquals("<redacted>", value(HttpHeaders.SetCookie))
        assertEquals("<redacted>", value("X-Api-Key"))
        assertEquals("<redacted>", value("Api-Key"))
        assertEquals("<redacted>", value("X-Goog-Api-Key"))
        assertEquals("<redacted>", value("Anthropic-Api-Key"))
        assertEquals("trace-1, trace-2", value("X-Trace-Id"))
    }
}
