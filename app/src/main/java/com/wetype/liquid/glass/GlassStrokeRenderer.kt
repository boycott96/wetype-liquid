package com.wetype.liquid.glass

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

class GlassStrokeRenderer {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePath = Path()
    private val highlightPath = Path()
    private val tempRect = RectF()
    private val highlightRect = RectF()

    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var cachedColor = 0
    private var cachedRatio = -1f
    private var cachedHighlightShader: LinearGradient? = null

    fun drawInnerStroke(
        canvas: Canvas,
        bounds: RectF,
        cornerRadius: Float,
        strokeWidth: Float,
        strokeColor: Int
    ) {
        val halfStroke = strokeWidth / 2f
        tempRect.set(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )
        val adjustedRadius = (cornerRadius - halfStroke).coerceAtLeast(0f)

        strokePaint.color = strokeColor
        strokePaint.strokeWidth = strokeWidth

        canvas.drawRoundRect(tempRect, adjustedRadius, adjustedRadius, strokePaint)
    }

    fun drawTopHighlight(
        canvas: Canvas,
        bounds: RectF,
        cornerRadius: Float,
        highlightColor: Int,
        heightRatio: Float = 0.18f
    ) {
        val highlightHeight = bounds.height() * heightRatio
        highlightRect.set(bounds.left, bounds.top, bounds.right, bounds.top + highlightHeight)

        if (cachedWidth != bounds.width() || cachedHeight != highlightHeight || cachedColor != highlightColor || cachedRatio != heightRatio) {
            cachedWidth = bounds.width()
            cachedHeight = highlightHeight
            cachedColor = highlightColor
            cachedRatio = heightRatio
            cachedHighlightShader = LinearGradient(
                bounds.left, bounds.top,
                bounds.left, bounds.top + highlightHeight,
                highlightColor,
                0x00FFFFFF and highlightColor,
                Shader.TileMode.CLAMP
            )
        }

        highlightPaint.shader = cachedHighlightShader

        // Clip to top rounded corner
        canvas.save()
        tempRect.set(bounds)
        highlightPath.reset()
        highlightPath.addRoundRect(
            tempRect,
            floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0f, 0f,
                0f, 0f
            ),
            Path.Direction.CW
        )
        canvas.clipPath(highlightPath)
        canvas.drawRect(highlightRect, highlightPaint)
        canvas.restore()
    }
}
