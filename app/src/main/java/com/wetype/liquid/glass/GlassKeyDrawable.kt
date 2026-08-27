package com.wetype.liquid.glass

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.wetype.liquid.config.ModuleConfig

enum class KeyType {
    NORMAL,
    SPACE,
    FUNCTIONAL,
    ACTION
}

class GlassKeyDrawable(
    private var config: ModuleConfig,
    private var isNight: Boolean,
    private val density: Float,
    var keyType: KeyType = KeyType.NORMAL
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokeRenderer = GlassStrokeRenderer()
    val pressRenderer = PressEffectRenderer()

    private val boundsF = RectF()
    private val shadowRect = RectF()

    fun updateState(newConfig: ModuleConfig, isNightMode: Boolean) {
        this.config = newConfig
        this.isNight = isNightMode
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        boundsF.set(bounds)
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        var pressed = false
        for (s in state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true
                break
            }
        }
        if (config.pressAnimationEnabled) {
            pressRenderer.setPressed(
                pressed = pressed,
                animate = true,
                durationMs = config.pressDurationMs
            ) {
                invalidateSelf()
            }
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        if (boundsF.isEmpty) return

        val radius = config.keyRadiusDp * density
        val borderWidth = config.keyBorderWidthDp * density

        // Determine fill alpha based on key type
        val baseFillAlpha = if (isNight) config.keyFillAlphaDark else config.keyFillAlphaLight
        val boost = when (keyType) {
            KeyType.SPACE -> config.spaceKeyContrastBoost
            KeyType.FUNCTIONAL, KeyType.ACTION -> config.functionalKeyContrastBoost
            KeyType.NORMAL -> 0f
        } + pressRenderer.getBrightnessBoost(config.pressBrightnessBoost)

        canvas.save()

        // 0. Apply press transform (scale 0.96~0.98)
        if (config.pressAnimationEnabled) {
            pressRenderer.applyPressTransform(canvas, boundsF, config.pressScale)
        }

        // 1. Draw subtle bottom shadow
        if (config.keyBottomShadowAlpha > 0.01f) {
            val shadowOffset = 1.0f * density
            shadowRect.set(
                boundsF.left,
                boundsF.top + shadowOffset,
                boundsF.right,
                boundsF.bottom + shadowOffset
            )
            shadowPaint.color = ColorResolver.getBottomShadowColor(isNight, config.keyBottomShadowAlpha)
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        }

        // 2. Draw rounded keycap background
        bgPaint.color = ColorResolver.getKeyFillColor(isNight, baseFillAlpha, boost)
        canvas.drawRoundRect(boundsF, radius, radius, bgPaint)

        // 3. Draw subtle top highlight (top 15%)
        val highlightColor = ColorResolver.getTopHighlightColor(isNight, config.keyTopHighlightAlpha)
        strokeRenderer.drawTopHighlight(canvas, boundsF, radius, highlightColor, heightRatio = 0.16f)

        // 4. Draw faint inner border
        val borderColor = ColorResolver.getKeyBorderColor(isNight, config.keyBorderAlpha)
        strokeRenderer.drawInnerStroke(canvas, boundsF, radius, borderWidth, borderColor)

        // 5. Draw press overlay
        if (config.pressAnimationEnabled) {
            pressRenderer.drawPressOverlay(canvas, boundsF, radius, isNight, config.pressOpacityBoost)
        }

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
