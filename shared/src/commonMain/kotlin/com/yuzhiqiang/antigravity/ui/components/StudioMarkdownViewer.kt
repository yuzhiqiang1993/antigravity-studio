package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService

/**
 * 结构化的 Markdown 块级元素
 */
sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val orderedNumber: String?, val text: String) : MarkdownBlock
    data class Quote(val lines: List<String>) : MarkdownBlock
    data class CodeBlock(val language: String?, val content: String) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

/**
 * 轻量且原生的 Compose Desktop Markdown 查看器
 * 适用于更新日志、关于说明、模型规格等场景的富文本渲染
 */
@Composable
fun StudioMarkdownViewer(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val (fontSize, topPadding) = when (block.level) {
                        1 -> 15.sp to 10.dp
                        2 -> 14.sp to 8.dp
                        else -> 13.5.sp to 6.dp
                    }
                    val annotated = remember(block.text, onSurface, primary, codeBg) {
                        renderInlineMarkdown(block.text, onSurface, primary, codeBg)
                    }
                    Box(modifier = Modifier.padding(top = topPadding, bottom = 2.dp)) {
                        RenderInlineText(
                            annotatedText = annotated,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = onSurface
                            )
                        )
                    }
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (block.orderedNumber != null) {
                            Text(
                                text = block.orderedNumber,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                ),
                                color = primary,
                                modifier = Modifier.widthIn(min = 14.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.85f))
                            )
                        }

                        val annotated = remember(block.text, onSurfaceVariant, primary, codeBg) {
                            renderInlineMarkdown(block.text, onSurfaceVariant, primary, codeBg)
                        }
                        RenderInlineText(
                            annotatedText = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                is MarkdownBlock.Quote -> {
                    val quoteText = block.lines.joinToString("\n")
                    val annotated = remember(quoteText, onSurfaceVariant, primary, codeBg) {
                        renderInlineMarkdown(quoteText, onSurfaceVariant, primary, codeBg)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(AppTokens.Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(AppTokens.Radius.small)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(primary.copy(alpha = 0.7f))
                        )
                        RenderInlineText(
                            annotatedText = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(AppTokens.Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(AppTokens.Radius.small)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = block.content,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = onSurface
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, onSurfaceVariant, primary, codeBg) {
                        renderInlineMarkdown(block.text, onSurfaceVariant, primary, codeBg)
                    }
                    RenderInlineText(
                        annotatedText = annotated,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 渲染行内富文本，支持链接点击
 */
@Composable
private fun RenderInlineText(
    annotatedText: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text = annotatedText,
        style = style,
        modifier = modifier
    )
}

/**
 * 将 Markdown 原文分割解析为块级元素
 */
fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. 分割线 --- 或 ***
        if (trimmed.matches(Regex("^(---|-{3,}|\\*{3,})$"))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 2. 代码块 ```language ... ```
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // 跳过闭合 ```
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // 3. 标题 # ## ### ####
        val headerMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val text = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }

        // 4. 引用块 >
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Quote(quoteLines))
            continue
        }

        // 5. 无序列表项 - 或 *
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val text = trimmed.substring(2)
            blocks.add(MarkdownBlock.ListItem(null, text))
            i++
            continue
        }

        // 6. 有序列表项 1. 2.
        val orderedMatch = Regex("^(\\d+\\.)\\s+(.+)$").find(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1]
            val text = orderedMatch.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(num, text))
            i++
            continue
        }

        // 7. 普通段落
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val pLine = lines[i].trim()
            if (pLine.isEmpty() ||
                pLine.startsWith("#") ||
                pLine.startsWith("```") ||
                pLine.startsWith(">") ||
                pLine.startsWith("- ") ||
                pLine.startsWith("* ") ||
                Regex("^(\\d+\\.)\\s+").containsMatchIn(pLine) ||
                pLine.matches(Regex("^(---|-{3,}|\\*{3,})$"))
            ) {
                break
            }
            paragraphLines.add(lines[i].trim())
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }

    return blocks
}

/**
 * 行内富文本解析：粗体 **text**、行内代码 `code`、链接 [text](url)
 */
private fun renderInlineMarkdown(
    text: String,
    defaultColor: Color,
    primaryColor: Color,
    codeBackground: Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`]+?)`)|(\\[([^\\]]+?)\\]\\(([^)]+?)\\))")
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            when {
                // 粗体 **bold**
                match.value.startsWith("**") && match.value.endsWith("**") -> {
                    val content = match.groupValues[2]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(content)
                    pop()
                }
                // 行内代码 `code`
                match.value.startsWith("`") && match.value.endsWith("`") -> {
                    val content = match.groupValues[4]
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = primaryColor,
                            fontSize = 11.sp
                        )
                    )
                    append(" $content ")
                    pop()
                }
                // 链接 [label](url)
                match.value.startsWith("[") -> {
                    val label = match.groupValues[6]
                    val url = match.groupValues[7]
                    pushLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ),
                            linkInteractionListener = {
                                DesktopPlatformService.openBrowser(url)
                            }
                        )
                    )
                    append(label)
                    pop()
                }
            }
            cursor = end
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
