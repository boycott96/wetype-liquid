package com.wetype.liquid.glass

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.SafeHook

object BlurController {

    enum class BlurBackend(val displayName: String) {
        REGIONAL_SURFACE_BLUR("Regional Surface Blur (Keyboard Only)"),
        WINDOW_BACKGROUND_BLUR("Window Background Blur (Keyboard Region)"),
        VIEW_RENDER_EFFECT("View Surface RenderEffect"),
        SURFACE_FALLBACK("Translucent Glass Surface Fallback"),
        DISABLED("Disabled")
    }

    private var activeBackend: BlurBackend = BlurBackend.SURFACE_FALLBACK

    fun getActiveBackend(): BlurBackend = activeBackend

    fun isCrossWindowBlurSupported(context: Context?): Boolean {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.isCrossWindowBlurEnabled ?: false
        } catch (t: Throwable) {
            false
        }
    }

    fun applyRegionalSurfaceBlur(
        root: View?,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float,
        onStateChanged: (Boolean) -> Unit
    ): Boolean {
        val context = root?.context
        val supported = isCrossWindowBlurSupported(context)
        HookDiagnostics.isCrossWindowBlurSupported = supported
        if (!supported) {
            RegionalSurfaceBlurController.detach()
            activeBackend = BlurBackend.SURFACE_FALLBACK
            HookDiagnostics.activeBlurBackend = activeBackend.displayName
            onStateChanged(false)
            return false
        }

        val attached = RegionalSurfaceBlurController.attachOrUpdate(
            root = root,
            config = config,
            isNight = isNight,
            density = density
        ) { active, error ->
            activeBackend = if (active) {
                BlurBackend.REGIONAL_SURFACE_BLUR
            } else {
                BlurBackend.SURFACE_FALLBACK
            }
            HookDiagnostics.activeBlurBackend = activeBackend.displayName
            if (error != null) {
                SafeHook.log(SafeHook.LogLevel.WARN, message = "Regional Surface blur failed: $error")
            } else if (active) {
                SafeHook.log(SafeHook.LogLevel.INFO, message = "Regional Surface blur is active")
            }
            onStateChanged(active)
        }

        if (!attached) {
            activeBackend = BlurBackend.SURFACE_FALLBACK
            HookDiagnostics.activeBlurBackend = activeBackend.displayName
        }
        return attached
    }

    fun applyWindowBlur(window: Window?, blurRadiusDp: Float, density: Float, context: Context?): BlurBackend {
        if (window == null) {
            activeBackend = BlurBackend.SURFACE_FALLBACK
            HookDiagnostics.activeBlurBackend = activeBackend.displayName
            return activeBackend
        }

        val blurRadiusPx = (blurRadiusDp * density).toInt().coerceAtLeast(0)

        // Never use FLAG_BLUR_BEHIND for an IME. WeType's transparent input-method
        // window is almost full-screen on some ROMs (including OriginOS), so that
        // flag blurs the editor and all typed content behind the keyboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            clearUnsafeWindowBlur(window)

            // A child Surface bounded to the keyboard is always preferred. Do
            // not also blur the containing Window, which may be full-screen.
            if (RegionalSurfaceBlurController.isAttached()) {
                activeBackend = if (RegionalSurfaceBlurController.isActive()) {
                    BlurBackend.REGIONAL_SURFACE_BLUR
                } else {
                    BlurBackend.SURFACE_FALLBACK
                }
                HookDiagnostics.activeBlurBackend = activeBackend.displayName
                return activeBackend
            }

            val ctx = context ?: window.context
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val displayHeightPx = ctx.resources.displayMetrics.heightPixels
            val imeWindowHeightPx = window.decorView.height
            val canUseRegionalBlur = BlurSafetyPolicy.canUseWindowBackgroundBlur(
                windowHeightPx = imeWindowHeightPx,
                displayHeightPx = displayHeightPx
            )
            val isCrossBlurEnabled = wm?.isCrossWindowBlurEnabled == true

            if (isCrossBlurEnabled && canUseRegionalBlur && blurRadiusPx > 0) {
                val success = SafeHook.runSafe("ApplyWindowBackgroundBlur", false) {
                    window.setBackgroundBlurRadius(blurRadiusPx)
                    true
                }
                if (success) {
                    activeBackend = BlurBackend.WINDOW_BACKGROUND_BLUR
                    HookDiagnostics.activeBlurBackend = activeBackend.displayName
                    return activeBackend
                }
            } else if (!canUseRegionalBlur) {
                SafeHook.log(
                    SafeHook.LogLevel.INFO,
                    message = "Skipped cross-window blur for full-screen IME window: " +
                        "windowHeight=$imeWindowHeightPx, displayHeight=$displayHeightPx"
                )
            }
        }

        // Fallback: Translucent Glass Surface
        activeBackend = BlurBackend.SURFACE_FALLBACK
        HookDiagnostics.activeBlurBackend = activeBackend.displayName
        return activeBackend
    }

    /**
     * Apply RenderEffect ONLY to background surface layer View (NEVER to whole DecorView or keyboard text/icons).
     */
    fun applySurfaceRenderEffect(surfaceView: View?, blurRadiusDp: Float, density: Float): Boolean {
        if (surfaceView == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return SafeHook.runSafe("SurfaceRenderEffect", false) {
                val radius = (blurRadiusDp * density).coerceAtLeast(1f)
                val effect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                surfaceView.setRenderEffect(effect)
                activeBackend = BlurBackend.VIEW_RENDER_EFFECT
                HookDiagnostics.activeBlurBackend = activeBackend.displayName
                true
            }
        }
        return false
    }

    fun clearBlur(window: Window?, surfaceView: View?) {
        SafeHook.runSafe("ClearBlur") {
            RegionalSurfaceBlurController.detach()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (window != null) {
                    clearUnsafeWindowBlur(window)
                }

                surfaceView?.setRenderEffect(null)
            }
            activeBackend = BlurBackend.DISABLED
            HookDiagnostics.activeBlurBackend = activeBackend.displayName
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearUnsafeWindowBlur(window: Window) {
        val lp = window.attributes
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        lp.blurBehindRadius = 0
        window.attributes = lp
        window.setBackgroundBlurRadius(0)
    }
}
