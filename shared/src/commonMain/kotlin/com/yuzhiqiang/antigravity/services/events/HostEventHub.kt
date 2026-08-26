package com.yuzhiqiang.antigravity.services.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * 全局宿主事件分发与订阅中枢。
 * 承载文件变更和进程退出事件，解耦被动监听源与业务响应逻辑。
 */
object HostEventHub {

    private val _events = MutableSharedFlow<HostEvent>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<HostEvent> = _events.asSharedFlow()

    /**
     * 文件变动事件流
     */
    val fileEvents = events.filterIsInstance<HostEvent.FileModified>()

    /**
     * 进程退出事件流
     */
    val processExitEvents = events.filterIsInstance<HostEvent.ProcessExited>()

    /**
     * 发布事件（线程安全、非阻塞）
     */
    fun emit(event: HostEvent) {
        _events.tryEmit(event)
    }

    /**
     * 协程挂起发布事件
     */
    suspend fun emitSuspend(event: HostEvent) {
        _events.emit(event)
    }
}
