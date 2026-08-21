package com.yuzhiqiang.antigravity.studio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform