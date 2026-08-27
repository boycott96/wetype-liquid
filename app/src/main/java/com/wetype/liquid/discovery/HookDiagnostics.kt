package com.wetype.liquid.discovery

import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class HookState {
    DISCOVERED,
    INSTALL_FAILED,
    INSTALLED,
    ATTACHED,
    UNHOOKED
}

data class DiscoveredClassInfo(
    val className: String,
    val category: String,
    val score: Int,
    val matchedTraits: List<String>
)

data class HookDetail(
    val id: String,
    val targetSignature: String,
    val matchReason: String,
    val installedTimestamp: Long = System.currentTimeMillis(),
    var state: HookState = HookState.DISCOVERED,
    val hitCount: AtomicLong = AtomicLong(0),
    var firstHitTimestamp: Long = 0L,
    var lastError: String? = null
) {
    val isAttached: Boolean get() = state == HookState.ATTACHED || (state == HookState.INSTALLED && hitCount.get() > 0)
}

// Data structures for Gson serialization
data class DiagnosticsReportPayload(
    val moduleVersion: String = "1.0.0",
    val reportTimestamp: Long,
    val formattedTime: String,
    val framework: FrameworkPayload,
    val environment: EnvironmentPayload,
    val targetApp: TargetAppPayload,
    val status: StatusPayload,
    val discoveredClasses: List<DiscoveredClassPayload>,
    val hooks: List<HookPayload>,
    val recentErrors: List<String>,
    val viewTree: String? = null
)

data class FrameworkPayload(
    val name: String,
    val version: String,
    val apiLevel: Int,
    val isModernLibXposed: Boolean
)

data class EnvironmentPayload(
    val androidVersion: String,
    val sdkInt: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val processName: String
)

