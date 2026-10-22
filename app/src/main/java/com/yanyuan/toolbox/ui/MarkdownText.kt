package com.yanyuan.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ★ 提到顶层复用，避免每渲染一行都重新编译一次正则。 */
private val NUMBERED_LINE = Regex("^\\s*\\d+[.)]\\s+(.+)$")

/** 代码块 ``` 判定 */
private val FENCE = "```"
private val TABLE_DIVIDER = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    // ★ 按内容缓存解析结果：重组时不再重新逐行扫描与切分字符串
    val lines = remember(text) { text.lines() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trim().startsWith(FENCE) -> {
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(FENCE)) {
                        code.add(lines[i]); i++
                    }
                    CodeBlock(code.joinToString("\n"))
                }
                line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) -> {
                    val headers = tableCells(line)
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        rows += tableCells(lines[i])
                        i++
                    }
                    MarkdownTable(headers, rows)
                    i--
                }
                line.startsWith("### ") -> Heading(line.removePrefix("### "), 15.sp)
                line.startsWith("## ") -> Heading(line.removePrefix("## "), 17.sp)
                line.startsWith("# ") -> Heading(line.removePrefix("# "), 19.sp)
                line.trim().startsWith("> ") -> Quote(line.trim().removePrefix("> "))
                line.trim().startsWith("- ") || line.trim().startsWith("* ") ->
                    Bullet(line.trim().drop(2))
                numberedText(line) != null -> Bullet(numberedText(line)!!, ordered = true)
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Body(line)
            }
            i++
        }
    }
}

@Composable
private fun MarkdownTable(headers: List<String>, rows: List<List<String>>) {
    if (headers.isEmpty()) return
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    val bodyBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxOf(104.dp, maxWidth / headers.size)
        Column(Modifier.horizontalScroll(remember { androidx.compose.foundation.ScrollState(0) })) {
            TableRow(headers, cellWidth, outline, headerBackground, header = true)
            rows.forEach { row ->
                TableRow(
                    cells = headers.indices.map { row.getOrElse(it) { "" } },
                    cellWidth = cellWidth,
                    outline = outline,
                    background = bodyBackground,
                    header = false
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    cellWidth: androidx.compose.ui.unit.Dp,
    outline: Color,
    background: Color,
    header: Boolean
) {
    Row {
        cells.forEach { cell ->
            Text(
                inline(cell),
                modifier = Modifier.width(cellWidth).border(0.5.dp, outline)
                    .background(background).padding(horizontal = 9.dp, vertical = 8.dp),
                fontSize = 13.sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
    }
}

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map(String::trim)

@Composable private fun Heading(t: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(inline(t), fontSize = size, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface)
}

@Composable private fun Body(t: String) {
    Text(inline(t), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 22.sp)
}

@Composable private fun Quote(t: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(3.dp).height(20.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
        Spacer(Modifier.width(8.dp))
        Text(inline(t), fontSize = 14.sp, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Bullet(t: String, ordered: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(if (ordered) "1." else "•", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp))
        Text(inline(t), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
}

@Composable private fun CodeBlock(code: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(code, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * ★ 行内 Markdown 解析 + 缓存。
 *
 * 原实现是普通函数，每次重组都会把所有行重新扫描一遍并重建 AnnotatedString。
 * 流式输出时每秒会重组约 10 次，这段纯字符串计算就会不断占用主线程。
 * 改由 remember 按「行内容 + 主题色」缓存，内容不变就直接复用。
 */
@Composable
private fun inline(text: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, codeBg, linkColor) { buildInline(text, codeBg, linkColor) }
}

private fun buildInline(text: String, codeBg: Color, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        append(text.substring(i + 2, end)); pop(); i = end + 2
                    } else { append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                        append(text.substring(i + 1, end)); pop(); i = end + 1
                    } else { append(text[i]); i++ }
                }
                text[i] == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val endParen = if (close > 0) text.indexOf(')', close + 2) else -1
                    if (close > i && endParen > close) {
                        val label = text.substring(i + 1, close)
                        pushStyle(SpanStyle(color = linkColor))
                        append("$label "); pop()
                        i = endParen + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }

private fun numberedText(line: String): String? {
    val m = NUMBERED_LINE.find(line) ?: return null
    return m.groupValues[1]
}
