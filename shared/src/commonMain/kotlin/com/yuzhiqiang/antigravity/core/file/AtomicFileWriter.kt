package com.yuzhiqiang.antigravity.core.file

import java.io.File
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * 统一原子写入工具，封装临时文件写入、跨平台原子重命名、失败回退及 POSIX 权限保护。
 */
internal object AtomicFileWriter {

    private val isPosix: Boolean = System.getProperty("os.name", "").lowercase().let { osName ->
        !osName.contains("win") || osName.contains("darwin")
    }

    /**
     * 以原子方式写入文本内容。
     */
    fun writeText(
        target: File,
        content: String,
        charset: Charset = Charsets.UTF_8,
        preservePermissions: Boolean = false,
        disallowSymlinks: Boolean = false
    ): Result<Unit> {
        return writeBytes(
            target = target,
            content = content.toByteArray(charset),
            preservePermissions = preservePermissions,
            disallowSymlinks = disallowSymlinks
        )
    }

    /**
     * 以原子方式写入字节数据。
     */
    fun writeBytes(
        target: File,
        content: ByteArray,
        preservePermissions: Boolean = false,
        disallowSymlinks: Boolean = false
    ): Result<Unit> {
        return runCatching {
            val targetPath = target.toPath()
            if (disallowSymlinks && Files.isSymbolicLink(targetPath)) {
                error("写入目标不能是符号链接：" + target.absolutePath)
            }

            val parent = target.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                error("无法创建目标父级目录：" + parent.absolutePath)
            }

            val originalPermissions = if (preservePermissions && isPosix) {
                runCatching {
                    if (target.exists() && !Files.isSymbolicLink(targetPath)) {
                        Files.getPosixFilePermissions(targetPath)
                    } else {
                        null
                    }
                }.getOrNull()
            } else {
                null
            }

            val temp = File.createTempFile(target.name + "-", ".tmp", parent)
            try {
                temp.writeBytes(content)
                val moved = try {
                    try {
                        Files.move(temp.toPath(), targetPath, ATOMIC_MOVE, REPLACE_EXISTING)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(temp.toPath(), targetPath, REPLACE_EXISTING)
                    }
                    true
                } catch (_: Exception) {
                    // Windows 下当目标文件被其他进程占用时，Files.move 可能会报错，回退直接写入
                    false
                }

                if (!moved) {
                    target.writeBytes(content)
                }

                if (preservePermissions && isPosix && originalPermissions != null) {
                    runCatching { Files.setPosixFilePermissions(targetPath, originalPermissions) }
                }
            } finally {
                if (temp.exists()) {
                    temp.delete()
                }
            }
        }
    }
}
