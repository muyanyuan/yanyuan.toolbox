package com.yanyuan.toolbox.terminal

/** 基础 VT 文本屏幕缓冲：处理常见清屏/清行/颜色控制序列，并保留有界行历史。 */
class TerminalScreenBuffer(private val maxLines: Int = 2_000) {
    private val lines = ArrayDeque<String>()
    private var current = StringBuilder()
    private var escape = StringBuilder()

    @Synchronized
    fun append(raw: String) {
        for (ch in raw) {
            if (escape.isNotEmpty() || ch == '\u001B') {
                consumeEscape(ch)
                continue
            }
            when (ch) {
                '\r' -> current = StringBuilder()
                '\n' -> commitLine()
                '\b' -> if (current.isNotEmpty()) current.deleteAt(current.lastIndex)
                '\t' -> repeat(4) { current.append(' ') }
                else -> if (!ch.isISOControl()) current.append(ch)
            }
        }
    }

    @Synchronized
    fun text(): String = (lines + current.toString()).joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear(); current = StringBuilder(); escape.clear()
    }

    private fun consumeEscape(ch: Char) {
        if (escape.isEmpty()) escape.append(ch) else escape.append(ch)
        if (escape.length == 1 && ch == '\u001B') return
        if (escape.length == 2 && ch == '[') return
        if (escape.length > 1 && ch in '@'..'~') {
            // CSI: 清屏/清行等只影响当前的文本视图；复杂光标语义由后续 renderer 扩展。
            if (escape.endsWith("2J") || escape.endsWith("J")) clear()
            escape.clear()
        } else if (escape.length > 32) {
            escape.clear()
        }
    }

    private fun commitLine() {
        lines.addLast(current.toString())
        while (lines.size > maxLines) lines.removeFirst()
        current = StringBuilder()
    }
}
