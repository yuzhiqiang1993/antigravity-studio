package com.yuzhiqiang.antigravity.network

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyConfig
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyMode
import com.yuzhiqiang.antigravity.domain.model.OutboundProxyProtocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlatformNetworkConfigTest {

    private val remoteUri = URI("https://daily-cloudcode-pa.googleapis.com/v1internal:listExperiments")
    private val noNativeSystemProxy: (URI) -> List<Proxy> = { emptyList() }

    @Test
    fun noSystemOrEnvironmentProxyUsesDirectConnection() {
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { null },
            environment = emptyMap(),
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(remoteUri))
    }

    @Test
    fun systemProxyCandidateIsFollowedByDirectFallback() {
        val systemProxy = httpProxy("127.0.0.1", 65_535)
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { FixedProxySelector(listOf(systemProxy)) },
            environment = emptyMap(),
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        assertEquals(listOf(systemProxy, Proxy.NO_PROXY), selector.select(remoteUri))
    }

    @Test
    fun commonLocalProxyPortsAreNotGuessed() {
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { null },
            environment = emptyMap(),
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        val selectedPorts = selector.select(remoteUri)
            .mapNotNull { it.address() as? InetSocketAddress }
            .filter { it.hostString == "127.0.0.1" }
            .map { it.port }

        assertFalse(selectedPorts.any { it in setOf(7_890, 7_897, 1_087, 10_808) })
    }

    @Test
    fun selectorReadsCurrentSystemProxyForEachRequest() {
        var currentSelector: ProxySelector? = null
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { currentSelector },
            environment = emptyMap(),
            nativeSystemProxyProvider = noNativeSystemProxy
        )
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(remoteUri))

        val systemProxy = httpProxy("127.0.0.1", 7_890)
        currentSelector = FixedProxySelector(listOf(systemProxy))

        assertEquals(listOf(systemProxy, Proxy.NO_PROXY), selector.select(remoteUri))
    }

    @Test
    fun legacyConfigWithoutOutboundProxyUsesSmartSelection() {
        val config = Json.decodeFromString<AppConfig>("{}")

        assertEquals(OutboundProxyMode.AUTO, config.outboundProxy.mode)
        assertEquals(true, config.outboundProxy.fallbackToDirect)
    }

    @Test
    fun directModeIgnoresSystemAndEnvironmentProxies() {
        val systemProxy = httpProxy("127.0.0.1", 7_890)
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { FixedProxySelector(listOf(systemProxy)) },
            environment = mapOf("HTTPS_PROXY" to "http://127.0.0.1:7897"),
            outboundConfigProvider = { OutboundProxyConfig(mode = OutboundProxyMode.DIRECT) },
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(remoteUri))
    }

    @Test
    fun strictSystemModeDoesNotAddDirectFallback() {
        val systemProxy = httpProxy("127.0.0.1", 7_890)
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { FixedProxySelector(listOf(systemProxy)) },
            environment = emptyMap(),
            outboundConfigProvider = {
                OutboundProxyConfig(
                    mode = OutboundProxyMode.SYSTEM,
                    fallbackToDirect = false
                )
            },
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        assertEquals(listOf(systemProxy), selector.select(remoteUri))
    }

    @Test
    fun manualSocksProxyIsFollowedByDirectFallback() {
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { null },
            environment = emptyMap(),
            outboundConfigProvider = {
                OutboundProxyConfig(
                    mode = OutboundProxyMode.MANUAL,
                    protocol = OutboundProxyProtocol.SOCKS5,
                    host = "127.0.0.1",
                    port = 10_808,
                    fallbackToDirect = true
                )
            },
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        val routes = selector.select(remoteUri)
        assertEquals(Proxy.Type.SOCKS, routes.first().type())
        assertEquals(10_808, (routes.first().address() as InetSocketAddress).port)
        assertEquals(Proxy.NO_PROXY, routes.last())
    }

    @Test
    fun localAddressAlwaysConnectsDirectly() {
        val selector = PlatformNetworkConfig.createSmartProxySelector(
            defaultSelectorProvider = { null },
            environment = emptyMap(),
            outboundConfigProvider = {
                OutboundProxyConfig(
                    mode = OutboundProxyMode.MANUAL,
                    host = "127.0.0.1",
                    port = 7_890,
                    fallbackToDirect = false
                )
            },
            nativeSystemProxyProvider = noNativeSystemProxy
        )

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("http://127.0.0.1:8317/v1/models")))
    }

    private fun httpProxy(host: String, port: Int): Proxy {
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }

    private class FixedProxySelector(
        private val proxies: List<Proxy>
    ) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = proxies

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }
}
