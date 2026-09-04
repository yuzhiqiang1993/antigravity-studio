package com.yuzhiqiang.antigravity.update.model

data class ParsedChangelog(
    val chineseContent: String?,
    val englishContent: String?,
    val rawContent: String
) {
    val hasBilingual: Boolean = !chineseContent.isNullOrBlank() && !englishContent.isNullOrBlank()
}

/**
 * 智能解析 GitHub Release 或 CHANGELOG Markdown 中的中英文分段
 */
object UpdateChangelogParser {
    fun parse(rawMarkdown: String?): ParsedChangelog {
        if (rawMarkdown.isNullOrBlank()) {
            return ParsedChangelog(null, null, "")
        }

        val lines = rawMarkdown.lines()
        var inZh = false
        var inEn = false
        val zhLines = mutableListOf<String>()
        val enLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("###") && (trimmed.contains("中文") || trimmed.contains("🇨🇳"))) {
                inZh = true
                inEn = false
                continue
            }
            if (trimmed.startsWith("###") && (trimmed.contains("English", ignoreCase = true) || trimmed.contains("🌐"))) {
                inZh = false
                inEn = true
                continue
            }
            if (trimmed == "---" && (inZh || inEn)) {
                continue
            }

            if (inZh) {
                zhLines.add(line)
            } else if (inEn) {
                enLines.add(line)
            }
        }

        val zhResult = zhLines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        val enResult = enLines.joinToString("\n").trim().takeIf { it.isNotEmpty() }

        return ParsedChangelog(
            chineseContent = zhResult,
            englishContent = enResult,
            rawContent = rawMarkdown.trim()
        )
    }
}
