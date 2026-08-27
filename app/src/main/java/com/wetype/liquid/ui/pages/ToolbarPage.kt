package com.wetype.liquid.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.ModuleConfig

@Composable
fun ToolbarPage(
    config: ModuleConfig,
    onConfigChange: (ModuleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Toolbar Modernization", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = config.toolbarGlassEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(toolbarGlassEnabled = it))
                        }
                    )
                }

                Text(
                    text = "Removes opaque toolbar cards and gives icons a clean, uniform 70% opacity with dynamic liquid press ripples.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (config.toolbarGlassEnabled) {
                    // Icon Alpha Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Icon Default Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(config.toolbarIconAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = config.toolbarIconAlpha,
                            onValueChange = {
                                onConfigChange(config.copy(toolbarIconAlpha = it))
                            },
                            valueRange = 0.3f..1.0f
                        )
                    }
                }
            }
        }
    }
}
