package com.yuzhiqiang.antigravity.network

import com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal data class MacSystemProxySnapshot(
    val http: NetworkProxyEndpoint? = null,
    val https: NetworkProxyEndpoint? = null,
    val socks: NetworkProxyEndpoint? = null,
    val autoConfigEnabled: Boolean = false,
    val exceptions: List<String> = emptyList()
) {
    fun select(uri: URI): List<Proxy> {
        val host = uri.host?.trim('[', ']')?.lowercase().orEmpty()
        if (host.isEmpty() || shouldBypass(host)) return emptyList()

        val endpoints = buildList {
            when (uri.scheme?.lowercase()) {
                "http" -> http?.let(::add)
                "https" -> https?.let(::add)
            }
            socks?.let(::add)
        }
        return endpoints.distinct().map { endpoint ->
            val type = if (endpoint.protocol == OutboundProxyProtocol.SOCKS5) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            Proxy(type, InetSocketAddress.createUnresolved(endpoint.host, endpoint.port))
        }
    }

    private fun shouldBypass(host: String): Boolean {
        return exceptions.any { rawRule ->
            val rule = rawRule.trim().lowercase()
            when {
                rule.isEmpty() -> false
                rule == "<local>" -> '.' !in host
                rule.startsWith("*.") -> host == rule.removePrefix("*.") || host.endsWith(rule.removePrefix("*"))
                '/' in rule -> matchesIpv4Cidr(host, rule)
                else -> host == rule
            }
        }
    }

    private fun matchesIpv4Cidr(host: String, cidr: String): Boolean {
        val address = host.toIpv4Long() ?: return false
        val network = cidr.substringBefore('/').toIpv4Long() ?: return false
        val prefix = cidr.substringAfter('/', "").toIntOrNull()?.takeIf { it in 0..32 } ?: return false
        val mask = if (prefix == 0) 0L else (0xFFFF_FFFFL shl (32 - prefix)) and 0xFFFF_FFFFL
        return (address and mask) == (network and mask)
    }

    private fun String.toIpv4Long(): Long? {
        val parts = split('.')
        if (parts.size != 4) return null
        return parts.fold(0L) { acc, part ->
            val value = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            (acc shl 8) or value.toLong()
        }
    }
}

internal object MacSystemProxyDetector {
    private const val CACHE_TTL_MS = 2_000L

    private data class CachedSnapshot(
        val timestamp: Long,
        val snapshot: MacSystemProxySnapshot
    )

    private val cache = AtomicReference(CachedSnapshot(0L, MacSystemProxySnapshot()))

    fun select(uri: URI): List<Proxy> {
        if (!System.getProperty("os.name", "").contains("mac", ignoreCase = true)) return emptyList()
        return currentSnapshot().select(uri)
    }

    internal fun parse(output: String): MacSystemProxySnapshot {
        val values = mutableMapOf<String, String>()
        val exceptions = mutableListOf<String>()
        var readingExceptions = false

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("ExceptionsList") -> readingExceptions = true
                readingExceptions && line == "}" -> readingExceptions = false
                readingExceptions && line.matches(Regex("\\d+\\s*:.*")) -> {
                    line.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let(exceptions::add)
                }

                line.matches(Regex("[A-Za-z]+\\s*:.*")) -> {
                    val key = line.substringBefore(':').trim()
                    values[key] = line.substringAfter(':').trim()
                }
            }
        }

        fun endpoint(
            enableKey: String,
            hostKey: String,
            portKey: String,
            protocol: OutboundProxyProtocol
        ): NetworkProxyEndpoint? {
            if (values[enableKey] != "1") return null
            val host = values[hostKey]?.takeIf { it.isNotBlank() } ?: return null
            val port = values[portKey]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return NetworkProxyEndpoint(protocol, host, port)
        }

        return MacSystemProxySnapshot(
            http = endpoint("HTTPEnable", "HTTPProxy", "HTTPPort", OutboundProxyProtocol.HTTP),
            https = endpoint("HTTPSEnable", "HTTPSProxy", "HTTPSPort", OutboundProxyProtocol.HTTP),
            socks = endpoint("SOCKSEnable", "SOCKSProxy", "SOCKSPort", OutboundProxyProtocol.SOCKS5),
            autoConfigEnabled = values["ProxyAutoConfigEnable"] == "1",
            exceptions = exceptions.distinct()
        )
    }

    private fun currentSnapshot(): MacSystemProxySnapshot {
        val now = System.currentTimeMillis()
        val cached = cache.get()
        if (now - cached.timestamp < CACHE_TTL_MS) return cached.snapshot

        val detected = runCatching {
            val process = ProcessBuilder("/usr/sbin/scutil", "--proxy")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(1_500L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("scutil --proxy timed out")
            }
            parse(process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }.getOrDefault(MacSystemProxySnapshot())

        cache.set(CachedSnapshot(now, detected))
        return detected
    }
}
