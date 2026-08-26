package com.yuzhiqiang.antigravity.services.auth

import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * 轻量级 Protobuf 二进制编码器与 UnifiedState 构造器。
 * 严格对齐 Antigravity 官方 TypeScript 实现，构造结构无缝兼容的 UnifiedStateSync 状态对象。
 */
object ProtobufEncoder {

    /**
     * 将整数编码为 Protobuf Varint 字节数组
     */
    fun encodeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        if (v == 0L) {
            return byteArrayOf(0)
        }
        while (v > 0x7FL || v < 0L) {
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
            if (v == 0L) break
        }
        if (v != 0L || out.size() == 0) {
            out.write((v and 0x7FL).toInt())
        }
        return out.toByteArray()
    }

    /**
     * 编码字段 Tag (fieldNumber << 3 | wireType)
     */
    fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray {
        val tagValue = ((fieldNumber.toLong() shl 3) or (wireType.toLong() and 0x7L))
        return encodeVarint(tagValue)
    }

    /**
     * 编码长度定界字段 (Wire Type 2)
     */
    fun encodeLengthDelimited(fieldNumber: Int, data: ByteArray): ByteArray {
        val tag = encodeTag(fieldNumber, 2)
        val len = encodeVarint(data.size.toLong())
        return tag + len + data
    }

    /**
     * 编码 UTF-8 字符串字段 (Wire Type 2)
     */
    fun encodeStringField(fieldNumber: Int, str: String): ByteArray {
        return encodeLengthDelimited(fieldNumber, str.toByteArray(Charsets.UTF_8))
    }

    /**
     * 编码 Varint 字段 (Wire Type 0)
     */
    fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
        val tag = encodeTag(fieldNumber, 0)
        val v = encodeVarint(value)
        return tag + v
    }

    /**
     * 构造最小 UserStatus Protobuf 二进制载荷
     * 严格对齐官方 createMinimalUssStatus(email): Field 3 = name/email, Field 7 = email
     */
    fun createMinimalUssStatus(email: String, name: String? = null): ByteArray {
        val effectiveName = name?.takeIf { it.isNotBlank() } ?: email
        return encodeStringField(3, effectiveName) +
                encodeStringField(7, email)
    }

    /**
     * 构造 OAuthTokenInfo Protobuf 二进制载荷
     */
    fun createOAuthInfo(
        accessToken: String,
        refreshToken: String,
        expirySeconds: Long,
        email: String? = null,
        isGcpTos: Boolean = false
    ): ByteArray {
        val isPersonalEmail = email?.let {
            val lower = it.lowercase()
            lower.endsWith("@gmail.com") || lower.endsWith("@googlemail.com") ||
                    lower.endsWith("@outlook.com") || lower.endsWith("@hotmail.com") ||
                    lower.endsWith("@qq.com") || lower.endsWith("@163.com")
        } ?: false

        val effectiveGcpTos = if (isPersonalEmail) false else isGcpTos

        val bufs = mutableListOf<ByteArray>()
        bufs.add(encodeStringField(1, accessToken))
        bufs.add(encodeStringField(2, "Bearer"))
        bufs.add(encodeStringField(3, refreshToken))

        val timestampInner = encodeTag(1, 0) + encodeVarint(expirySeconds) +
                encodeTag(2, 0) + encodeVarint(0L)
        bufs.add(encodeLengthDelimited(4, timestampInner))

        if (effectiveGcpTos) {
            bufs.add(encodeTag(6, 0) + encodeVarint(1L))
        }

        var total = ByteArray(0)
        for (b in bufs) {
            total += b
        }
        return total
    }

    /**
     * 构造 UnifiedState 嵌套封装体 (返回 Base64 编码的最终 Topic 字符串)
     */
    fun createUnifiedStateEntry(sentinelKey: String, payload: ByteArray): String {
        val base64Payload = Base64.getEncoder().encodeToString(payload)
        val row = encodeStringField(1, base64Payload)
        val dataEntry = encodeStringField(1, sentinelKey) + encodeLengthDelimited(2, row)
        val topic = encodeLengthDelimited(1, dataEntry)
        return Base64.getEncoder().encodeToString(topic)
    }
}
