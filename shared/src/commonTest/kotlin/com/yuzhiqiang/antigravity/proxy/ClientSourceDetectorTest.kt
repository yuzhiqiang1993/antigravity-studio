package com.yuzhiqiang.antigravity.proxy

import com.yuzhiqiang.antigravity.proxy.activity.ClientSourceDetector
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientSourceDetectorTest {

    @Test
    fun detectsCliFromActualAntigravityCliUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/cli/1.1.21 (aidev_client; os_type=darwin; arch=arm64)"
        )

        assertEquals(ClientSourceDetector.CLIENT_CLI, source)
    }

    @Test
    fun detectsAppFromHubSubclientUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/hub/2.11.0 (aidev_client; os_type=darwin; arch=arm64)"
        )

        assertEquals(ClientSourceDetector.CLIENT_APP, source)
    }

    @Test
    fun detectsIdeFromIdeSubclientUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/ide/1.107.0 (aidev_client; os_type=darwin; arch=arm64)"
        )

        assertEquals(ClientSourceDetector.CLIENT_IDE, source)
    }

    @Test
    fun explicitClientTakesPriorityOverUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = "Antigravity App",
            userAgent = "antigravity/ide/1.107.0"
        )

        assertEquals(ClientSourceDetector.CLIENT_APP, source)
    }

    @Test
    fun detectsIdeFromOfficialNodeJsClientUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/2.5.5 darwin/arm64 google-api-nodejs-client/10.3.0"
        )

        assertEquals(ClientSourceDetector.CLIENT_IDE, source)
    }

    @Test
    fun genericUnknownClientIsPreserved() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/2.11.0 (unsupported_tool)"
        )

        assertEquals("antigravity/2.11.0", source)
    }




    @Test
    fun genericHttpClientIsNotMisclassifiedAsAntigravityCli() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "curl/8.7.1"
        )

        assertEquals("curl/8.7.1", source)
    }

    @Test
    fun missingEvidenceReturnsUnknownClient() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = null
        )

        assertEquals(ClientSourceDetector.CLIENT_UNKNOWN, source)
    }

    @Test
    fun customExplicitClientNameIsPreserved() {
        val source = ClientSourceDetector.detect(
            explicitClient = "My Integration",
            userAgent = "antigravity/ide/1.107.0"
        )

        assertEquals("My Integration", source)
    }

    @Test
    fun detectsPluginFromExplicitClientHeader() {
        val fromStandardKebab = ClientSourceDetector.detect(
            explicitClient = "cockpit-plugin",
            userAgent = "antigravity/ide/2.5.5 (aidev_client; os_type=darwin; arch=arm64)"
        )
        val fromCockpitPlugin = ClientSourceDetector.detect(
            explicitClient = "Cockpit Plugin",
            userAgent = "antigravity/ide/2.5.5 (aidev_client; os_type=darwin; arch=arm64)"
        )
        val fromCockpit = ClientSourceDetector.detect(
            explicitClient = "Cockpit",
            userAgent = "antigravity/ide/2.5.5 (aidev_client; os_type=darwin; arch=arm64)"
        )
        val fromPlugin = ClientSourceDetector.detect(
            explicitClient = "plugin",
            userAgent = null
        )

        assertEquals(ClientSourceDetector.CLIENT_PLUGIN, fromStandardKebab)
        assertEquals(ClientSourceDetector.CLIENT_PLUGIN, fromCockpitPlugin)
        assertEquals(ClientSourceDetector.CLIENT_PLUGIN, fromCockpit)
        assertEquals(ClientSourceDetector.CLIENT_PLUGIN, fromPlugin)
    }



    @Test
    fun detectsPluginFromCockpitUserAgent() {
        val source = ClientSourceDetector.detect(
            explicitClient = null,
            userAgent = "antigravity/cockpit/1.3.11 (aidev_client; os_type=darwin; arch=arm64)"
        )

        assertEquals(ClientSourceDetector.CLIENT_PLUGIN, source)
    }
}

