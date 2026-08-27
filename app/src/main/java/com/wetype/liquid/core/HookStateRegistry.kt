package com.wetype.liquid.core

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.wetype.liquid.discovery.MethodFinder
import java.lang.ref.WeakReference
import java.util.WeakHashMap

enum class FeatureGroup {
    KEYBOARD_ROOT,
    CANDIDATE,
    CANDIDATE_PARENT,
    TOOLBAR_ROOT,
    TOOLBAR_ICON,
    KEY_VIEW,
    WINDOW
}

data class OriginalViewState(
    val background: Drawable?,
    val alpha: Float,
    val renderEffect: Any? = null,
    val padding: Rect = Rect(),
    val featureGroup: FeatureGroup
)

data class OriginalWindowState(
    val background: Drawable?,
    val flags: Int,
    val blurBehindRadius: Int,
    val backgroundBlurRadius: Int
)

object HookStateRegistry {
    private val viewStateMap = WeakHashMap<View, OriginalViewState>()
    private var windowStateMap = WeakHashMap<Window, OriginalWindowState>()

    @Synchronized
    fun saveViewState(view: View, group: FeatureGroup) {
        if (!viewStateMap.containsKey(view)) {
            val renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val getRenderEffectMethod = MethodFinder.findMethodExact(View::class.java, "getRenderEffect")
                    getRenderEffectMethod?.invoke(view)
                } catch (t: Throwable) {
                    null
                }
            } else null

            val padding = Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            viewStateMap[view] = OriginalViewState(
                background = view.background,
                alpha = view.alpha,
                renderEffect = renderEffect,
                padding = padding,
                featureGroup = group
            )
        }
    }

    @Synchronized
    fun saveWindowState(window: Window) {
        if (!windowStateMap.containsKey(window)) {
            val lp = window.attributes
            val flags = lp.flags
            val blurBehind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lp.blurBehindRadius
            } else 0

            val bgBlur = 0 // In standard Android, backgroundBlurRadius is tracked internally

            windowStateMap[window] = OriginalWindowState(
                background = null, // Window background drawable cannot always be safely inspected
                flags = flags,
                blurBehindRadius = blurBehind,
                backgroundBlurRadius = bgBlur
            )
        }
    }

    @Synchronized
    fun restoreViewState(view: View): Boolean {
        val state = viewStateMap.remove(view) ?: return false
        view.background = state.background
        view.alpha = state.alpha
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(state.renderEffect as? android.graphics.RenderEffect)
        }
        view.setPadding(state.padding.left, state.padding.top, state.padding.right, state.padding.bottom)
        return true
    }

    @Synchronized
    fun restoreWindowState(window: Window): Boolean {
        val state = windowStateMap.remove(window) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val lp = window.attributes
            lp.flags = state.flags
            lp.blurBehindRadius = state.blurBehindRadius
            window.attributes = lp
        }
        return true
    }

    @Synchronized
    fun restoreGroup(group: FeatureGroup): Int {
        var count = 0
        val iterator = viewStateMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.key
            val state = entry.value
            if (state.featureGroup == group) {
                if (view != null) {
                    view.background = state.background
                    view.alpha = state.alpha
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        view.setRenderEffect(state.renderEffect as? android.graphics.RenderEffect)
                    }
                    view.setPadding(state.padding.left, state.padding.top, state.padding.right, state.padding.bottom)
                }
                iterator.remove()
                count++
            }
        }
        return count
    }

    @Synchronized
    fun restoreAll() {
        // 1. Restore all Views
        val iterator = viewStateMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.key
            val state = entry.value
            if (view != null) {
                view.background = state.background
                view.alpha = state.alpha
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.setRenderEffect(state.renderEffect as? android.graphics.RenderEffect)
                }
                view.setPadding(state.padding.left, state.padding.top, state.padding.right, state.padding.bottom)
            }
            iterator.remove()
        }

        // 2. Restore all Windows
        val winIterator = windowStateMap.entries.iterator()
        while (winIterator.hasNext()) {
            val entry = winIterator.next()
            val window = entry.key
            val state = entry.value
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val lp = window.attributes
                lp.flags = state.flags
                lp.blurBehindRadius = state.blurBehindRadius
                window.attributes = lp
            }
            winIterator.remove()
        }
    }

    @Synchronized
    fun hasSavedState(view: View): Boolean = viewStateMap.containsKey(view)

    @Synchronized
    fun getRegisteredCount(group: FeatureGroup? = null): Int {
        if (group == null) return viewStateMap.size
        return viewStateMap.values.count { it.featureGroup == group }
    }
}
