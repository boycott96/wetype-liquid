package com.wetype.liquid.glass

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.wetype.liquid.config.ModuleConfig

object GlassRenderer {
    /* Hallmark · component: keyboard material · genre: modern-minimal
     * theme: studied-DNA (iOS floating frosted keyboard reference)
     * states: default · active · disabled-action
     */
    private val strokeRenderer = GlassStrokeRenderer()
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowRect = RectF()
    private val visualBounds = RectF()

    fun renderKeyDirect(
        canvas: Canvas,
        bounds: RectF,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float,
        isPressed: Boolean = false,
        keyType: KeyType = KeyType.NORMAL
    ) {
        val horizontalInset = when (keyType) {
            KeyType.SPACE -> 2.0f * density
            else -> 2.5f * density
        }
        val verticalInset = 3.5f * density
        visualBounds.set(
            bounds.left + horizontalInset,
            bounds.top + verticalInset,
            bounds.right - horizontalInset,
            bounds.bottom - verticalInset
        )
        if (visualBounds.width() <= 0f || visualBounds.height() <= 0f) return

        val radius = (config.keyRadiusDp * density).coerceAtMost(visualBounds.height() / 2f)
        val borderWidth = config.keyBorderWidthDp * density

        val baseFillAlpha = if (isNight) config.keyFillAlphaDark else config.keyFillAlphaLight
        val boost = when (keyType) {
            KeyType.SPACE -> config.spaceKeyContrastBoost
            KeyType.FUNCTIONAL, KeyType.ACTION -> config.functionalKeyContrastBoost
            KeyType.NORMAL -> 0f
        } + if (isPressed) 0.04f else 0f

        canvas.save()

        if (isPressed && config.pressAnimationEnabled) {
            val scale = config.pressScale
            canvas.scale(scale, scale, visualBounds.centerX(), visualBounds.centerY())
        }

        // Bottom shadow
        if (config.keyBottomShadowAlpha > 0.01f) {
            val shadowOffset = 0.75f * density
            shadowRect.set(
                visualBounds.left,
                visualBounds.top + shadowOffset,
                visualBounds.right,
                visualBounds.bottom + shadowOffset
            )
            shadowPaint.color = ColorResolver.getBottomShadowColor(isNight, config.keyBottomShadowAlpha)
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        }

        // Rounded background
        drawPaint.color = when (keyType) {
            KeyType.ACTION -> ColorResolver.getActionKeyFillColor(
                isNight,
                (baseFillAlpha - 0.08f + boost).coerceIn(0f, 1f)
            )
            KeyType.FUNCTIONAL -> ColorResolver.getFunctionalKeyFillColor(
                isNight,
                (baseFillAlpha - 0.04f + boost).coerceIn(0f, 1f)
            )
            else -> ColorResolver.getKeyFillColor(isNight, baseFillAlpha, boost)
        }
        canvas.drawRoundRect(visualBounds, radius, radius, drawPaint)

        // Top highlight
        val highlightColor = ColorResolver.getTopHighlightColor(isNight, config.keyTopHighlightAlpha)
        strokeRenderer.drawTopHighlight(canvas, visualBounds, radius, highlightColor, heightRatio = 0.12f)

        // Inner border
        val borderColor = ColorResolver.getKeyBorderColor(isNight, config.keyBorderAlpha)
        strokeRenderer.drawInnerStroke(canvas, visualBounds, radius, borderWidth, borderColor)

        canvas.restore()
    }

    fun renderCandidateSelectedPill(
        canvas: Canvas,
        bounds: RectF,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float
    ) {
        val radius = 8f * density
        pillPaint.color = ColorResolver.getCandidateSelectedHighlightColor(isNight, config.candidateHighlightAlpha)
        canvas.drawRoundRect(bounds, radius, radius, pillPaint)

        // Subtle border on selected candidate
        val borderColor = ColorResolver.getKeyBorderColor(isNight, config.keyBorderAlpha * 0.6f)
        strokeRenderer.drawInnerStroke(canvas, bounds, radius, 0.5f * density, borderColor)
    }

    fun renderToolbarItem(
        canvas: Canvas,
        bounds: RectF,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float,
        isPressed: Boolean
    ) {
        if (!isPressed) return // Keep toolbar icons clean without static plate
        val radius = bounds.width().coerceAtMost(bounds.height()) / 2f
        drawPaint.color = ColorResolver.getCandidateSelectedHighlightColor(isNight, 0.18f)
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, drawPaint)
    }

    fun adjustTextPaint(
        paint: Paint,
        isNight: Boolean,
        isPrimary: Boolean = true,
        config: ModuleConfig
    ) {
        if (!config.textContrastEnhanced) return
        if (isPrimary) {
            paint.color = ColorResolver.getPrimaryTextColor(isNight)
        } else {
            paint.color = ColorResolver.getSecondaryTextColor(isNight)
        }
    }
}
