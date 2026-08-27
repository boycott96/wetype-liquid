package com.wetype.liquid.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.ModuleConfig

@Composable
fun CandidatePage(
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
                        Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Candidate Bar Integration", style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = config.candidateGlassEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(candidateGlassEnabled = it))
                        }
                    )
                }

                Text(
                    text = "Seamlessly blends the candidate strip into the keyboard glass surface without thick opaque cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (config.candidateGlassEnabled) {
                    // Candidate Selected Highlight Alpha
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Selected Highlight Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(config.candidateHighlightAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = config.candidateHighlightAlpha,
                            onValueChange = {
                                onConfigChange(config.copy(candidateHighlightAlpha = it))
                            },
                            valueRange = 0.05f..0.4f
                        )
                    }

                    // Candidate Divider Alpha
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtle Divider Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(config.candidateDividerAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = config.candidateDividerAlpha,
                            onValueChange = {
                                onConfigChange(config.copy(candidateDividerAlpha = it))
                            },
                            valueRange = 0.0f..0.25f
                        )
                    }
                }
            }
        }
    }
}
