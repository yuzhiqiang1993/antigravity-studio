package com.yuzhiqiang.antigravity.services.auth

/**
 * 轻量级纯 Kotlin Protobuf 解码器，用于解析宿主状态与凭证
 */
object ProtobufExtractor {

    data class ProtoField(
        val fieldNumber: Int,
        val wireType: Int,
        val varintValue: Long? = null,
        val bytesValue: ByteArray? = null
    )

    private fun decodeVarint(buffer: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (pos < buffer.size) {
            val byte = buffer[pos++].toLong()
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80L) == 0L) break
            shift += 7
        }
        return Pair(result, pos)
    }

    fun readFields(buffer: ByteArray): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        var offset = 0
        while (offset < buffer.size) {
            try {
                val (tag, nextOffset) = decodeVarint(buffer, offset)
                offset = nextOffset
                val fieldNumber = (tag ushr 3).toInt()
                val wireType = (tag and 0x7L).toInt()

                when (wireType) {
                    0 -> { // Varint
                        val (v, afterVarint) = decodeVarint(buffer, offset)
                        offset = afterVarint
                        fields.add(ProtoField(fieldNumber, wireType, varintValue = v))
                    }
                    2 -> { // Length-delimited
                        val (len, afterLen) = decodeVarint(buffer, offset)
                        val length = len.toInt()
                        val bytes = buffer.copyOfRange(afterLen, (afterLen + length).coerceAtMost(buffer.size))
                        offset = afterLen + length
                        fields.add(ProtoField(fieldNumber, wireType, bytesValue = bytes))
                    }
                    5 -> { // 32-bit
                        offset += 4
                    }
                    1 -> { // 64-bit
                        offset += 8
                    }
                    else -> break
                }
            } catch (_: Exception) {
                break
            }
        }
        return fields
    }

    fun extractStringField(buffer: ByteArray, targetFieldNumber: Int): String? {
        val fields = readFields(buffer)
        val match = fields.firstOrNull { it.fieldNumber == targetFieldNumber && it.wireType == 2 }
        return match?.bytesValue?.decodeToString()
    }
}
