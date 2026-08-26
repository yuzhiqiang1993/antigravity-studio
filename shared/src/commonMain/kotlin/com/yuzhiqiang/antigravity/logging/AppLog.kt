package com.yuzhiqiang.antigravity.logging

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.DefaultFormatter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.Severity

/**
 * 通用日志门面工具类 (基于 Kermit)。
 *
 * ## 调试与生产环境策略
 * - **本地开发/调试阶段** ([isDebug] = true)：
 *   自动探测源码构建路径 (`build/classes`)、IDE 运行环境或 JDWP 参数，默认开启 [Severity.Debug] 分级日志输出。
 * - **生产打包安装包阶段** ([isDebug] = false)：
 *   默认完全静默 ([isEnabled] = false)，零控制台输出、零字符串拼接开销。
 *   生产环境如需临时抓取排查日志，可通过 `-Dantigravity.debug=true` 或环境变量 `ANTIGRAVITY_DEBUG=true` 动态覆盖。
 *
 * ## 设计准则与规范
 * 1. **Lambda 延迟计算**：所有日志均使用 Lambda `{ "..." }` 传入，在日志级别未开启时避免字符串拼接开销。
 * 2. **Tag 命名空间**：采用 `模块/业务` 二级结构，如 `"Auth/Switch"`, `"Host/Process"`, `"Proxy/Catalog"`。
 * 3. **敏感信息脱敏**：涉及 Token、密码等敏感凭据时，必须使用 [maskToken] 或 [maskEmail] 脱敏。
 */
object AppLog {

    const val DEFAULT_TAG = "Antigravity"

    /**
     * 是否启用日志输出（生产环境默认静默，本地调试默认开启）
     */
    var isEnabled: Boolean = detectIsDebugEnvironment()

    /**
     * 当前是否处于 Debug 调试模式（支持自动检测与运行时修改）
     */
    var isDebug: Boolean
        get() = isEnabled
        set(value) {
            isEnabled = value
        }

    /**
     * 当前生效的最小日志输出级别
     */
    var minSeverity: Severity = Severity.Debug

    @PublishedApi
    internal val mutableConfig = object : LoggerConfig {
        override val minSeverity: Severity
            get() = this@AppLog.minSeverity
        override val logWriterList = listOf(CommonWriter(DefaultFormatter))
    }

    @PublishedApi
    internal val baseLogger: Logger by lazy {
        Logger(
            config = mutableConfig,
            tag = DEFAULT_TAG
        )
    }

    /**
     * Verbose 日志 (极高频调试信息)
     */
    inline fun v(tag: String = DEFAULT_TAG, crossinline message: () -> String) {
        if (isEnabled && minSeverity <= Severity.Verbose) {
            baseLogger.withTag(tag).v { message() }
        }
    }

    /**
     * Debug 日志 (开发期调试信息)
     */
    inline fun d(tag: String = DEFAULT_TAG, crossinline message: () -> String) {
        if (isEnabled && minSeverity <= Severity.Debug) {
            baseLogger.withTag(tag).d { message() }
        }
    }

    /**
     * Info 日志 (关键业务节点、生命周期)
     */
    inline fun i(tag: String = DEFAULT_TAG, crossinline message: () -> String) {
        if (isEnabled && minSeverity <= Severity.Info) {
            baseLogger.withTag(tag).i { message() }
        }
    }

    /**
     * Warn 日志 (警告信息、非致命错误)
     */
    inline fun w(tag: String = DEFAULT_TAG, throwable: Throwable? = null, crossinline message: () -> String) {
        if (isEnabled && minSeverity <= Severity.Warn) {
            val logger = baseLogger.withTag(tag)
            if (throwable != null) {
                logger.w(throwable) { message() }
            } else {
                logger.w { message() }
            }
        }
    }

    /**
     * Error 日志 (严重错误、未捕获异常)
     */
    inline fun e(tag: String = DEFAULT_TAG, throwable: Throwable? = null, crossinline message: () -> String) {
        if (isEnabled && minSeverity <= Severity.Error) {
            val logger = baseLogger.withTag(tag)
            if (throwable != null) {
                logger.e(throwable) { message() }
            } else {
                logger.e { message() }
            }
        }
    }

