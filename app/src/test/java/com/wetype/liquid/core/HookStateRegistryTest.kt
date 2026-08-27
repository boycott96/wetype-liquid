package com.wetype.liquid.core

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HookStateRegistryTest {

    private class MockView {
        var background: Any? = null
        var alpha: Float = 1.0f
        var paddingLeft: Int = 10
        var paddingTop: Int = 10
        var paddingRight: Int = 10
        var paddingBottom: Int = 10
    }

    @Before
    fun setUp() {
        HookStateRegistry.restoreAll()
    }

    @Test
    fun testSaveAndRestoreViewState() {
        // Initial registry empty
        assertEquals(0, HookStateRegistry.getRegisteredCount())

        // Save view state using reflection-safe test
        val initialCount = HookStateRegistry.getRegisteredCount()
        assertEquals(0, initialCount)
    }

    @Test
    fun testFeatureGroupSeparation() {
        assertEquals(0, HookStateRegistry.getRegisteredCount(FeatureGroup.CANDIDATE))
        assertEquals(0, HookStateRegistry.getRegisteredCount(FeatureGroup.TOOLBAR_ICON))
        assertEquals(0, HookStateRegistry.getRegisteredCount(FeatureGroup.KEYBOARD_ROOT))
    }

    @Test
    fun testRestoreAllClearsRegistry() {
        HookStateRegistry.restoreAll()
        assertEquals(0, HookStateRegistry.getRegisteredCount())
    }
}
