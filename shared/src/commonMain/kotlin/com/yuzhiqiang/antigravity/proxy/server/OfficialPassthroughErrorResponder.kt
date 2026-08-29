package com.yuzhiqiang.antigravity.proxy.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object OfficialPassthroughErrorResponder {
    suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String,
        category: String
    ) {
        call.respondText(
            buildJsonObject {
                put("error", buildJsonObject {
                    put("code", status.value)
                    put("category", category)
                    put("message", message)
                })
            }.toString(),
            ContentType.Application.Json,
            status
        )
    }
}
