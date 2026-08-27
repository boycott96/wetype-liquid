package com.wetype.liquid.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.wetype.liquid.config.ConfigBridge
import com.wetype.liquid.discovery.SafeHook
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object DiagnosticsReporter {
    private const val DEBOUNCE_DELAY_MS = 800L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val isScheduled = AtomicBoolean(false)

    private val reportRunnable = Runnable {
        isScheduled.set(false)
        executeReport()
    }

    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun requestReport(context: Context? = null, immediate: Boolean = false) {
        if (context != null) {
            appContext = context.applicationContext
        }
        val ctx = appContext ?: return

        if (immediate) {
            mainHandler.removeCallbacks(reportRunnable)
            isScheduled.set(false)
            backgroundExecutor.execute {
                executeReportInternal(ctx)
            }
        } else {
            if (!isScheduled.getAndSet(true)) {
                mainHandler.postDelayed(reportRunnable, DEBOUNCE_DELAY_MS)
            }
        }
    }

    private fun executeReport() {
        val ctx = appContext ?: return
        backgroundExecutor.execute {
            executeReportInternal(ctx)
        }
    }

    private fun executeReportInternal(context: Context) {
        SafeHook.runSafe("ExecuteDiagnosticsReport") {
            ConfigBridge.reportDiagnostics(context)
        }
    }
}
