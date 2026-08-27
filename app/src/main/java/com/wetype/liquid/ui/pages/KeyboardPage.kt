package com.wetype.liquid.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.GlassPreset
import com.wetype.liquid.config.ModuleConfig

@Composable
fun KeyboardPage(
    config: ModuleConfig,
    onConfigChange: (ModuleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Keycap Glass Style
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
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Keycap Styling", style = MaterialTheme.typography.titleMedium)
                }

                // Key Radius
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keycap Corner Radius", style = MaterialTheme.typography.bodyMedium)
                        Text("${config.keyRadiusDp.toInt()} dp", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.keyRadiusDp,
                        onValueChange = {
                            onConfigChange(config.copy(keyRadiusDp = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 4f..24f
                    )
                }

                // Key Fill Alpha Light
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Key Fill Alpha (Light Mode)", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.keyFillAlphaLight * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.keyFillAlphaLight,
                        onValueChange = {
                            onConfigChange(config.copy(keyFillAlphaLight = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0.05f..0.8f
                    )
                }

                // Key Fill Alpha Dark
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Key Fill Alpha (Dark Mode)", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.keyFillAlphaDark * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.keyFillAlphaDark,
                        onValueChange = {
                            onConfigChange(config.copy(keyFillAlphaDark = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0.05f..0.8f
                    )
                }

                // Key Border Width
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Key Border Width", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format(java.util.Locale.getDefault(), "%.2f dp", config.keyBorderWidthDp), style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.keyBorderWidthDp,
                        onValueChange = {
                            onConfigChange(config.copy(keyBorderWidthDp = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0.2f..2.0f
                    )
                }

                // Key Border Alpha
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Key Border Opacity", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.keyBorderAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = config.keyBorderAlpha,
                        onValueChange = {
                            onConfigChange(config.copy(keyBorderAlpha = it, preset = GlassPreset.CUSTOM))
                        },
                        valueRange = 0.02f..0.5f
                    )
                }
            }
        }

        // Press Feedback Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Liquid Press Animation", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = config.pressAnimationEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(pressAnimationEnabled = it))
                        }
                    )
                }

                if (config.pressAnimationEnabled) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Press Scale Feedback", style = MaterialTheme.typography.bodyMedium)
                            Text(String.format(java.util.Locale.getDefault(), "%.2f", config.pressScale), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = config.pressScale,
                            onValueChange = {
                                onConfigChange(config.copy(pressScale = it))
                            },
                            valueRange = 0.90f..1.00f
                        )
                    }
                }
            }
        }
    }
}
