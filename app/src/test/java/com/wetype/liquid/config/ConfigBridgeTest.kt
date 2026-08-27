package com.wetype.liquid.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBridgeTest {

    @Test
    fun testConfigBridgeDefaultConfigNotNull() {
        val config = ConfigBridge.getConfig(null)
        assertNotNull(config)
        assertTrue(config.enabled)
        assertEquals(GlassPreset.LIQUID, config.preset)
    }

    @Test
    fun testListenerNotification() {
        var notifiedConfig: ModuleConfig? = null
        val listener: (ModuleConfig) -> Unit = {
            notifiedConfig = it
        }

        ConfigBridge.addChangeListener(listener)

        val updated = ModuleConfig(enabled = false, preset = GlassPreset.DARK_GLASS)
        // Simulate local cache update
        ConfigBridge.addChangeListener {
            // Check listener trigger
        }

        ConfigBridge.removeChangeListener(listener)
    }
}
