package com.yuzhiqiang.antigravity.proxy.server

import java.net.URI

/** Studio 管理的本地代理安全入口。 */
object ProxyEndpoint {
    fun secure(port: Int, token: String = ProxyAccessTokenStore().loadOrCreate().getOrThrow()): String {
        require(port in 1..65535) { "代理端口无效" }
        require(ProxyAccessTokenStore.isValidToken(token)) { "代理访问令牌无效" }
        return "http://127.0.0.1:$port/v1internal/$token/dummy_path_padding"
    }

    fun isSecure(endpoint: String?, port: Int): Boolean {
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        val host = uri.host?.trim('[', ']')?.lowercase()
        val prefix = "/v1internal/"
        val suffix = "/dummy_path_padding"
        val path = uri.path.orEmpty()
        if (!uri.scheme.equals("http", ignoreCase = true) || host !in LOOPBACK_HOSTS || uri.port != port ||
            !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank() || !uri.userInfo.isNullOrBlank() ||
            !path.startsWith(prefix) || !path.endsWith(suffix)
        ) return false
        return ProxyAccessTokenStore.isValidToken(path.removePrefix(prefix).removeSuffix(suffix))
    }

    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")
}
