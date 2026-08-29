package com.yuzhiqiang.antigravity.proxy.parser

import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import kotlin.test.Test
import kotlin.test.assertEquals

class AntigravityRequestParserTest {

    @Test
    fun parserMergesThinkingPartsAndKeepsStableGeneratedToolIds() {
        val request = AntigravityRequestParser.parse(
            """
            {
              "model":"vm-1",
              "contents":[{
                "role":"model",
                "parts":[
                  {"thought":true,"text":"reason "},
                  {"thought":true,"text":"summary","thoughtSignature":"signed"},
                  {"functionCall":{"name":"lookup","args":{"id":1}}},
                  {"functionCall":{"name":"lookup","args":{"id":2}}}
                ]
              }]
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(
            "reason summary",
            (request.messages[0].contents[0] as NeutralContent.Thinking).text
        )
        assertEquals(
            "signed",
            (request.messages[0].contents[0] as NeutralContent.Thinking).signature
        )
        assertEquals(
            "call_0_2",
            (request.messages[0].contents[1] as NeutralContent.ToolCall).id
        )
        assertEquals(
            "call_0_3",
            (request.messages[0].contents[2] as NeutralContent.ToolCall).id
        )
    }
}
