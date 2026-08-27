package com.wetype.liquid.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wetype.liquid.config.ConfigBridge
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.ui.pages.AppearancePage
import com.wetype.liquid.ui.pages.CandidatePage
import com.wetype.liquid.ui.pages.DebugPage
import com.wetype.liquid.ui.pages.KeyboardPage
import com.wetype.liquid.ui.pages.ToolbarPage
import com.wetype.liquid.ui.theme.WeTypeLiquidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeTypeLiquidTheme {
                MainScreen()
            }
        }
    }
}

enum class NavigationTab(val title: String) {
    APPEARANCE("Appearance"),
    KEYBOARD("Keyboard"),
    CANDIDATES("Candidates"),
    TOOLBAR("Toolbar"),
    DEBUG("Debug")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ConfigBridge.getConfig(context, forceRefresh = true)) }
    var selectedTab by remember { mutableStateOf(NavigationTab.APPEARANCE) }
    var isModuleActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (_, active) = ConfigBridge.fetchRemoteDiagnostics(context)
        isModuleActive = active
    }

    fun updateConfig(newConfig: ModuleConfig) {
        config = newConfig
        ConfigBridge.saveConfig(context, newConfig)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WeType Liquid Glass", style = MaterialTheme.typography.titleLarge)
                        Text("Target: com.tencent.wetype", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.APPEARANCE,
                    onClick = { selectedTab = NavigationTab.APPEARANCE },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("Surface") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.KEYBOARD,
                    onClick = { selectedTab = NavigationTab.KEYBOARD },
                    icon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                    label = { Text("Keycaps") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.CANDIDATES,
                    onClick = { selectedTab = NavigationTab.CANDIDATES },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    label = { Text("Candidate") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.TOOLBAR,
                    onClick = { selectedTab = NavigationTab.TOOLBAR },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Toolbar") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.DEBUG,
                    onClick = { selectedTab = NavigationTab.DEBUG },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    label = { Text("Debug") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Master Switch & Dynamic Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (config.enabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (config.enabled) "Liquid Glass Active" else "Module Disabled",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isModuleActive) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Vector / Xposed Hook Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Framework Idle / Waiting for Input Method",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = {
                            updateConfig(config.copy(enabled = it))
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    NavigationTab.APPEARANCE -> AppearancePage(config = config, onConfigChange = ::updateConfig)
                    NavigationTab.KEYBOARD -> KeyboardPage(config = config, onConfigChange = ::updateConfig)
                    NavigationTab.CANDIDATES -> CandidatePage(config = config, onConfigChange = ::updateConfig)
                    NavigationTab.TOOLBAR -> ToolbarPage(config = config, onConfigChange = ::updateConfig)
                    NavigationTab.DEBUG -> DebugPage(config = config, onConfigChange = ::updateConfig)
                }
            }
        }
    }
}
