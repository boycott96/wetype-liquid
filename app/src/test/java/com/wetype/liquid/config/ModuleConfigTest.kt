package com.wetype.liquid.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleConfigTest {

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = ModuleConfig(
            enabled = true,
            preset = GlassPreset.CLEAR,
            backgroundAlpha = 0.55f,
            blurRadiusDp = 35f,
            keyRadiusDp = 14f,
            pressAnimationEnabled = true
        )

        val json = original.toJson()
        assertNotNull(json)
        assertTrue(json.contains("\"preset\":\"CLEAR\""))

        val deserialized = ModuleConfig.fromJson(json)
        assertEquals(original.enabled, deserialized.enabled)
        assertEquals(original.preset, deserialized.preset)
        assertEquals(original.backgroundAlpha, deserialized.backgroundAlpha, 0.001f)
        assertEquals(original.blurRadiusDp, deserialized.blurRadiusDp, 0.001f)
        assertEquals(original.keyRadiusDp, deserialized.keyRadiusDp, 0.001f)
        assertEquals(original.pressAnimationEnabled, deserialized.pressAnimationEnabled)
    }

    @Test
    fun testPresetApplication() {
        val config = ModuleConfig()

        config.applyPreset(GlassPreset.CLEAR)
        assertEquals(GlassPreset.CLEAR, config.preset)
        assertEquals(0.55f, config.backgroundAlpha, 0.01f)
        assertEquals(35f, config.blurRadiusDp, 0.01f)

        config.applyPreset(GlassPreset.MINIMAL)
        assertEquals(GlassPreset.MINIMAL, config.preset)
        assertEquals(0.65f, config.backgroundAlpha, 0.01f)
        assertEquals(20f, config.blurRadiusDp, 0.01f)

        config.applyPreset(GlassPreset.DARK_GLASS)
        assertEquals(GlassPreset.DARK_GLASS, config.preset)
        assertEquals(0.75f, config.backgroundAlpha, 0.01f)
    }

    @Test
    fun testNullAndInvalidJsonFallback() {
        val emptyConfig = ModuleConfig.fromJson("")
        assertNotNull(emptyConfig)
        assertTrue(emptyConfig.enabled)

        val corruptedConfig = ModuleConfig.fromJson("{corrupted_json:}")
        assertNotNull(corruptedConfig)
        assertTrue(corruptedConfig.enabled)
    }
}
