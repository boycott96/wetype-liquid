package com.wetype.liquid.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.GlassPreset
import com.wetype.liquid.config.ModuleConfig

@Composable
fun AppearancePage(
    config: ModuleConfig,
    onConfigChange: (ModuleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Presets", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GlassPreset.entries.toTypedArray()) { preset ->
                        FilterChip(
                            selected = config.preset == preset,
                            onClick = {
                                val updated = config.copy()
                                updated.applyPreset(preset)
                                onConfigChange(updated)
                            },
                            label = { Text(preset.displayName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = config.preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Keyboard Surface Sliders
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.InvertColors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Keyboard Surface", style = MaterialTheme.typography.titleMedium)
                }

                // Background Alpha
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Background Opacity", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.backgroundAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.backgroundAlpha,
                        onValueChange = {
                            onConfigChange(config.copy(backgroundAlpha = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0.1f..1.0f
                    )
                }

                // Blur Radius
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blur Radius", style = MaterialTheme.typography.bodyMedium)
                        Text("${config.blurRadiusDp.toInt()} dp", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.blurRadiusDp,
                        onValueChange = {
                            onConfigChange(config.copy(blurRadiusDp = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0f..60f
                    )
                }

                // Top Corner Radius
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Top Corner Radius", style = MaterialTheme.typography.bodyMedium)
                        Text("${config.cornerRadiusTopDp.toInt()} dp", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.cornerRadiusTopDp,
                        onValueChange = {
                            onConfigChange(config.copy(cornerRadiusTopDp = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0f..36f
                    )
                }

                // Highlight Alpha
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Top Highlight Intensity", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.highlightAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.highlightAlpha,
                        onValueChange = {
                            onConfigChange(config.copy(highlightAlpha = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0f..0.5f
                    )
                }
            }
        }
    }
}
