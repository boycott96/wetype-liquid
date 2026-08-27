package com.wetype.liquid.glass

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeycapRendererTest {

    @Test
    fun testKeyTypeClassification() {
        val spaceType = KeycapRenderer.resolveKeyType(300f, 100f)
        assertEquals("300x100 must be SPACE key", KeyType.SPACE, spaceType)

        val fnType = KeycapRenderer.resolveKeyType(150f, 100f)
        assertEquals("150x100 must be FUNCTIONAL key", KeyType.FUNCTIONAL, fnType)

        val normalType = KeycapRenderer.resolveKeyType(100f, 100f)
        assertEquals("100x100 must be NORMAL key", KeyType.NORMAL, normalType)

        assertEquals(KeyType.ACTION, KeycapRenderer.resolveKeyType(100f, 100f, "换行"))
        assertEquals(KeyType.ACTION, KeycapRenderer.resolveKeyType(100f, 100f, "发送"))
        assertEquals(KeyType.ACTION, KeycapRenderer.resolveKeyType(100f, 100f, "前往"))
        assertEquals(KeyType.FUNCTIONAL, KeycapRenderer.resolveKeyType(100f, 100f, "重输"))
        assertEquals(KeyType.SPACE, KeycapRenderer.resolveKeyType(100f, 100f, "空格"))
        assertEquals(KeyType.NORMAL, KeycapRenderer.resolveKeyType(170f, 100f, "ABC"))
    }

    @Test
    fun testTouchHitDetection() {
        val bounds = RectF(10f, 20f, 60f, 70f)
        bounds.left = 10f
        bounds.top = 20f
        bounds.right = 60f
        bounds.bottom = 70f

        // Points inside
        assertTrue(KeycapRenderer.containsPoint(bounds, 15f, 25f))
        assertTrue(KeycapRenderer.containsPoint(bounds, 55f, 65f))

        // Points outside
        assertFalse(KeycapRenderer.containsPoint(bounds, 5f, 25f))
        assertFalse(KeycapRenderer.containsPoint(bounds, 65f, 25f))
        assertFalse(KeycapRenderer.containsPoint(bounds, 15f, 75f))
    }
}
