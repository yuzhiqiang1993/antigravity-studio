package com.yuzhiqiang.antigravity.studio

import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SharedLogicDesktopTest {

    @Test
    fun testHostInspectDoesNotCrash() {
        val ideStatus = IdeHostManager.inspect(8080)
        assertNotNull(ideStatus)

        val appStatus = AppHostManager.inspect(8080)
        assertNotNull(appStatus)

        val cliStatus = CliHostManager.inspect(8080)
        assertNotNull(cliStatus)
    }
}