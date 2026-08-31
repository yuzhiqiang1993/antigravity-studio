package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

class OfficialPassthroughHandler(
    private val configStore: ConfigStore,
    private val actualPortProvider: () -> Int
) {
    private val forwarder = OfficialPassthroughForwarder(configStore, actualPortProvider)
    private val catalogHandler = OfficialPassthroughCatalogHandler(configStore, actualPortProvider)

    suspend fun forwardOfficial(
        call: ApplicationCall,
        path: String,
        rawBody: ByteArray,
        modelId: String?,
        startTime: Long,
        queueWaitMs: Long? = null
    ) {
        forwarder.forwardOfficial(call, path, rawBody, modelId, startTime, queueWaitMs)
    }

    suspend fun forwardOfficialCatalog(
        call: ApplicationCall,
        path: String,
        rawBody: String,
        startTime: Long,
        queueWaitMs: Long? = null
    ) {
        catalogHandler.forwardOfficialCatalog(call, path, rawBody, startTime, queueWaitMs)
    }

    suspend fun respondCatalogFallback(
        call: ApplicationCall,
        path: String,
        startTime: Long,
        reason: String,
        rawBody: String? = null
    ) {
        catalogHandler.respondCatalogFallback(call, path, startTime, reason, rawBody)
    }

    fun officialUrl(path: String, query: String): Result<String> {
        return OfficialPassthroughRouting.officialUrl(path, query, actualPortProvider)
    }

    fun rewriteOfficialUrls(body: String, call: ApplicationCall): String {
        return OfficialPassthroughRouting.rewriteOfficialUrls(body, call, actualPortProvider)
    }

    fun isTextualContentType(contentType: ContentType): Boolean {
        return OfficialPassthroughRouting.isTextualContentType(contentType)
    }

    fun isInternalHeader(name: String): Boolean {
        return OfficialPassthroughHttpSupport.isInternalHeader(name)
    }

    fun isHopByHopHeader(name: String): Boolean {
        return OfficialPassthroughHttpSupport.isHopByHopHeader(name)
    }

    fun copyForwardResponseHeaders(call: ApplicationCall, response: HttpResponse) {
        OfficialPassthroughHttpSupport.copyForwardResponseHeaders(call, response)
    }
}
