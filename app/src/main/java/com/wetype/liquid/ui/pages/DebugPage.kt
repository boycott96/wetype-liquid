package com.wetype.liquid.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.ConfigBridge
import com.wetype.liquid.config.GlassPreset
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.ui.HookDetailUiModel
import com.wetype.liquid.ui.RemoteDiagnosticsUiState
import com.wetype.liquid.ui.RemoteStatus

@Composable
fun DebugPage(
    config: ModuleConfig,
    onConfigChange: (ModuleConfig) -> Unit
) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf(RemoteDiagnosticsUiState()) }
    val scrollState = rememberScrollState()

    fun refreshDiagnostics() {
        val (remoteJson, active) = ConfigBridge.fetchRemoteDiagnostics(context)
        uiState = RemoteDiagnosticsUiState.parseFromJson(remoteJson, active)
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status & Connection Overview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("WeType Live Diagnostics", style = MaterialTheme.typography.titleMedium)
                    }

                    IconButton(onClick = { refreshDiagnostics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                // Remote Connection Badge
                RemoteStatusBadge(uiState.status, uiState.lastSeenSecondsAgo)

                StatusRow("Target Package", "com.tencent.wetype")
                StatusRow("Target Version", "${uiState.wetypeVersionName} (${uiState.wetypeVersionCode})")
                StatusRow("Target Process", uiState.processName)
                StatusRow("Framework Mode", if (uiState.isModernLibXposed) "Modern libxposed API ${uiState.frameworkApi}" else "Legacy XposedBridge (${uiState.frameworkVersion})")
                StatusRow("Framework Name", uiState.frameworkName)
                StatusRow("Blur Backend", uiState.activeBlurBackend)
                StatusRow("Cross-Window Blur", if (uiState.isCrossWindowBlurSupported) "Supported (System HW)" else "Not Supported / Disabled")
                StatusRow("DEX Scan Stats", "${uiState.scanDurationMs} ms (${uiState.discoveredClasses.size} candidate classes)")
                StatusRow("Report Timestamp", uiState.formattedReportTime)

                HorizontalDivider()

                Text("Hook Handles & Invocations", style = MaterialTheme.typography.labelLarge)

                if (uiState.hooks.isEmpty()) {
                    Text("No hooks registered yet. Waiting for WeType process...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    uiState.hooks.forEach { hook ->
                        HookDetailCard(hook)
                    }
                }
            }
        }

        // Discovered Classes Card
        if (uiState.discoveredClasses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Discovered WeType Classes", style = MaterialTheme.typography.titleMedium)
                    uiState.discoveredClasses.forEach { cls ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cls.category, style = MaterialTheme.typography.bodyMedium)
                                Text("Score: ${cls.score}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(cls.className, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (cls.traits.isNotEmpty()) {
                                Text("Traits: ${cls.traits.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        // Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Diagnostics & Tools", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val textToCopy = if (uiState.rawJson.isNotBlank()) uiState.rawJson else "No diagnostics report available."
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("WeType Diagnostics", textToCopy))
                            Toast.makeText(context, "Diagnostics report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Report")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reset = ModuleConfig()
                            reset.applyPreset(GlassPreset.LIQUID)
                            onConfigChange(reset)
                            Toast.makeText(context, "Reset to Liquid default preset", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Default")
                    }
                }
            }
        }

        // Recent Errors Log
        if (uiState.recentErrors.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Recent Errors", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    uiState.recentErrors.forEach { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Live Diagnostic JSON Viewer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Raw Remote Diagnostics JSON", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uiState.rawJson.isNotBlank()) uiState.rawJson else "No remote data reported yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RemoteStatusBadge(status: RemoteStatus, secondsAgo: Long) {
    val (color, icon, text) = when (status) {
        RemoteStatus.ACTIVE -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle, "ACTIVE (Connected)")
        RemoteStatus.STALE -> Triple(MaterialTheme.colorScheme.error, Icons.Default.Warning, "STALE (Last seen ${secondsAgo}s ago)")
        RemoteStatus.NO_REPORT -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.HourglassEmpty, "NO REPORT (Waiting for WeType)")
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun HookDetailCard(hook: HookDetailUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(hook.id, style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (hook.state) {
                    "ATTACHED" -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("ATTACHED (${hook.hitCount} hits)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    "INSTALLED" -> {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text("INSTALLED (0 hits)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    "INSTALL_FAILED" -> {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text("FAILED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        Text(hook.state, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(hook.targetSignature, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hook.lastError != null) {
            Text("Error: ${hook.lastError}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
