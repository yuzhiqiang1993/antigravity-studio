package com.yuzhiqiang.antigravity.services.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HostAccountDetectorTest {

    @Test
    fun testProtobufEncoderAndRoundtrip() {
        val targetEmail = "fixture-user@example.invalid"
        val targetName = "Fixture User"
        val payload = ProtobufEncoder.createMinimalUssStatus(targetEmail, targetName)
        val unifiedTopic = ProtobufEncoder.createUnifiedStateEntry("userStatusSentinelKey", payload)

        val decodedProfile = HostAccountDetector.parseProfileFromUserStatusRaw(unifiedTopic)
        assertNotNull(decodedProfile, "Protobuf 解码应该成功")
        assertEquals(targetEmail, decodedProfile.email, "解码邮箱应该与目标完全一致")
        assertEquals(targetName, decodedProfile.name, "解码名称应该与目标完全一致")
    }

    @Test
    fun testRealEnvironmentDetection() = kotlinx.coroutines.runBlocking {
        println("=== RuntimeIdeAccountProbe ===")
        val ideResult = RuntimeIdeAccountProbe.detectProfile()
        println("IdeResult: $ideResult")

        println("=== RuntimeAppAccountProbe ===")
        val appResult = RuntimeAppAccountProbe.detectProfile()
        println("AppResult: $appResult")

        println("=== HostAccountDetector.detectIdeAccountProbes ===")
        val ideProbes = HostAccountDetector.detectIdeAccountProbes()
        println("IdeProbes: $ideProbes")

        println("=== HostAccountDetector.detectAppCliAccountProbes ===")
        val appProbes = HostAccountDetector.detectAppCliAccountProbes()
        println("AppProbes: $appProbes")
    }
}

