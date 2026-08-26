package com.yuzhiqiang.antigravity.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

object PlatformNetworkConfig {

    init {
        try {
            System.setProperty("java.net.useSystemProxies", "true")
        } catch (_: Exception) {
        }
    }

    fun applySystemProperties() {
        try {
            System.setProperty("java.net.useSystemProxies", "true")
        } catch (_: Exception) {
        }
    }

    /**
     * 智能代理选择器：
     * 1. 优先使用系统/JVM 代理 (ProxySelector.getDefault())
     * 2. 检查环境变量 http_proxy / https_proxy / all_proxy
     * 3. 智能路由与回退，确保访问 Google 端点顺畅
     */
    fun createSmartProxySelector(): ProxySelector {
        val defaultSelector = ProxySelector.getDefault() ?: ProxySelector.of(null)
        val envProxy = resolveEnvProxy()

        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val host = uri?.host?.lowercase() ?: ""
                val isLocal = host == "127.0.0.1" || host == "localhost" || host.startsWith("192.168.") || host.startsWith("10.")

                if (isLocal) {
                    return listOf(Proxy.NO_PROXY)
                }

                val list = mutableListOf<Proxy>()
                try {
                    val systemProxies = defaultSelector.select(uri)
                    if (systemProxies != null) {
                        list.addAll(systemProxies.filter { it.type() != Proxy.Type.DIRECT })
                    }
                } catch (_: Exception) {
                }

                if (envProxy != null && !list.contains(envProxy)) {
                    list.add(envProxy)
                }

                // 探测本地常用代理 (如 7890, 7897, 1087) 作为候选
                val fallbackList = listOf(7890, 7897, 1087, 10808).map { port ->
                    Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
                }
                fallbackList.forEach { p ->
                    if (!list.contains(p)) {
                        list.add(p)
                    }
                }

                list.add(Proxy.NO_PROXY)
                return list.distinct()
            }

            override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                try {
                    defaultSelector.connectFailed(uri, sa, ioe)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun resolveEnvProxy(): Proxy? {
        val proxyStr = System.getenv("https_proxy")
            ?: System.getenv("http_proxy")
            ?: System.getenv("all_proxy")
            ?: System.getenv("HTTPS_PROXY")
            ?: System.getenv("HTTP_PROXY")
            ?: System.getenv("ALL_PROXY")
            ?: return null

        return try {
            val clean = proxyStr.removePrefix("http://").removePrefix("https://").removePrefix("socks5://").removePrefix("socks5h://")
            val parts = clean.split(":")
            if (parts.size == 2) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return null
                Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
