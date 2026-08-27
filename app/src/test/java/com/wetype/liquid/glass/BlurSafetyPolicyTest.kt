package com.wetype.liquid.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurSafetyPolicyTest {

    @Test
    fun rejectsNearFullscreenImeWindow() {
        assertFalse(BlurSafetyPolicy.canUseWindowBackgroundBlur(2520, 2640))
    }

    @Test
    fun allowsKeyboardRegionWindow() {
        assertTrue(BlurSafetyPolicy.canUseWindowBackgroundBlur(900, 2640))
    }

    @Test
    fun rejectsUnknownDimensions() {
        assertFalse(BlurSafetyPolicy.canUseWindowBackgroundBlur(0, 2640))
        assertFalse(BlurSafetyPolicy.canUseWindowBackgroundBlur(900, 0))
    }

    @Test
    fun regionalTintIsReadableButTranslucent() {
        assertEquals(0.4464f, BlurSafetyPolicy.regionalTintAlpha(0.72f), 0.001f)
        assertEquals(0.24f, BlurSafetyPolicy.regionalTintAlpha(0f), 0.001f)
        assertEquals(0.50f, BlurSafetyPolicy.regionalTintAlpha(1f), 0.001f)
    }
}