data class TargetAppPayload(
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

data class StatusPayload(
    val blurBackend: String,
    val isCrossWindowBlurSupported: Boolean,
    val scanDurationMs: Long,
    val discoveredCount: Int
)

data class DiscoveredClassPayload(
    val className: String,
    val category: String,
    val score: Int,
    val traits: List<String>
)

data class HookPayload(
    val id: String,
    val targetSignature: String,
    val matchReason: String,
    val state: String,
    val hitCount: Long,
    val firstHitTime: String,
    val isAttached: Boolean,
    val lastError: String? = null
)

object HookDiagnostics {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var frameworkName: String = "Unknown / Inactive"
    var frameworkVersion: String = "Unknown"
    var frameworkApi: Int = 0
    var isModernLibXposed: Boolean = false

    var wetypeVersionName: String = "Unknown"
    var wetypeVersionCode: Long = 0L
    var currentProcessName: String = "Unknown"

    var activeBlurBackend: String = "Translucent Glass Surface Fallback"
    var isCrossWindowBlurSupported: Boolean = false
    var lastScanDurationMs: Long = 0L
    var discoveredClassCount: Int = 0
    var lastScannedViewTree: String = ""

    private val hooksMap = ConcurrentHashMap<String, HookDetail>()
    private val discoveredClassesList = CopyOnWriteArrayList<DiscoveredClassInfo>()
    private val recentErrorsList = CopyOnWriteArrayList<String>()

    fun resetForTest() {
        hooksMap.clear()
        discoveredClassesList.clear()
        recentErrorsList.clear()
        lastScannedViewTree = ""
    }

    fun recordHookDiscovered(id: String, targetSignature: String, matchReason: String) {
        hooksMap[id] = HookDetail(
            id = id,
            targetSignature = targetSignature,
            matchReason = matchReason,
            state = HookState.DISCOVERED
        )
    }

    fun recordHookInstalled(id: String, targetSignature: String, matchReason: String) {
        val detail = hooksMap[id]
        if (detail != null) {
            detail.state = HookState.INSTALLED
        } else {
            hooksMap[id] = HookDetail(
                id = id,
                targetSignature = targetSignature,
                matchReason = matchReason,
                state = HookState.INSTALLED
            )
        }
    }

    fun recordHookHit(id: String) {
        val detail = hooksMap[id]
        if (detail != null) {
            val count = detail.hitCount.incrementAndGet()
            detail.state = HookState.ATTACHED
            if (count == 1L) {
                detail.firstHitTimestamp = System.currentTimeMillis()
                SafeHook.log(
                    SafeHook.LogLevel.HOOK,
                    message = "First hook hit: $id -> ${detail.targetSignature}"
                )
            }
        }
    }

    fun recordHookFailure(id: String, error: Throwable) {
        val errStr = error.message ?: error.javaClass.simpleName
        recentErrorsList.add("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} [$id] $errStr")
        if (recentErrorsList.size > 20) {
            recentErrorsList.removeAt(0)
        }

        val detail = hooksMap[id]
        if (detail != null) {
            detail.state = HookState.INSTALL_FAILED
            detail.lastError = errStr
        } else {
            hooksMap[id] = HookDetail(
                id = id,
                targetSignature = "Unknown",
                matchReason = "FailedDuringInstall",
                state = HookState.INSTALL_FAILED,
                lastError = errStr
            )
        }
    }

    fun recordHookUnhooked(id: String) {
        val detail = hooksMap[id]
        if (detail != null) {
            detail.state = HookState.UNHOOKED
        }
    }

    fun recordDiscoveredClass(category: String, className: String, score: Int = 0, traits: List<String> = emptyList()) {
        discoveredClassesList.removeIf { it.className == className }
        discoveredClassesList.add(DiscoveredClassInfo(className, category, score, traits))
    }

    fun getHookDetails(): List<HookDetail> = hooksMap.values.toList()

    fun getDiscoveredClasses(): List<DiscoveredClassInfo> = discoveredClassesList.toList()

    fun getRecentErrors(): List<String> = recentErrorsList.toList()

    fun generateReportJson(): String {
        val now = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))

        val frameworkPayload = FrameworkPayload(
            name = frameworkName,
            version = frameworkVersion,
            apiLevel = frameworkApi,
            isModernLibXposed = isModernLibXposed
        )

        val envPayload = EnvironmentPayload(
            androidVersion = try { Build.VERSION.RELEASE ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            sdkInt = try { Build.VERSION.SDK_INT } catch (t: Throwable) { 0 },
            deviceManufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            deviceModel = try { Build.MODEL ?: "Unknown" } catch (t: Throwable) { "Unknown" },
            processName = currentProcessName
        )

        val targetPayload = TargetAppPayload(
            packageName = "com.tencent.wetype",
            versionName = wetypeVersionName,
            versionCode = wetypeVersionCode
        )

        val statusPayload = StatusPayload(
            blurBackend = activeBlurBackend,
            isCrossWindowBlurSupported = isCrossWindowBlurSupported,
            scanDurationMs = lastScanDurationMs,
            discoveredCount = discoveredClassesList.size
        )

        val discoveredPayload = discoveredClassesList.map {
            DiscoveredClassPayload(
                className = it.className,
                category = it.category,
                score = it.score,
                traits = it.matchedTraits
            )
        }

        val hooksPayload = hooksMap.values.map { hook ->
            HookPayload(
                id = hook.id,
                targetSignature = hook.targetSignature,
                matchReason = hook.matchReason,
                state = hook.state.name,
                hitCount = hook.hitCount.get(),
                firstHitTime = if (hook.firstHitTimestamp > 0) SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(hook.firstHitTimestamp)) else "Never",
                isAttached = hook.isAttached,
                lastError = hook.lastError
            )
        }

        val report = DiagnosticsReportPayload(
            reportTimestamp = now,
            formattedTime = formattedTime,
            framework = frameworkPayload,
            environment = envPayload,
            targetApp = targetPayload,
            status = statusPayload,
            discoveredClasses = discoveredPayload,
            hooks = hooksPayload,
            recentErrors = recentErrorsList.toList(),
            viewTree = if (lastScannedViewTree.isNotEmpty()) lastScannedViewTree else null
        )

        return gson.toJson(report)
    }
}
