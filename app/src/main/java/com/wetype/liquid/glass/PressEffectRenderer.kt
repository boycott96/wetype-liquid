package com.wetype.liquid.glass

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

class PressEffectRenderer {
    private var isPressed = false
    private var pressProgress = 0f // 0f = normal, 1f = fully pressed
    private var animator: ValueAnimator? = null

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun isCurrentlyPressed(): Boolean = isPressed

    fun setPressed(
        pressed: Boolean,
        animate: Boolean = true,
        durationMs: Long = 100L,
        onInvalidate: () -> Unit
    ) {
        if (this.isPressed == pressed) return
        this.isPressed = pressed

        animator?.cancel()

        if (!animate) {
            pressProgress = if (pressed) 1f else 0f
            onInvalidate()
            return
        }

        val target = if (pressed) 1f else 0f
        animator = ValueAnimator.ofFloat(pressProgress, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                onInvalidate()
            }
            start()
        }
    }

    fun applyPressTransform(canvas: Canvas, bounds: RectF, maxScale: Float = 0.97f) {
        if (pressProgress <= 0f) return
        val currentScale = 1f - (1f - maxScale) * pressProgress
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        canvas.scale(currentScale, currentScale, centerX, centerY)
    }

    fun drawPressOverlay(
        canvas: Canvas,
        bounds: RectF,
        cornerRadius: Float,
        isNight: Boolean,
        opacityBoost: Float = 0.05f
    ) {
        if (pressProgress <= 0f) return
        val alphaInt = (255 * (opacityBoost * pressProgress).coerceIn(0f, 1f)).toInt()
        overlayPaint.color = if (isNight) {
            Color.argb(alphaInt, 255, 255, 255)
        } else {
            Color.argb(alphaInt, 255, 255, 255)
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, overlayPaint)
    }

    fun getBrightnessBoost(maxBoost: Float = 0.03f): Float = pressProgress * maxBoost
}
