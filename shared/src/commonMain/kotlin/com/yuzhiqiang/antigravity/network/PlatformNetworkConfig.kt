package com.yuzhiqiang.antigravity.network

import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

data class NetworkProxyEndpoint(
    val protocol: OutboundProxyProtocol,
    val host: String,
    val port: Int
) {
    val address: String
        get() = if (':' in host && !host.startsWith("[")) "[$host]:$port" else "$host:$port"
}

data class OutboundProxyInspection(
    val systemProxies: List<NetworkProxyEndpoint>,
    val environmentProxy: NetworkProxyEndpoint?,
    val effectiveProxies: List<NetworkProxyEndpoint>,
    val directEnabled: Boolean
)

object PlatformNetworkConfig {
    private val activeOutboundProxy = AtomicReference(OutboundProxyConfig())
    private val inspectionUri = URI("https://daily-cloudcode-pa.googleapis.com")

    init {
        applySystemProperties()
        MacSystemProxyDetector.prewarm()
    }

    fun applySystemProperties() {
        try {
            System.setProperty("java.net.useSystemProxies", "true")
        } catch (_: Exception) {
        }
    }

    fun applyOutboundProxy(config: OutboundProxyConfig) {
        activeOutboundProxy.set(config)
    }

    fun currentOutboundProxy(): OutboundProxyConfig = activeOutboundProxy.get()

    internal fun awaitSystemProxyPrewarm(timeoutMs: Long = 2_000L): Boolean {
        return MacSystemProxyDetector.awaitInitialSnapshot(timeoutMs)
    }

