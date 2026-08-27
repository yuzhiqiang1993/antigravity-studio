package com.yuzhiqiang.antigravity.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacSystemProxyDetectorTest {

    private val scutilOutput = """
        <dictionary> {
          ExceptionsList : <array> {
            0 : 127.0.0.1
            1 : 192.168.0.0/16
            2 : *.local
            3 : <local>
          }
          HTTPEnable : 1
          HTTPPort : 7890
          HTTPProxy : 127.0.0.1
          HTTPSEnable : 1
          HTTPSPort : 7890
          HTTPSProxy : 127.0.0.1
          ProxyAutoConfigEnable : 0
          SOCKSEnable : 1
          SOCKSPort : 7890
          SOCKSProxy : 127.0.0.1
        }
    """.trimIndent()

    @Test
    fun parsesEnabledMacSystemProxies() {
        val snapshot = MacSystemProxyDetector.parse(scutilOutput)

        assertEquals("127.0.0.1:7890", snapshot.http?.address)
        assertEquals("127.0.0.1:7890", snapshot.https?.address)
        assertEquals("127.0.0.1:7890", snapshot.socks?.address)
        assertEquals(false, snapshot.autoConfigEnabled)
    }

    @Test
    fun selectsHttpsAndSocksRoutesForRemoteHttpsTarget() {
        val snapshot = MacSystemProxyDetector.parse(scutilOutput)
        val routes = snapshot.select(URI("https://daily-cloudcode-pa.googleapis.com"))

        assertEquals(listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS), routes.map(Proxy::type))
        assertTrue(routes.all { (it.address() as InetSocketAddress).port == 7890 })
    }

    @Test
    fun respectsMacSystemProxyExceptions() {
        val snapshot = MacSystemProxyDetector.parse(scutilOutput)

        assertEquals(emptyList(), snapshot.select(URI("https://127.0.0.1")))
        assertEquals(emptyList(), snapshot.select(URI("https://printer.local")))
        assertEquals(emptyList(), snapshot.select(URI("https://intranet")))
    }
}
