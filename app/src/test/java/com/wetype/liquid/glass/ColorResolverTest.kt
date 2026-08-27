package com.wetype.liquid.glass

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorResolverTest {

    @Test
    fun testKeyboardBackgroundColor() {
        val lightColor = ColorResolver.getKeyboardBackgroundColor(isNight = false, alphaFactor = 0.72f)
        val darkColor = ColorResolver.getKeyboardBackgroundColor(isNight = true, alphaFactor = 0.72f)

        assertNotEquals("Light and dark background colors must differ", lightColor, darkColor)
    }

    @Test
    fun testAlphaClamping() {
        // Exceeding 1.0f or below 0.0f must not crash or produce invalid colors
        val clampedHigh = ColorResolver.getKeyFillColor(isNight = false, alpha = 1.5f)
        val clampedLow = ColorResolver.getKeyFillColor(isNight = false, alpha = -0.5f)

        // Verifying alpha stays valid
        val alphaHigh = (clampedHigh ushr 24) and 0xFF
        val alphaLow = (clampedLow ushr 24) and 0xFF

        assertEquals(255, alphaHigh)
        assertEquals(0, alphaLow)
    }

    @Test
    fun testTextColorContrast() {
        val lightPrimary = ColorResolver.getPrimaryTextColor(isNight = false)
        val darkPrimary = ColorResolver.getPrimaryTextColor(isNight = true)

        assertNotEquals(lightPrimary, darkPrimary)
    }
}