    /**
     * 为长期存活的 HTTP Client 创建动态代理选择器。每次请求都会读取最新的 Studio 配置和系统代理，
     * 因此保存网络设置后无需重建 Client，也不会继续持有已经关闭的旧系统代理路由。
     */
    fun createSmartProxySelector(
        defaultSelectorProvider: () -> ProxySelector? = { ProxySelector.getDefault() },
        environment: Map<String, String> = System.getenv(),
        outboundConfigProvider: () -> OutboundProxyConfig = { activeOutboundProxy.get() },
        nativeSystemProxyProvider: (URI) -> List<Proxy> = MacSystemProxyDetector::select
    ): ProxySelector {
        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val target = uri ?: inspectionUri
                return selectProxies(
                    config = outboundConfigProvider(),
                    uri = target,
                    defaultSelector = defaultSelectorProvider()?.takeUnless { it === this },
                    environment = environment,
                    nativeSystemProxyProvider = nativeSystemProxyProvider
                )
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                val defaultSelector = defaultSelectorProvider()
                if (defaultSelector != null && defaultSelector !== this) {
                    try {
                        defaultSelector.connectFailed(uri, sa, ioe)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun inspectOutboundProxy(
        config: OutboundProxyConfig = activeOutboundProxy.get(),
        uri: URI = inspectionUri,
        defaultSelector: ProxySelector? = ProxySelector.getDefault(),
        environment: Map<String, String> = System.getenv(),
        nativeSystemProxyProvider: (URI) -> List<Proxy> = MacSystemProxyDetector::select
    ): OutboundProxyInspection {
        val systemProxies = systemProxies(defaultSelector, uri, nativeSystemProxyProvider).mapNotNull(::toEndpoint)
        val environmentProxy = resolveEnvProxy(environment)?.let(::toEndpoint)
        val effectiveRoutes = selectProxies(
            config,
            uri,
            defaultSelector,
            environment,
            nativeSystemProxyProvider
        )
        return OutboundProxyInspection(
            systemProxies = systemProxies,
            environmentProxy = environmentProxy,
            effectiveProxies = effectiveRoutes.mapNotNull(::toEndpoint),
            directEnabled = effectiveRoutes.any { it.type() == Proxy.Type.DIRECT }
        )
    }

    internal fun selectProxies(
        config: OutboundProxyConfig,
        uri: URI,
        defaultSelector: ProxySelector? = ProxySelector.getDefault(),
        environment: Map<String, String> = System.getenv(),
        nativeSystemProxyProvider: (URI) -> List<Proxy> = MacSystemProxyDetector::select
    ): List<Proxy> {
        if (isLocalAddress(uri.host)) return listOf(Proxy.NO_PROXY)

        val proxies = mutableListOf<Proxy>()
        when (config.mode) {
            OutboundProxyMode.AUTO -> {
                proxies += systemProxies(defaultSelector, uri, nativeSystemProxyProvider)
                resolveEnvProxy(environment)?.let { proxies += it }
                proxies += Proxy.NO_PROXY
            }

            OutboundProxyMode.DIRECT -> proxies += Proxy.NO_PROXY

            OutboundProxyMode.SYSTEM -> {
                proxies += systemProxies(defaultSelector, uri, nativeSystemProxyProvider)
                if (config.fallbackToDirect) proxies += Proxy.NO_PROXY
            }

            OutboundProxyMode.MANUAL -> {
                manualProxy(config)?.let { proxies += it }
                if (config.fallbackToDirect) proxies += Proxy.NO_PROXY
            }
        }
        return proxies.distinct()
    }

    private fun manualProxy(config: OutboundProxyConfig): Proxy? {
        val host = config.host.trim().trim('[', ']')
        if (host.isEmpty() || config.port !in 1..65535) return null
        val type = when (config.protocol) {
            OutboundProxyProtocol.HTTP -> Proxy.Type.HTTP
            OutboundProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
        }
        return Proxy(type, InetSocketAddress.createUnresolved(host, config.port))
    }

    private fun systemProxies(
        selector: ProxySelector?,
        uri: URI,
        nativeSystemProxyProvider: (URI) -> List<Proxy>
    ): List<Proxy> {
        val jvmProxies = if (selector == null) {
            emptyList()
        } else {
            try {
                selector.select(uri).orEmpty().filter { it.type() != Proxy.Type.DIRECT }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val nativeProxies = runCatching { nativeSystemProxyProvider(uri) }.getOrDefault(emptyList())
        return (jvmProxies + nativeProxies)
            .filter { it.type() != Proxy.Type.DIRECT }
            .distinct()
    }

    private fun isLocalAddress(host: String?): Boolean {
        val normalized = host?.trim('[', ']')?.lowercase().orEmpty()
        val isPrivate172 = normalized.startsWith("172.") &&
                normalized.substringAfter("172.").substringBefore('.').toIntOrNull() in 16..31
        return normalized == "127.0.0.1" ||
                normalized == "localhost" ||
                normalized == "::1" ||
                normalized.startsWith("10.") ||
                normalized.startsWith("192.168.") ||
                isPrivate172
    }

    private fun resolveEnvProxy(environment: Map<String, String>): Proxy? {
        val raw = environment["https_proxy"]
            ?: environment["http_proxy"]
            ?: environment["all_proxy"]
            ?: environment["HTTPS_PROXY"]
            ?: environment["HTTP_PROXY"]
            ?: environment["ALL_PROXY"]
            ?: return null

        return parseProxy(raw)
    }

    private fun parseProxy(raw: String): Proxy? {
        return try {
            val value = raw.trim()
            val parsed = URI(if (value.contains("://")) value else "http://$value")
            val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
            val protocol = parsed.scheme?.lowercase().orEmpty()
            val type = if (protocol.startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val port = parsed.port.takeIf { it in 1..65535 }
                ?: if (type == Proxy.Type.SOCKS) 1080 else 8080
            Proxy(type, InetSocketAddress.createUnresolved(host, port))
        } catch (_: Exception) {
            null
        }
    }

    private fun toEndpoint(proxy: Proxy): NetworkProxyEndpoint? {
        val address = proxy.address() as? InetSocketAddress ?: return null
        val protocol = when (proxy.type()) {
            Proxy.Type.HTTP -> OutboundProxyProtocol.HTTP
            Proxy.Type.SOCKS -> OutboundProxyProtocol.SOCKS5
            Proxy.Type.DIRECT -> return null
        }
        return NetworkProxyEndpoint(protocol, address.hostString, address.port)
    }
}
