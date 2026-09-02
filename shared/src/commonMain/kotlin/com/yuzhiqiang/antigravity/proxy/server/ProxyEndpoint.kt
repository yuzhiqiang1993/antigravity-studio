package com.yuzhiqiang.antigravity.proxy.server

/** Studio 管理的本地代理入口。 */
object ProxyEndpoint {
    fun local(port: Int): String = "http://127.0.0.1:$port"

    fun secure(port: Int, token: String = ""): String = "http://127.0.0.1:$port"

    fun isSecure(endpoint: String?, port: Int): Boolean {
        return endpoint != null && (endpoint.contains("127.0.0.1:$port") || endpoint.contains("localhost:$port"))
    }
}

