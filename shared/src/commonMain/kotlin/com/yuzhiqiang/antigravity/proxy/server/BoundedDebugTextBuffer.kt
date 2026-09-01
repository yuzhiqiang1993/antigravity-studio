package com.yuzhiqiang.antigravity.proxy.server

/** 仅限制 Debug 副本大小；真实响应仍按原始内容完整转发。 */
internal class BoundedDebugTextBuffer(
    private val maxChars: Int = DEFAULT_MAX_CHARS
) {
    init {
        require(maxChars > 0) { "maxChars must be greater than zero" }
    }

    private val content = StringBuilder(minOf(maxChars, INITIAL_CAPACITY))
    private var omittedChars = 0L

    fun append(value: String) {
        val remaining = maxChars - content.length
        if (remaining > 0) {
            val accepted = minOf(remaining, value.length)
            content.append(value, 0, accepted)
            omittedChars += (value.length - accepted).toLong()
        } else {
            omittedChars += value.length.toLong()
        }
    }

    override fun toString(): String {
        if (omittedChars == 0L) return content.toString()
        return buildString(content.length + TRUNCATION_MARKER_CAPACITY) {
            append(content)
            append("\n\n... [Truncated: ")
            append(omittedChars)
            append(" chars omitted for performance]")
        }
    }

    private companion object {
        const val DEFAULT_MAX_CHARS = 1_000_000
        const val INITIAL_CAPACITY = 16_384
        const val TRUNCATION_MARKER_CAPACITY = 80
    }
}
