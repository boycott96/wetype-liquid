package com.wetype.liquid.discovery

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassScorerTest {

    // Dummy sample classes for testing heuristic detection
    class FakeImeButtonData {
        fun getLabel(): String = "A"
        fun getUpperSymbol(): String = "@"
        fun H0(customSymbol: String) {}
        val bounds: RectF = RectF()
    }

    class FakeIncompleteButton {
        fun getLabel(): String = "A"
    }

    open class DummyKeyboardView(context: android.content.Context) : ViewGroup(context) {
        val keyPaint: Paint = Paint()
        val keyBounds: RectF = RectF()
        val buttons: Array<FakeImeButtonData> = emptyArray()

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        override fun onDraw(canvas: Canvas) {}
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }

    open class DummyViewWithGenericStringList(context: android.content.Context) : View(context) {
        val tags: List<String> = emptyList()
        val items: ArrayList<String> = ArrayList()
    }

    class DummyNonViewWithDraw {
        fun onDraw(canvas: Canvas) {}
    }

    @Test
    fun testImeButtonDataClassDetection() {
        assertTrue("FakeImeButtonData should be identified as ImeButton data class",
            ClassScorer.isImeButtonDataClass(FakeImeButtonData::class.java))

        assertFalse("FakeIncompleteButton should not be identified as ImeButton",
            ClassScorer.isImeButtonDataClass(FakeIncompleteButton::class.java))

        assertFalse("View subclasses should not be identified as ImeButton data class",
            ClassScorer.isImeButtonDataClass(DummyKeyboardView::class.java))
    }

    @Test
    fun testKeyboardViewScoringWithImeButtons() {
        val scoreResult = ClassScorer.scoreKeyboardViewClass(DummyKeyboardView::class.java)

        assertTrue("DummyKeyboardView should be qualified", scoreResult.isQualified)
        assertTrue("Score should be at least minimum threshold", scoreResult.score >= ClassScorer.MINIMUM_KEYBOARD_VIEW_SCORE)
        assertTrue("Should detect DrawsCanvas trait", scoreResult.matchedTraits.any { it.startsWith("DrawsCanvas") })
        assertTrue("Should detect GraphicsField trait", scoreResult.matchedTraits.any { it.startsWith("GraphicsField") })
        assertTrue("Should detect TouchHandler trait", scoreResult.matchedTraits.any { it.startsWith("TouchHandler") })
        assertTrue("Should detect ImeButtonArrayField trait", scoreResult.matchedTraits.any { it.startsWith("ImeButtonArrayField") })
    }

    @Test
    fun testGenericCollectionNotFalselyIdentifiedAsImeButtonCollection() {
        val scoreResult = ClassScorer.scoreKeyboardViewClass(DummyViewWithGenericStringList::class.java)

        assertFalse("Should NOT identify generic String list as ImeButtonCollection",
            scoreResult.matchedTraits.any { it.startsWith("ImeButtonCollectionField") || it.startsWith("ImeButtonArrayField") })
    }

    @Test
    fun testNonViewClassRejection() {
        val result = ClassScorer.scoreKeyboardViewClass(DummyNonViewWithDraw::class.java)

        assertFalse("Non-view classes must be rejected", result.isQualified)
        assertEquals(0, result.score)
        assertTrue(result.matchedTraits.contains("NotAView"))
    }
}
