package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.ide.IdeHostManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostPathIsolationTest {

    @Test
    fun testIdeCandidatePathsDoNotContainAppOrCli() {
        val ideCandidates = IdeHostManager.getCandidateInstallations()
        for (candidate in ideCandidates) {
            val path = candidate.absolutePath.replace('\\', '/')
            assertFalse(
                path.endsWith("/Antigravity.app") || path.endsWith("/Antigravity App.app"),
                "IDE 候选路径中不得包含 Antigravity App 路径: $path"
            )
            assertFalse(
                path.endsWith("/agy") || path.endsWith("/agy.exe"),
                "IDE 候选路径中不得包含 CLI 二进制路径: $path"
            )
        }

        val settingsFiles = IdeHostManager.getCandidateSettingsFiles()
        for (file in settingsFiles) {
            val path = file.absolutePath.replace('\\', '/')
            assertFalse(
                path.contains("/Antigravity/User/settings.json"),
                "IDE 设置候选路径中不得包含独立 App 的数据目录: $path"
            )
            assertTrue(
                path.contains("Antigravity IDE") || path.contains("Antigravity-IDE"),
                "IDE 设置候选路径必须明确属于 IDE 目录: $path"
            )
        }
    }

    @Test
    fun testAppCandidatePathsDoNotContainIdeOrCli() {
        val appCandidates = AppHostManager.getCandidateInstallations()
        for (candidate in appCandidates) {
            val path = candidate.absolutePath.replace('\\', '/')
            assertFalse(
                path.contains("Antigravity IDE", ignoreCase = true) || path.contains("Antigravity-IDE", ignoreCase = true),
                "App 候选路径中不得包含 Antigravity IDE 路径: $path"
            )
            assertFalse(
                path.endsWith("/agy") || path.endsWith("/agy.exe"),
                "App 候选路径中不得包含 CLI 二进制路径: $path"
            )
        }
    }

    @Test
    fun testCliCandidatePathsOnlyContainCliBinaries() {
        val cliCandidates = CliHostManager.getCandidateInstallations()
        assertTrue(cliCandidates.isNotEmpty(), "CLI 候选路径列表不应为空")
        for (candidate in cliCandidates) {
            val path = candidate.absolutePath.replace('\\', '/')
            assertFalse(
                path.endsWith(".app") || path.contains("Antigravity IDE"),
                "CLI 候选路径中不得包含 IDE 或 App Bundle: $path"
            )
            assertTrue(
                path.contains("agy", ignoreCase = true) || path.contains("antigravity", ignoreCase = true),
                "CLI 候选路径应指向 CLI 工具: $path"
            )
        }
    }

    @Test
    fun testHostCandidatePathsAreMutuallyExclusive() {
        val idePaths = IdeHostManager.getCandidateInstallations().map { it.absolutePath.replace('\\', '/') }.toSet()
        val appPaths = AppHostManager.getCandidateInstallations().map { it.absolutePath.replace('\\', '/') }.toSet()
        val cliPaths = CliHostManager.getCandidateInstallations().map { it.absolutePath.replace('\\', '/') }.toSet()

        val ideAppIntersection = idePaths.intersect(appPaths)
        assertTrue(
            ideAppIntersection.isEmpty(),
            "IDE 与 App 候选路径集合必须严格互斥，发现重叠路径: $ideAppIntersection"
        )

        val ideCliIntersection = idePaths.intersect(cliPaths)
        assertTrue(
            ideCliIntersection.isEmpty(),
            "IDE 与 CLI 候选路径集合必须严格互斥，发现重叠路径: $ideCliIntersection"
        )

        val appCliIntersection = appPaths.intersect(cliPaths)
        assertTrue(
            appCliIntersection.isEmpty(),
            "App 与 CLI 候选路径集合必须严格互斥，发现重叠路径: $appCliIntersection"
        )
    }
}
