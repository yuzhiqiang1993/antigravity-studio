package com.yuzhiqiang.antigravity.services.auth

import kotlin.test.*

class RefreshTokenParserTest {

    @Test
    fun testParseSingleRawToken() {
        val input = "1//0gABC_12345-xyz"
        val entries = RefreshTokenParser.parse(input)
        assertEquals(1, entries.size)
        assertEquals("1//0gABC_12345-xyz", entries[0].token)
        assertNull(entries[0].email)
    }

    @Test
    fun testParseMultipleLinesTokens() {
        val input = """
            1//0gToken1_ABC
            # 这里是一行注释
            1//0gToken2_DEF
            // 这里也是注释
            1//0gToken3_GHI
        """.trimIndent()

        val entries = RefreshTokenParser.parse(input)
        assertEquals(3, entries.size)
        assertEquals("1//0gToken1_ABC", entries[0].token)
        assertEquals("1//0gToken2_DEF", entries[1].token)
        assertEquals("1//0gToken3_GHI", entries[2].token)
    }

    @Test
    fun testParseEmailTokenSeparatedLines() {
        val input = """
            user1@gmail.com:1//0gToken1
            user2@company.com----1//0gToken2
            1//0gToken3,user3@antigravity.ai
        """.trimIndent()

        val entries = RefreshTokenParser.parse(input)
        assertEquals(3, entries.size)
        assertEquals("user1@gmail.com", entries[0].email)
        assertEquals("1//0gToken1", entries[0].token)

        assertEquals("user2@company.com", entries[1].email)
        assertEquals("1//0gToken2", entries[1].token)

        assertEquals("user3@antigravity.ai", entries[2].email)
        assertEquals("1//0gToken3", entries[2].token)
    }

    @Test
    fun testParseJsonArray() {
        val input = """
            [
              {
                "email": "user1@gmail.com",
                "refresh_token": "1//0gToken1",
                "custom_note": "主号"
              },
              {
                "email": "user2@gmail.com",
                "refreshToken": "1//0gToken2"
              }
            ]
        """.trimIndent()

        val entries = RefreshTokenParser.parse(input)
        assertEquals(2, entries.size)
        assertEquals("user1@gmail.com", entries[0].email)
        assertEquals("1//0gToken1", entries[0].token)
        assertEquals("主号", entries[0].customNote)

        assertEquals("user2@gmail.com", entries[1].email)
        assertEquals("1//0gToken2", entries[1].token)
    }

    @Test
    fun testParseCockpitExportAndStudioBackupJson() {
        val input = """
            {
              "version": 1,
              "accounts": [
                {
                  "id": "acc_1",
                  "profile": {
                    "email": "test@gmail.com",
                    "name": "Tester"
                  },
                  "tokens": {
                    "access_token": "access",
                    "refresh_token": "1//0gTokenFromBackup",
                    "expiry_timestamp": 1234567890
                  }
                }
              ]
            }
        """.trimIndent()

        val entries = RefreshTokenParser.parse(input)
        assertEquals(1, entries.size)
        assertEquals("test@gmail.com", entries[0].email)
        assertEquals("1//0gTokenFromBackup", entries[0].token)
        assertEquals("Tester", entries[0].name)
    }
}
