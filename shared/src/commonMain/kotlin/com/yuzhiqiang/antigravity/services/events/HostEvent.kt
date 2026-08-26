package com.yuzhiqiang.antigravity.services.events

/**
 * 宿主文件与进程退出事件定义。
 */
sealed interface HostEvent {

    /**
     * 宿主物理文件变更事件（如 state.vscdb、oauth_credentials.json 等）
     */
    data class FileModified(
        val path: String,
        val fileName: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : HostEvent

    /**
     * 宿主进程退出事件（IDE、App、Language Server 进程终止）
     */
    data class ProcessExited(
        val label: String,
        val pid: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : HostEvent

}
