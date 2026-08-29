package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.data.storage.OfficialCredentialsStore

import java.io.File

/**
 * 账号切换前捕获的各宿主及存储的原始状态快照。
 */
internal data class OriginalState(
    val ideSnapshot: StateDbInjector.Snapshot?,
    val appDbSnapshot: StateDbInjector.Snapshot?,
    val sharedCredentialsSnapshot: OfficialCredentialsStore.Snapshot?,
    val jetskiTokenSnapshot: FileSnapshot?,
    val systemCredentialSnapshot: SystemCredentialSnapshot?
) : AutoCloseable {
    override fun close() {
        sharedCredentialsSnapshot?.close()
        jetskiTokenSnapshot?.close()
        systemCredentialSnapshot?.close()
    }
}

/**
 * 单个文件的快照，记录文件存在性及原始二进制内容。
 */
internal data class FileSnapshot(
    val file: File,
    val existed: Boolean,
    val originalBytes: ByteArray
) : AutoCloseable {
    override fun close() {
        originalBytes.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSnapshot) return false
        if (file != other.file) return false
        if (existed != other.existed) return false
        if (!originalBytes.contentEquals(other.originalBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + existed.hashCode()
        result = 31 * result + originalBytes.contentHashCode()
        return result
    }
}

/**
 * 账号切换过程中已应用的变更状态追踪。
 */
internal data class AppliedChanges(
    var ideDbWritten: Boolean = false,
    var appDbWritten: Boolean = false,
    var jetskiTokenWriteAttempted: Boolean = false,
    var jetskiTokenWritten: Boolean = false,
    var sharedCredentialsWriteAttempted: Boolean = false,
    var sharedCredentialsWritten: Boolean = false,
    var systemCredentialWriteAttempted: Boolean = false,
    var ideTerminated: Boolean = false,
    var appTerminated: Boolean = false,
    var ideLaunchAttempted: Boolean = false,
    var appLaunchAttempted: Boolean = false,
    var ideUnavailable: Boolean = false,
    var appUnavailable: Boolean = false
)
