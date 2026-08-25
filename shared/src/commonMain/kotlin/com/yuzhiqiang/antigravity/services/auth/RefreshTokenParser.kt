package com.yuzhiqiang.antigravity.services.auth

import kotlinx.serialization.json.*

data class RefreshTokenEntry(
    val token: String,
    val email: String? = null,
    val name: String? = null,
    val customNote: String? = null
)

/**
 * 账号与 Refresh Token 输入解析引擎。
 * 1:1 对齐 Cockpit 插件，支持单 Token、多行 Token、标准备份 JSON、Studio accounts.v1.json 以及多格式混合输入。
 */
object RefreshTokenParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowComments = true
    }

    private val TOKEN_REGEX = Regex("1//[a-zA-Z0-9_\\-]+")
    private val EMAIL_REGEX = Regex("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+$")

    fun parse(input: String): List<RefreshTokenEntry> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. 尝试作为 JSON 解析 (JSON Array, JSON Object, 嵌套 accounts 数组等)
        val jsonEntries = tryParseJson(trimmed)
        if (jsonEntries.isNotEmpty()) {
            return jsonEntries.distinctBy { it.token }
        }

        // 2. 尝试作为按行文本解析 (支持纯 Token、email:token、email----token 等)
        val lineEntries = parseLines(trimmed)
        if (lineEntries.isNotEmpty()) {
            return lineEntries.distinctBy { it.token }
        }

        // 3. 兜底正则全局提取所有的 1//... Refresh Token
        return TOKEN_REGEX.findAll(trimmed).map { match ->
            RefreshTokenEntry(token = match.value)
        }.distinctBy { it.token }.toList()
    }

    private fun tryParseJson(input: String): List<RefreshTokenEntry> {
        return try {
            val element = json.parseToJsonElement(input)
            when (element) {
                is JsonArray -> element.mapNotNull { extractEntryFromObject(it) }
                is JsonObject -> {
                    // 支持 {"accounts": [...]} 或 {"trackedAccounts": [...]}
                    val accountsArray = (element["accounts"] as? JsonArray)
                        ?: (element["trackedAccounts"] as? JsonArray)
                    if (accountsArray != null) {
                        accountsArray.mapNotNull { extractEntryFromObject(it) }
                    } else {
                        extractEntryFromObject(element)?.let { listOf(it) } ?: emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractEntryFromObject(element: JsonElement): RefreshTokenEntry? {
        val obj = element as? JsonObject ?: return null

        // 兼容 tokens 对象嵌套结构 {"tokens": {"refresh_token": "..."}}
        val tokensObj = obj["tokens"] as? JsonObject
        val profileObj = obj["profile"] as? JsonObject

        val token = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: obj["refreshToken"]?.jsonPrimitive?.contentOrNull
            ?: obj["token"]?.jsonPrimitive?.contentOrNull
            ?: tokensObj?.get("refresh_token")?.jsonPrimitive?.contentOrNull
            ?: tokensObj?.get("refreshToken")?.jsonPrimitive?.contentOrNull
            ?: return null

        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return null

        val email = obj["email"]?.jsonPrimitive?.contentOrNull
            ?: obj["user_email"]?.jsonPrimitive?.contentOrNull
            ?: obj["antigravity_cockpit_active_email"]?.jsonPrimitive?.contentOrNull
            ?: profileObj?.get("email")?.jsonPrimitive?.contentOrNull

        val name = obj["name"]?.jsonPrimitive?.contentOrNull
            ?: profileObj?.get("name")?.jsonPrimitive?.contentOrNull

        val customNote = obj["custom_note"]?.jsonPrimitive?.contentOrNull
            ?: obj["customNote"]?.jsonPrimitive?.contentOrNull
            ?: obj["note"]?.jsonPrimitive?.contentOrNull

        val normalizedEmail = email?.trim()?.takeIf { EMAIL_REGEX.matches(it) }

        return RefreshTokenEntry(
            token = cleanToken,
            email = normalizedEmail,
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            customNote = customNote?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseLines(input: String): List<RefreshTokenEntry> {
        val results = mutableListOf<RefreshTokenEntry>()
        val lines = input.lines()

        for (rawLine in lines) {
            val line = rawLine.trim().removePrefix("-").removePrefix("*").trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            // 格式 A: email:token 或 email----token 或 email,token 或 email token
            val separators = listOf("----", "---", "::", ":", ",", "\t", " ")
            var matched = false

            for (sep in separators) {
                if (line.contains(sep)) {
                    val parts = line.split(sep)
                    if (parts.size >= 2) {
                        val first = parts[0].trim().trim('"', '\'')
                        val second = parts[1].trim().trim('"', '\'')

                        if (EMAIL_REGEX.matches(first) && TOKEN_REGEX.containsMatchIn(second)) {
                            val token = TOKEN_REGEX.find(second)?.value ?: second
                            results.add(RefreshTokenEntry(token = token, email = first))
                            matched = true
                            break
                        } else if (TOKEN_REGEX.containsMatchIn(first) && EMAIL_REGEX.matches(second)) {
                            val token = TOKEN_REGEX.find(first)?.value ?: first
                            results.add(RefreshTokenEntry(token = token, email = second))
                            matched = true
                            break
                        }
                    }
                }
            }

            // 格式 B: 纯行 Refresh Token
            if (!matched) {
                val tokenMatch = TOKEN_REGEX.find(line)
                if (tokenMatch != null) {
                    results.add(RefreshTokenEntry(token = tokenMatch.value))
                }
            }
        }

        return results
    }
}
