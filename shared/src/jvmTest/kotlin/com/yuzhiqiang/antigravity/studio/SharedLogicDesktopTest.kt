package com.yuzhiqiang.antigravity.studio

import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import com.yuzhiqiang.antigravity.ui.components.MarkdownBlock
import com.yuzhiqiang.antigravity.ui.components.parseMarkdownBlocks
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

    @Test
    fun testMarkdownBlockParsing() {
        val rawMarkdown = """
            ### 🚀 优化与修复

            - 修复 Antigravity IDE 运行状态误判问题
            - 统一宿主文案为「已安装」

            ---

            > ⚠️ **macOS 用户注意**：执行以下命令
            ```bash
            sudo xattr -rd com.apple.quarantine "/Applications/Antigravity Studio.app"
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(rawMarkdown)
        assertNotNull(blocks)
        kotlin.test.assertTrue(blocks.isNotEmpty())

        val hasHeader = blocks.any { it is MarkdownBlock.Header }
        kotlin.test.assertTrue(hasHeader)

        val listItems = blocks.filterIsInstance<MarkdownBlock.ListItem>()
        kotlin.test.assertEquals(2, listItems.size)

        val hasDivider = blocks.any { it is MarkdownBlock.Divider }
        kotlin.test.assertTrue(hasDivider)

        val hasQuote = blocks.any { it is MarkdownBlock.Quote }
        kotlin.test.assertTrue(hasQuote)

        val hasCodeBlock = blocks.any { it is MarkdownBlock.CodeBlock }
        kotlin.test.assertTrue(hasCodeBlock)
    }
}