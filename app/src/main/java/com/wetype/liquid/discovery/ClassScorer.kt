package com.wetype.liquid.discovery

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

data class ClassScoreResult(
    val clazz: Class<*>,
    val score: Int,
    val matchedTraits: List<String>,
    val isQualified: Boolean
)

object ClassScorer {
    const val MINIMUM_KEYBOARD_VIEW_SCORE = 50

    /**
     * Identifies whether a class is an ImeButton data model (e.g., com.tencent.wetype.plugin.hld.keyboard.selfdraw.j).
     * Characteristics:
     * - NOT a View
     * - Has >= 2 methods returning String without arguments (e.g. getLabel(), getUpperSymbol())
     * - Has >= 1 method taking (String) with void return (e.g. H0(String))
     */
    fun isImeButtonDataClass(clazz: Class<*>): Boolean {
        if (View::class.java.isAssignableFrom(clazz)) {
            return false
        }

        var stringNoArgReturnCount = 0
        var stringConsumerCount = 0

        try {
            for (m in clazz.declaredMethods) {
                if (Modifier.isStatic(m.modifiers)) continue
                if (m.returnType == String::class.java && m.parameterTypes.isEmpty()) {
                    stringNoArgReturnCount++
                }
                if (m.returnType == Void.TYPE && m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java) {
                    stringConsumerCount++
                }
            }
        } catch (t: Throwable) {
            return false
        }

        return stringNoArgReturnCount >= 2 && stringConsumerCount >= 1
    }

    /**
     * Scores a candidate class for Keyboard Canvas rendering.
     */
    fun scoreKeyboardViewClass(clazz: Class<*>): ClassScoreResult {
        var score = 0
        val traits = mutableListOf<String>()

        // 1. Is View / ViewGroup subclass (+30)
        val isView = View::class.java.isAssignableFrom(clazz)
        val isViewGroup = ViewGroup::class.java.isAssignableFrom(clazz)
        if (isView) {
            score += 30
            traits.add(if (isViewGroup) "ViewGroupSubclass" else "ViewSubclass")
        } else {
            return ClassScoreResult(clazz, 0, listOf("NotAView"), false)
        }

        // 2. Canvas drawing methods (+25)
        var hasDrawCanvas = false
        try {
            for (m in clazz.declaredMethods) {
                if (m.name in listOf("onDraw", "draw", "dispatchDraw")) {
                    if (m.parameterTypes.size == 1 && m.parameterTypes[0] == Canvas::class.java) {
                        hasDrawCanvas = true
                        traits.add("DrawsCanvas:${m.name}")
                        break
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        if (hasDrawCanvas) {
            score += 25
        }

        // 3. Paint / Drawable / Rect / RectF fields (+15)
        var hasGraphicsFields = false
        try {
            for (f in clazz.declaredFields) {
                val type = f.type
                if (Paint::class.java.isAssignableFrom(type) ||
                    Drawable::class.java.isAssignableFrom(type) ||
                    Rect::class.java.isAssignableFrom(type) ||
                    RectF::class.java.isAssignableFrom(type)
                ) {
                    hasGraphicsFields = true
                    traits.add("GraphicsField:${type.simpleName}")
                    break
                }
            }
        } catch (ignored: Throwable) {
        }
        if (hasGraphicsFields) {
            score += 15
        }

        // 4. Touch Event handling (+10)
        var hasTouch = false
        try {
            for (m in clazz.declaredMethods) {
                if (m.name in listOf("onTouchEvent", "dispatchTouchEvent")) {
                    if (m.parameterTypes.size == 1 && m.parameterTypes[0] == MotionEvent::class.java) {
                        hasTouch = true
                        traits.add("TouchHandler:${m.name}")
                        break
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        if (hasTouch) {
            score += 10
        }

        // 5. Button/Key data structure collection (+15)
        var hasKeyCollection = false
        try {
            for (f in clazz.declaredFields) {
                val fType = f.type
                val comp = fType.componentType
                if (fType.isArray && comp != null && isImeButtonDataClass(comp)) {
                    hasKeyCollection = true
                    traits.add("ImeButtonArrayField:${f.name}")
                    break
                }
                if (Collection::class.java.isAssignableFrom(fType)) {
                    val genericType = f.genericType
                    if (genericType is ParameterizedType) {
                        val typeArgs = genericType.actualTypeArguments
                        if (typeArgs.isNotEmpty() && typeArgs[0] is Class<*>) {
                            val elemClass = typeArgs[0] as Class<*>
                            if (isImeButtonDataClass(elemClass)) {
                                hasKeyCollection = true
                                traits.add("ImeButtonCollectionField:${f.name}")
                                break
                            }
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        if (hasKeyCollection) {
            score += 15
        }

        // 6. Target package prefix match (+10)
        if (clazz.name.startsWith("com.tencent.wetype.plugin.hld.keyboard")) {
            score += 10
            traits.add("PackagePrefixMatch")
        }

        val isQualified = score >= MINIMUM_KEYBOARD_VIEW_SCORE && hasDrawCanvas
        return ClassScoreResult(clazz, score, traits, isQualified)
    }

    /**
     * Validates candidate view / toolbar view known classes.
     */
    fun validateCandidateViewClass(clazz: Class<*>): Boolean {
        if (!View::class.java.isAssignableFrom(clazz)) return false
        val hasDrawOrLayout = clazz.declaredMethods.any { m ->
            m.name in listOf("onDraw", "dispatchDraw", "onLayout", "onMeasure")
        }
        return hasDrawOrLayout
    }

    fun validateToolbarViewClass(clazz: Class<*>): Boolean {
        if (!View::class.java.isAssignableFrom(clazz)) return false
        return true
    }
}
