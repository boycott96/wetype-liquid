package com.wetype.liquid.glass

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.discovery.ClassScorer
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.MethodFinder
import com.wetype.liquid.discovery.SafeHook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

data class KeycapState(
    val keyId: Any,
    val bounds: RectF,
    val keyType: KeyType,
    var isPressed: Boolean = false,
    val pressRenderer: PressEffectRenderer = PressEffectRenderer()
)

object KeycapRenderer {
    private val viewKeycapsMap = WeakHashMap<View, MutableMap<Any, KeycapState>>()
    private val cachedKeyCollectionFields = ConcurrentHashMap<Class<*>, Field?>()
    private val cachedLabelMethods = ConcurrentHashMap<Class<*>, Method>()
    private val cachedKeyDataMethods = ConcurrentHashMap<Class<*>, Method>()
    private val keyTypeCache = WeakHashMap<Any, KeyType>()

    var isPerKeyExtractionActive: Boolean = false
        private set

    /**
     * Attempts to extract individual key models and their bounding rectangles from the KeyboardView.
     */
    fun extractKeycapsFromView(view: View): List<KeycapState> {
        val clazz = view.javaClass
        val keyList = mutableListOf<KeycapState>()

        val field = cachedKeyCollectionFields.computeIfAbsent(clazz) {
            findKeyCollectionField(clazz)
        } ?: return emptyList()

        try {
            val collectionObj = field.get(view) ?: return emptyList()
            val rawKeys = when (collectionObj) {
                is Array<*> -> collectionObj.filterNotNull()
                is Collection<*> -> collectionObj.filterNotNull()
                else -> emptyList()
            }

            for (keyObj in rawKeys) {
                val bounds = extractBoundsFromKeyObject(keyObj) ?: continue
                if (bounds.isEmpty) continue

                val keyType = resolveKeyType(bounds, keyObj)
                keyList.add(
                    KeycapState(
                        keyId = keyObj,
                        bounds = bounds,
                        keyType = keyType
                    )
                )
            }
        } catch (t: Throwable) {
            SafeHook.log(SafeHook.LogLevel.WARN, message = "Error extracting keycaps from ${clazz.name}: ${t.message}")
        }

        isPerKeyExtractionActive = keyList.isNotEmpty()
        return keyList
    }

    private fun findKeyCollectionField(clazz: Class<*>): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (f in current.declaredFields) {
                f.isAccessible = true
                val fType = f.type
                val comp = fType.componentType
                if (fType.isArray && comp != null && ClassScorer.isImeButtonDataClass(comp)) {
                    return f
                }
                if (Collection::class.java.isAssignableFrom(fType)) {
                    // Check field name heuristics
                    val name = f.name.lowercase()
                    if (name.contains("key") || name.contains("button") || name.contains("item")) {
                        return f
                    }
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun extractBoundsFromKeyObject(keyObj: Any): RectF? {
        val kClass = keyObj.javaClass

        // 1. Check Rect / RectF fields
        for (f in kClass.declaredFields) {
            f.isAccessible = true
            val type = f.type
            if (RectF::class.java.isAssignableFrom(type)) {
                val rf = f.get(keyObj) as? RectF
                if (rf != null && !rf.isEmpty) return RectF(rf)
            }
            if (Rect::class.java.isAssignableFrom(type)) {
                val r = f.get(keyObj) as? Rect
                if (r != null && !r.isEmpty) return RectF(r)
            }
        }

        // 2. Check coordinate getter methods
        try {
            val getBoundsMethod = MethodFinder.findMethodExact(kClass, "getBounds")
                ?: MethodFinder.findMethodExact(kClass, "getRect")
            if (getBoundsMethod != null) {
                val res = getBoundsMethod.invoke(keyObj)
                if (res is RectF && !res.isEmpty) return RectF(res)
                if (res is Rect && !res.isEmpty) return RectF(res)
            }
        } catch (ignored: Throwable) {
        }

        return null
    }

    fun renderAllKeys(
        canvas: Canvas,
        view: View,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float
    ): Boolean {
        var keyMap = viewKeycapsMap[view]
        if (keyMap == null) {
            val extracted = extractKeycapsFromView(view)
            if (extracted.isEmpty()) {
                isPerKeyExtractionActive = false
                return false
            }
            keyMap = mutableMapOf()
            for (k in extracted) {
                keyMap[k.keyId] = k
            }
            viewKeycapsMap[view] = keyMap
        }

        isPerKeyExtractionActive = true

        // Draw each individual key on its distinct bounds
        for (keyState in keyMap.values) {
            GlassRenderer.renderKeyDirect(
                canvas = canvas,
                bounds = keyState.bounds,
                config = config,
                isNight = isNight,
                density = density,
                isPressed = keyState.isPressed,
                keyType = keyState.keyType
            )
        }
        return true
    }

    fun handleTouchEvent(
        view: View,
        event: MotionEvent,
        config: ModuleConfig
    ): Boolean {
        val keyMap = viewKeycapsMap[view] ?: return false
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var touchedKey: KeycapState? = null
                for (keyState in keyMap.values) {
                    if (keyState.bounds.contains(x, y)) {
                        touchedKey = keyState
                        break
                    }
                }

                var changed = false
                for (keyState in keyMap.values) {
                    val shouldPress = (keyState == touchedKey)
                    if (keyState.isPressed != shouldPress) {
                        keyState.isPressed = shouldPress
                        keyState.pressRenderer.setPressed(
                            pressed = shouldPress,
                            animate = true,
                            durationMs = config.pressDurationMs
                        ) {
                            view.invalidate()
                        }
                        changed = true
                    }
                }
                return changed
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                var changed = false
                for (keyState in keyMap.values) {
                    if (keyState.isPressed) {
                        keyState.isPressed = false
                        keyState.pressRenderer.setPressed(
                            pressed = false,
                            animate = true,
                            durationMs = config.pressDurationMs
                        ) {
                            view.invalidate()
                        }
                        changed = true
                    }
                }
                return changed
            }
        }
        return false
    }

