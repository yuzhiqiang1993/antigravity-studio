package com.yuzhiqiang.antigravity.core.file

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/**
 * 同目录临时文件原子写入工具。
 *
 * 写入只允许通过 [ATOMIC_MOVE] 提交；文件系统不支持原子替换时返回失败，绝不降级为直接覆盖目标文件。
 */
internal object AtomicFileWriter {

    enum class PermissionPolicy {
        DEFAULT,
        PRESERVE_EXISTING,
        OWNER_ONLY
    }

    internal fun interface MoveOperation {
        fun move(source: Path, target: Path)
    }

    private val ownerOnlyPermissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    )

    private val defaultFileSystemSupportsPosix: Boolean =
        "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    private val systemAtomicMove = MoveOperation { source, target ->
        Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    fun writeText(
        target: File,
        content: String,
        charset: Charset = Charsets.UTF_8,
        permissionPolicy: PermissionPolicy = PermissionPolicy.DEFAULT,
        disallowSymlinks: Boolean = false
    ): Result<Unit> {
        return writeBytes(
            target = target,
            content = content.toByteArray(charset),
            permissionPolicy = permissionPolicy,
            disallowSymlinks = disallowSymlinks
        )
    }

    fun writeBytes(
        target: File,
        content: ByteArray,
        permissionPolicy: PermissionPolicy = PermissionPolicy.DEFAULT,
        disallowSymlinks: Boolean = false
    ): Result<Unit> {
        return writeBytes(
            target = target,
            content = content,
            permissionPolicy = permissionPolicy,
            disallowSymlinks = disallowSymlinks,
            moveOperation = systemAtomicMove
        )
    }

    internal fun writeBytes(
        target: File,
        content: ByteArray,
        permissionPolicy: PermissionPolicy,
        disallowSymlinks: Boolean,
        moveOperation: MoveOperation
    ): Result<Unit> {
        return runCatching {
            val targetPath = target.toPath().toAbsolutePath().normalize()
            val parentPath = targetPath.parent
                ?: error("写入目标缺少父目录：${target.absolutePath}")
            validateTarget(targetPath, disallowSymlinks)
            Files.createDirectories(parentPath)
            check(Files.isDirectory(parentPath)) {
                "目标父级路径不是目录：$parentPath"
            }

            var tempPath: Path? = null
            var primaryFailure: Throwable? = null
            try {
                val createdTempPath = Files.createTempFile(
                    parentPath,
                    ".${targetPath.fileName.toString().take(32)}.",
                    ".tmp"
                )
                tempPath = createdTempPath
                applyPermissionsBeforeWrite(
                    tempPath = createdTempPath,
                    targetPath = targetPath,
                    permissionPolicy = permissionPolicy
                )
                writeAndSync(createdTempPath, content)
                validateTarget(targetPath, disallowSymlinks)
                try {
                    moveOperation.move(createdTempPath, targetPath)
                    tempPath = null
                } catch (error: AtomicMoveNotSupportedException) {
                    throw IllegalStateException("文件系统不支持原子替换：$targetPath", error)
                }
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                val cleanupPath = tempPath
                if (cleanupPath != null) {
                    try {
                        Files.deleteIfExists(cleanupPath)
                    } catch (cleanupError: Throwable) {
                        primaryFailure?.addSuppressed(cleanupError) ?: throw cleanupError
                    }
                }
            }
        }
    }

    fun setOwnerOnlyPermissions(
        target: File,
        disallowSymlinks: Boolean = true
    ): Result<Unit> {
        return runCatching {
            val targetPath = target.toPath().toAbsolutePath().normalize()
            validateTarget(targetPath, disallowSymlinks)
            check(Files.exists(targetPath, NOFOLLOW_LINKS)) {
                "权限设置目标不存在：$targetPath"
            }
            if (supportsPosixPermissions(targetPath)) {
                setPosixPermissionsWithoutFollowingLinks(targetPath, ownerOnlyPermissions)
            }
        }
    }

    private fun validateTarget(targetPath: Path, disallowSymlinks: Boolean) {
        if (disallowSymlinks && Files.isSymbolicLink(targetPath)) {
            error("写入目标不能是符号链接：$targetPath")
        }
        if (Files.exists(targetPath, NOFOLLOW_LINKS) && Files.isDirectory(targetPath, NOFOLLOW_LINKS)) {
            error("写入目标不能是目录：$targetPath")
        }
    }

    private fun applyPermissionsBeforeWrite(
        tempPath: Path,
        targetPath: Path,
        permissionPolicy: PermissionPolicy
    ) {
        if (permissionPolicy == PermissionPolicy.DEFAULT || !supportsPosixPermissions(tempPath)) return
        val permissions = when (permissionPolicy) {
            PermissionPolicy.DEFAULT -> null
            PermissionPolicy.OWNER_ONLY -> ownerOnlyPermissions
            PermissionPolicy.PRESERVE_EXISTING -> {
                if (Files.exists(targetPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(targetPath)) {
                    Files.getPosixFilePermissions(targetPath, NOFOLLOW_LINKS)
                } else {
                    null
                }
            }
        }
        if (permissions != null) {
            setPosixPermissionsWithoutFollowingLinks(tempPath, permissions)
        }
    }

    private fun setPosixPermissionsWithoutFollowingLinks(
        path: Path,
        permissions: Set<PosixFilePermission>
    ) {
        val attributeView = Files.getFileAttributeView(
            path,
            PosixFileAttributeView::class.java,
            NOFOLLOW_LINKS
        ) ?: error("文件系统不支持 POSIX 权限：$path")
        attributeView.setPermissions(permissions)
    }

    private fun supportsPosixPermissions(path: Path): Boolean {
        if (!defaultFileSystemSupportsPosix) return false
        return Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView::class.java)
    }

    private fun writeAndSync(path: Path, content: ByteArray) {
        FileChannel.open(path, WRITE, TRUNCATE_EXISTING).use { channel ->
            val buffer = ByteBuffer.wrap(content)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }
}
