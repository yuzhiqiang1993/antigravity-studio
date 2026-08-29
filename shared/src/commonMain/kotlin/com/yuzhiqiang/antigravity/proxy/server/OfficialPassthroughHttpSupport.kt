package com.yuzhiqiang.antigravity.proxy.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.client.statement.HttpResponse

internal object OfficialPassthroughHttpSupport {
    fun isInternalHeader(name: String): Boolean {
        if (isHopByHopHeader(name)) return true
        val lower = name.lowercase()
        return lower == HttpHeaders.ContentType.lowercase() ||
                lower == HttpHeaders.AcceptEncoding.lowercase() ||
                lower == "x-antigravity-studio-token" ||
                lower == "x-antigravity-studio-internal-probe" ||
                lower == "x-antigravity-client" ||
                lower == "x-client-type" ||
                lower == "x-client-name" ||
                lower == "x-client-version" ||
                lower == "x-agy-byok-token" ||
                lower == "x-agy-byok-internal-probe"
    }

    fun isHopByHopHeader(name: String): Boolean {
        if (name.startsWith(":")) return true
        return name.equals(HttpHeaders.Host, ignoreCase = true) ||
                name.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
                name.equals(HttpHeaders.Connection, ignoreCase = true) ||
                name.equals("Keep-Alive", ignoreCase = true) ||
                name.equals("Proxy-Authenticate", ignoreCase = true) ||
                name.equals("Proxy-Authorization", ignoreCase = true) ||
                name.equals("TE", ignoreCase = true) ||
                name.equals("Trailer", ignoreCase = true) ||
                name.equals("Transfer-Encoding", ignoreCase = true) ||
                name.equals("Upgrade", ignoreCase = true)
    }

    fun copyForwardResponseHeaders(call: ApplicationCall, response: HttpResponse) {
        response.headers.forEach { name, values ->
            if (isHopByHopHeader(name) ||
                name.equals(HttpHeaders.ContentType, ignoreCase = true) ||
                name.equals(HttpHeaders.ContentLength, ignoreCase = true) ||
                name.startsWith("Access-Control-", ignoreCase = true)
            ) {
                return@forEach
            }
            values.forEach { value -> call.response.headers.append(name, value) }
        }
    }
}