    fun containsPoint(bounds: RectF, x: Float, y: Float): Boolean {
        return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
    }

    fun resolveKeyType(width: Float, height: Float): KeyType {
        return resolveKeyType(width, height, null)
    }

    fun resolveKeyType(width: Float, height: Float, label: String?): KeyType {
        if (width <= 0 || height <= 0) return KeyType.NORMAL

        val normalized = label?.trim()?.lowercase().orEmpty()
        if (normalized.contains("空格") || normalized == "space") {
            return KeyType.SPACE
        }
        if (
            normalized.contains("换行") || normalized.contains("回车") ||
            normalized.contains("完成") || normalized.contains("发送") ||
            normalized.contains("搜索") || normalized.contains("下一步") ||
            normalized == "enter" || normalized == "return" || normalized == "go" ||
            normalized == "done" || normalized == "send" || normalized == "search"
        ) {
            return KeyType.ACTION
        }
        if (
            normalized.contains("重输") || normalized.contains("删除") ||
            normalized.contains("退格") || normalized.contains("符号") ||
            normalized == "123" || normalized == "#+=" || normalized == "shift" ||
            normalized == "delete" || normalized == "backspace"
        ) {
            return KeyType.FUNCTIONAL
        }

        // T9 letter groups such as ABC/DEF are wider than they are tall but
        // still use the ordinary white key material.
        if (normalized.isNotEmpty()) {
            return KeyType.NORMAL
        }

        if (width > height * 2.2f) {
            return KeyType.SPACE
        }
        if (width > height * 1.3f) {
            return KeyType.FUNCTIONAL
        }
        return KeyType.NORMAL
    }

    fun resolveKeyType(bounds: RectF, keyObj: Any? = null): KeyType {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        if (keyObj == null) return resolveKeyType(width, height)
        return keyTypeCache[keyObj] ?: resolveKeyType(width, height, extractKeyLabel(keyObj)).also {
            keyTypeCache[keyObj] = it
        }
    }

    private fun extractKeyLabel(keyObj: Any?): String? {
        if (keyObj == null) return null
        val clazz = keyObj.javaClass

        val direct = cachedLabelMethods[clazz] ?: listOf("R", "getMainText").firstNotNullOfOrNull { name ->
            MethodFinder.findMethodExact(clazz, name)?.takeIf { it.returnType == String::class.java }
        }?.also { cachedLabelMethods[clazz] = it }
        try {
            (direct?.invoke(keyObj) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Throwable) {
        }

        val keyDataGetter = cachedKeyDataMethods[clazz]
            ?: MethodFinder.findMethodExact(clazz, "O")?.also { cachedKeyDataMethods[clazz] = it }
        try {
            val keyData = keyDataGetter?.invoke(keyObj) ?: return null
            val getter = MethodFinder.findMethodExact(keyData.javaClass, "getMainText") ?: return null
            return getter.invoke(keyData) as? String
        } catch (_: Throwable) {
            return null
        }
    }
}
