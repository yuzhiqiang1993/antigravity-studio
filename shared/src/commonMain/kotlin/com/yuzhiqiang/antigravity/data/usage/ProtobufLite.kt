package com.yuzhiqiang.antigravity.data.usage

/**
 * 极简纯 Kotlin Protobuf 解码器。
 *
 * 用量数据库由宿主持续写入，解析器必须同时支持“宽松读取”和“严格读取”：
 * 宽松读取便于兼容未知字段，严格读取则让调用方知道一行数据是否被截断，
 * 从而避免把半条数据库内容当成成功检查点缓存下来。
 */
object ProtobufLite {

    data class Field(
        val number: Int,
        val wireType: Int,
        val varint: Long = 0L,
        val bytes: ByteArray? = null
    ) {
        fun asString(): String = bytes?.toString(Charsets.UTF_8) ?: ""
    }

    /** 兼容旧调用方的宽松读取；格式异常时返回已解析部分。 */
    fun readFields(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<Field> {
        val fields = mutableListOf<Field>()
        val end = (offset + length).coerceAtMost(data.size)
        var pos = offset.coerceIn(0, data.size)

        while (pos < end) {
            val tagResult = decodeVarint(data, pos, end) ?: break
            val tag = tagResult.first
            pos = tagResult.second
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber <= 0) break

            when (wireType) {
                0 -> {
                    val value = decodeVarint(data, pos, end) ?: break
                    fields += Field(fieldNumber, wireType, varint = value.first)
                    pos = value.second
                }

                1 -> {
                    if (pos + 8 > end) break
                    fields += Field(fieldNumber, wireType)
                    pos += 8
                }

                2 -> {
                    val lenResult = decodeVarint(data, pos, end) ?: break
                    val len = lenResult.first.toIntOrNullLength() ?: break
                    pos = lenResult.second
                    if (len < 0 || pos > end - len) break
                    fields += Field(fieldNumber, wireType, bytes = data.copyOfRange(pos, pos + len))
                    pos += len
                }

                5 -> {
                    if (pos + 4 > end) break
                    fields += Field(fieldNumber, wireType)
                    pos += 4
                }

                else -> break
            }
        }
        return fields
    }

    /**
     * 严格读取一个完整 Protobuf message。
     * 返回 null 表示 tag、varint 或 length-delimited payload 被截断/非法。
     */
    fun readFieldsStrict(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<Field>? {
        if (offset < 0 || length < 0 || offset > data.size || length > data.size - offset) return null
        val fields = mutableListOf<Field>()
        val end = offset + length
        var pos = offset

        while (pos < end) {
            val tagResult = decodeVarint(data, pos, end) ?: return null
            val tag = tagResult.first
            if (tag <= 0L) return null
            pos = tagResult.second
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber <= 0 || wireType == 4) return null

            when (wireType) {
                0 -> {
                    val value = decodeVarint(data, pos, end) ?: return null
                    fields += Field(fieldNumber, wireType, varint = value.first)
                    pos = value.second
                }

                1 -> {
                    if (pos > end - 8) return null
                    fields += Field(fieldNumber, wireType)
                    pos += 8
                }

                2 -> {
                    val lenResult = decodeVarint(data, pos, end) ?: return null
                    val len = lenResult.first.toIntOrNullLength() ?: return null
                    pos = lenResult.second
                    if (pos > end - len) return null
                    fields += Field(fieldNumber, wireType, bytes = data.copyOfRange(pos, pos + len))
                    pos += len
                }

                5 -> {
                    if (pos > end - 4) return null
                    fields += Field(fieldNumber, wireType)
                    pos += 4
                }

                else -> return null
            }
        }
        return fields
    }

    fun decodeVarint(data: ByteArray, offset: Int, end: Int = data.size): Pair<Long, Int>? {
        if (offset < 0 || offset >= end || end > data.size) return null
        var result = 0L
        var shift = 0
        var pos = offset

        while (pos < end && shift < 64) {
            val byte = data[pos++].toInt() and 0xff
            if (shift == 63 && byte > 1) return null
            result = result or ((byte and 0x7f).toLong() shl shift)
            if ((byte and 0x80) == 0) return result to pos
            shift += 7
        }
        return null
    }

    fun findField(fields: List<Field>, fieldNumber: Int, wireType: Int? = null): Field? {
        return fields.firstOrNull { it.number == fieldNumber && (wireType == null || it.wireType == wireType) }
    }

    private fun Long.toIntOrNullLength(): Int? {
        return takeIf { it >= 0L && it <= Int.MAX_VALUE }?.toInt()
    }
}
