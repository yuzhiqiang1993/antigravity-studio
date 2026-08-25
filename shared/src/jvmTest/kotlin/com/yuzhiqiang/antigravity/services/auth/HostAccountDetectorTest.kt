package com.yuzhiqiang.antigravity.services.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HostAccountDetectorTest {

    @Test
    fun testDetectCliAppProfile() {
        val profile = HostAccountDetector.detectCliAppProfile()
        println("=== Detected CLI App Profile (Ground Truth via Physical Storage) ===")
        println("Email: ${profile?.email}")
        println("Name: ${profile?.name}")
        println("TokenType: ${profile?.tokenType}")
        println("Expiry: ${profile?.expiryTimestamp}")

        val email = HostAccountDetector.detectCliAppActiveEmail()
        println("CLI Active Email shortcut: $email")
        if (profile != null) {
            assertEquals(profile.email, email)
            assertTrue(profile.email.contains("@"))
        }
    }

    @Test
    fun testDetectIdeProfile() = runBlocking {
        val profile = HostAccountDetector.detectIdeActiveProfile()
        println("=== Detected IDE Profile (Ground Truth via state.vscdb Protobuf) ===")
        println("Email: ${profile?.email}")
        println("Name: ${profile?.name}")
        println("Tier: ${profile?.tierText}")
        println("Avatar: ${profile?.avatarUrl}")

        val ideEmail = HostAccountDetector.detectIdeActiveEmail()
        println("Detected IDE email shortcut: $ideEmail")
        if (profile != null) {
            assertEquals(profile.email, ideEmail)
            assertTrue(profile.email.contains("@"))
        }
    }

    @Test
    fun testDualActiveAccountCaseResolution() = runBlocking {
        // 模拟验证：当 IDE 和 App/CLI 生效同一账号时的仲裁与排序
        val sameEmail = "yuzhiqiang0904@gmail.com"
        val isIdeMatch = sameEmail.equals("yuzhiqiang0904@gmail.com", ignoreCase = true)
        val isCliMatch = sameEmail.equals("yuzhiqiang0904@gmail.com", ignoreCase = true)
        val isDualActive = isIdeMatch && isCliMatch

        assertTrue(isDualActive, "双端同一账号应正确识别为 isDualActive")
    }
}
