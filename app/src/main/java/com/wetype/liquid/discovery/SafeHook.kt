package com.wetype.liquid.discovery

import android.util.Log

object SafeHook {
    private const val TAG = "WeTypeLiquidGlass"

    enum class LogLevel {
        INFO, HOOK, DRAW, WARN, ERROR
    }

    private var enableDrawLogs = false

    fun setDrawLogs(enable: Boolean) {
        enableDrawLogs = enable
    }

    fun log(level: LogLevel, tag: String = TAG, message: String, throwable: Throwable? = null) {
        when (level) {
            LogLevel.INFO -> Log.i(tag, "[INFO] $message")
            LogLevel.HOOK -> Log.i(tag, "[HOOK] $message")
            LogLevel.DRAW -> {
                if (enableDrawLogs) Log.d(tag, "[DRAW] $message")
            }
            LogLevel.WARN -> Log.w(tag, "[WARN] $message", throwable)
            LogLevel.ERROR -> Log.e(tag, "[ERROR] $message", throwable)
        }
    }

    inline fun <T> runSafe(hookName: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            log(LogLevel.WARN, message = "SafeHook trapped exception in $hookName: ${t.message}", throwable = t)
            HookDiagnostics.recordHookFailure(hookName, t)
            fallback
        }
    }

    inline fun runSafe(hookName: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log(LogLevel.WARN, message = "SafeHook trapped exception in $hookName: ${t.message}", throwable = t)
            HookDiagnostics.recordHookFailure(hookName, t)
        }
    }
}