    /**
     * 获取指定 Tag 的局部 Logger 包装
     */
    fun withTag(tag: String): NamedLogger = NamedLogger(baseLogger.withTag(tag))

    /**
     * 自动探测当前是否处于本地开发/调试环境
     */
    fun detectIsDebugEnvironment(): Boolean {
        // 1. 显式环境变量优先
        val envDebug = System.getenv("ANTIGRAVITY_DEBUG") ?: System.getenv("DEBUG")
        if (!envDebug.isNullOrBlank()) {
            return envDebug.equals("true", ignoreCase = true) || envDebug == "1"
        }

        // 2. 显式 JVM 属性优先
        val propDebug = System.getProperty("antigravity.debug") ?: System.getProperty("debug")
        if (!propDebug.isNullOrBlank()) {
            return propDebug.equals("true", ignoreCase = true) || propDebug == "1"
        }

        // 3. 根据代码来源路径探测：若处于 build/classes、out/production、classes/kotlin 等源码编译目录，判定为本地开发环境
        try {
            val codeSourceLocation = AppLog::class.java.protectionDomain?.codeSource?.location?.path.orEmpty()
            if (codeSourceLocation.contains("build/classes") ||
                codeSourceLocation.contains("build/processedResources") ||
                codeSourceLocation.contains("out/production") ||
                codeSourceLocation.contains("classes/kotlin")
            ) {
                return true
            }
        } catch (_: Exception) {
            // 忽略反射或安全策略异常
        }

        // 4. 检测是否附加了 JVM 调试器 (JDWP)
        try {
            val runtimeClass = Class.forName("java.lang.management.ManagementFactory")
            val getRuntimeMXBean = runtimeClass.getMethod("getRuntimeMXBean")
            val runtimeMXBean = getRuntimeMXBean.invoke(null)
            val getInputArguments = runtimeMXBean.javaClass.getMethod("getInputArguments")
            @Suppress("UNCHECKED_CAST")
            val arguments = getInputArguments.invoke(runtimeMXBean) as? List<String>
            if (arguments?.any { it.contains("jdwp") || it.contains("-Xdebug") } == true) {
                return true
            }
        } catch (_: Exception) {
            // Android 或非标准 JVM 下可能无 ManagementFactory，安全降级
        }

        // 打包后的生产发布版本默认静默
        return false
    }

    /**
     * 敏感 Token 脱敏工具：仅展示后 6 位，例如 "***a1b2c3"
     */
    fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "<empty>"
        return if (token.length <= 8) {
            "***"
        } else {
            "***" + token.takeLast(6)
        }
    }

    /**
     * 邮箱地址脱敏工具：例如 "user***@gmail.com"
     */
    fun maskEmail(email: String?): String {
        if (email.isNullOrBlank()) return "<empty>"
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return "***"
        val prefix = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val maskedPrefix = if (prefix.length <= 3) {
            prefix.take(1) + "***"
        } else {
            prefix.take(3) + "***"
        }
        return "$maskedPrefix$domain"
    }

    /**
     * 局部带固定 Tag 的 Logger 辅助类
     */
    class NamedLogger @PublishedApi internal constructor(@PublishedApi internal val kermit: Logger) {
        inline fun v(crossinline message: () -> String) {
            if (isEnabled && minSeverity <= Severity.Verbose) kermit.v { message() }
        }

        inline fun d(crossinline message: () -> String) {
            if (isEnabled && minSeverity <= Severity.Debug) kermit.d { message() }
        }

        inline fun i(crossinline message: () -> String) {
            if (isEnabled && minSeverity <= Severity.Info) kermit.i { message() }
        }

        inline fun w(throwable: Throwable? = null, crossinline message: () -> String) {
            if (isEnabled && minSeverity <= Severity.Warn) {
                if (throwable != null) kermit.w(throwable) { message() } else kermit.w { message() }
            }
        }

        inline fun e(throwable: Throwable? = null, crossinline message: () -> String) {
            if (isEnabled && minSeverity <= Severity.Error) {
                if (throwable != null) kermit.e(throwable) { message() } else kermit.e { message() }
            }
        }
    }
}
