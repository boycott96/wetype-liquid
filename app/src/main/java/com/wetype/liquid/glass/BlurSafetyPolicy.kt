package com.wetype.liquid.glass

/**
 * Cross-window background blur is safe only when the IME window is restricted
 * to the keyboard region. A transparent, near-full-screen IME window would blur
 * the editor and hide the user's typed content.
 */
object BlurSafetyPolicy {
    private const val MAX_WINDOW_HEIGHT_RATIO = 0.70f

    fun canUseWindowBackgroundBlur(windowHeightPx: Int, displayHeightPx: Int): Boolean {
        if (windowHeightPx <= 0 || displayHeightPx <= 0) return false
        return windowHeightPx.toFloat() / displayHeightPx.toFloat() <= MAX_WINDOW_HEIGHT_RATIO
    }

    fun regionalTintAlpha(configuredBackgroundAlpha: Float): Float {
        return (configuredBackgroundAlpha * 0.62f).coerceIn(0.24f, 0.50f)
    }

    fun fallbackTintAlpha(configuredBackgroundAlpha: Float): Float {
        return (configuredBackgroundAlpha * 0.65f).coerceIn(0.28f, 0.48f)
    }
}
