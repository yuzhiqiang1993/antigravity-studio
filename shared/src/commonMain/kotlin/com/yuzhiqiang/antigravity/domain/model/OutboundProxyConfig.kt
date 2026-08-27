package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OutboundProxyMode {
    @SerialName("auto")
    AUTO,

    @SerialName("direct")
    DIRECT,

    @SerialName("system")
    SYSTEM,

    @SerialName("manual")
    MANUAL
}

@Serializable
enum class OutboundProxyProtocol {
    @SerialName("http")
    HTTP,

    @SerialName("socks5")
    SOCKS5
}

@Serializable
data class OutboundProxyConfig(
    @SerialName("mode")
    val mode: OutboundProxyMode = OutboundProxyMode.AUTO,
    @SerialName("protocol")
    val protocol: OutboundProxyProtocol = OutboundProxyProtocol.HTTP,
    @SerialName("host")
    val host: String = "127.0.0.1",
    @SerialName("port")
    val port: Int = 7890,
    @SerialName("fallback_to_direct")
    val fallbackToDirect: Boolean = true
)
