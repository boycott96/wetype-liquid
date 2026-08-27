package com.wetype.liquid.glass

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt

object ColorResolver {

    // Bitwise color composition for zero-overhead and pure JVM/Android compatibility
    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return ((alpha and 0xFF) shl 24) or
                ((red and 0xFF) shl 16) or
                ((green and 0xFF) shl 8) or
                (blue and 0xFF)
    }

    fun isNightMode(context: Context?): Boolean {
        if (context == null) return false
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    @ColorInt
    fun getKeyboardBackgroundColor(isNight: Boolean, alphaFactor: Float): Int {
        val baseAlpha = (255 * alphaFactor.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(baseAlpha, 24, 24, 26) // rgba(24, 24, 26, alpha)
        } else {
            // Cool neutral glass rather than near-white paint. This keeps the
            // regional blur visible even over bright app backgrounds.
            argb(baseAlpha, 218, 221, 226)
        }
    }

    @ColorInt
    fun getKeyFillColor(isNight: Boolean, alpha: Float, boost: Float = 0f): Int {
        val finalAlpha = (255 * (alpha + boost).coerceIn(0f, 1f)).toInt()
        return argb(finalAlpha, 255, 255, 255)
    }

    @ColorInt
    fun getFunctionalKeyFillColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(finalAlpha, 104, 106, 112)
        } else {
            argb(finalAlpha, 236, 238, 242)
        }
    }

    @ColorInt
    fun getActionKeyFillColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(finalAlpha, 80, 82, 88)
        } else {
            argb(finalAlpha, 195, 198, 204)
        }
    }

    @ColorInt
    fun getKeyBorderColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return argb(finalAlpha, 255, 255, 255)
    }

    @ColorInt
    fun getTopHighlightColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return argb(finalAlpha, 255, 255, 255)
    }

    @ColorInt
    fun getBottomShadowColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(finalAlpha, 0, 0, 0)
        } else {
            argb(finalAlpha, 15, 23, 42)
        }
    }

    @ColorInt
    fun getPrimaryTextColor(isNight: Boolean): Int {
        return if (isNight) {
            0xFFF5F5F7.toInt()
        } else {
            0xFF1C1C1E.toInt()
        }
    }

    @ColorInt
    fun getSecondaryTextColor(isNight: Boolean): Int {
        val primary = getPrimaryTextColor(isNight)
        val alpha = if (isNight) (255 * 0.60f).toInt() else (255 * 0.55f).toInt()
        val r = (primary ushr 16) and 0xFF
        val g = (primary ushr 8) and 0xFF
        val b = primary and 0xFF
        return argb(alpha, r, g, b)
    }

    @ColorInt
    fun getCandidateSelectedHighlightColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(finalAlpha, 255, 255, 255)
        } else {
            argb(finalAlpha, 0, 0, 0)
        }
    }

    @ColorInt
    fun getCandidateDividerColor(isNight: Boolean, alpha: Float): Int {
        val finalAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return if (isNight) {
            argb(finalAlpha, 255, 255, 255)
        } else {
            argb(finalAlpha, 0, 0, 0)
        }
    }
}
