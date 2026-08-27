package com.wetype.liquid.ui

import com.google.gson.Gson
import com.wetype.liquid.discovery.DiagnosticsReportPayload

enum class RemoteStatus(val displayName: String) {
    ACTIVE("ACTIVE (Connected)"),
    STALE("STALE (Inactive > 60s)"),
    NO_REPORT("NO REPORT (Waiting for WeType)")
}

data class DiscoveredClassUiModel(
    val className: String,
    val category: String,
    val score: Int,
    val traits: List<String>
)

data class HookDetailUiModel(
    val id: String,
    val targetSignature: String,
    val matchReason: String,
    val state: String,
    val hitCount: Long,
    val firstHitTime: String,
    val isAttached: Boolean,
    val lastError: String?
)

data class RemoteDiagnosticsUiState(
    val status: RemoteStatus = RemoteStatus.NO_REPORT,
    val hasReport: Boolean = false,
    val formattedReportTime: String = "N/A",
    val reportTimestamp: Long = 0L,
    val lastSeenSecondsAgo: Long = -1L,
    val frameworkName: String = "Unknown / Inactive",
    val frameworkVersion: String = "Unknown",
    val frameworkApi: Int = 0,
    val isModernLibXposed: Boolean = false,
    val wetypeVersionName: String = "Unknown",
    val wetypeVersionCode: Long = 0L,
    val processName: String = "Unknown",
    val activeBlurBackend: String = "Unknown",
    val isCrossWindowBlurSupported: Boolean = false,
    val scanDurationMs: Long = 0L,
    val discoveredClasses: List<DiscoveredClassUiModel> = emptyList(),
    val hooks: List<HookDetailUiModel> = emptyList(),
    val recentErrors: List<String> = emptyList(),
    val viewTree: String = "",
    val rawJson: String = ""
) {
    companion object {
        private val gson: Gson = Gson()

        fun parseFromJson(json: String?, isActiveHeartbeat: Boolean): RemoteDiagnosticsUiState {
            if (json.isNullOrBlank()) {
                return RemoteDiagnosticsUiState(
                    status = RemoteStatus.NO_REPORT,
                    hasReport = false
                )
            }

            return try {
                val payload = gson.fromJson(json, DiagnosticsReportPayload::class.java)
                    ?: return RemoteDiagnosticsUiState(status = RemoteStatus.NO_REPORT, hasReport = false)

                val reportTimestamp = payload.reportTimestamp
                val now = System.currentTimeMillis()
                val secondsAgo = if (reportTimestamp > 0) (now - reportTimestamp) / 1000L else -1L

                val status = when {
                    !isActiveHeartbeat && (secondsAgo > 60 || secondsAgo < 0) -> RemoteStatus.STALE
                    else -> RemoteStatus.ACTIVE
                }

                val discoveredList = payload.discoveredClasses?.map {
                    DiscoveredClassUiModel(
                        className = it.className ?: "Unknown",
                        category = it.category ?: "Unknown",
                        score = it.score,
                        traits = it.traits ?: emptyList()
                    )
                } ?: emptyList()

                val hooksList = payload.hooks?.map {
                    HookDetailUiModel(
                        id = it.id ?: "Unknown",
                        targetSignature = it.targetSignature ?: "Unknown",
                        matchReason = it.matchReason ?: "Unknown",
                        state = it.state ?: "DISCOVERED",
                        hitCount = it.hitCount,
                        firstHitTime = it.firstHitTime ?: "Never",
                        isAttached = it.isAttached,
                        lastError = it.lastError
                    )
                } ?: emptyList()

                RemoteDiagnosticsUiState(
                    status = status,
                    hasReport = true,
                    formattedReportTime = payload.formattedTime ?: "N/A",
                    reportTimestamp = reportTimestamp,
                    lastSeenSecondsAgo = secondsAgo,
                    frameworkName = payload.framework?.name ?: "Unknown",
                    frameworkVersion = payload.framework?.version ?: "Unknown",
                    frameworkApi = payload.framework?.apiLevel ?: 0,
                    isModernLibXposed = payload.framework?.isModernLibXposed ?: false,
                    wetypeVersionName = payload.targetApp?.versionName ?: "Unknown",
                    wetypeVersionCode = payload.targetApp?.versionCode ?: 0L,
                    processName = payload.environment?.processName ?: "Unknown",
                    activeBlurBackend = payload.status?.blurBackend ?: "Unknown",
                    isCrossWindowBlurSupported = payload.status?.isCrossWindowBlurSupported ?: false,
                    scanDurationMs = payload.status?.scanDurationMs ?: 0L,
                    discoveredClasses = discoveredList,
                    hooks = hooksList,
                    recentErrors = payload.recentErrors ?: emptyList(),
                    viewTree = payload.viewTree ?: "",
                    rawJson = json
                )
            } catch (t: Throwable) {
                RemoteDiagnosticsUiState(
                    status = RemoteStatus.NO_REPORT,
                    hasReport = false,
                    rawJson = json
                )
            }
        }
    }
}
