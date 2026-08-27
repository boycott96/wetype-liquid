package com.wetype.liquid.glass

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.wetype.liquid.config.ModuleConfig

class GlassDrawable(
    private var config: ModuleConfig,
    private var isNight: Boolean,
    private val density: Float
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val strokeRenderer = GlassStrokeRenderer()
    private val bgPath = Path()
    private val boundsF = RectF()
    private var lastCornerRadiusDp: Float = -1f
    private var regionalBlurActive: Boolean = false

    init {
        updatePath()
    }

    fun updateState(newConfig: ModuleConfig, isNightMode: Boolean) {
        val radiusChanged = this.config.cornerRadiusTopDp != newConfig.cornerRadiusTopDp
        this.config = newConfig
        this.isNight = isNightMode
        if (radiusChanged) {
            updatePath()
        }
        invalidateSelf()
    }

    fun setRegionalBlurActive(active: Boolean) {
        if (regionalBlurActive == active) return
        regionalBlurActive = active
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        boundsF.set(bounds)
        updatePath()
    }

    private fun updatePath() {
        if (boundsF.isEmpty) return
        val topRadius = config.cornerRadiusTopDp * density
        lastCornerRadiusDp = config.cornerRadiusTopDp
        bgPath.reset()
        bgPath.addRoundRect(
            boundsF,
            floatArrayOf(
                topRadius, topRadius, // Top-left
                topRadius, topRadius, // Top-right
                0f, 0f,               // Bottom-right
                0f, 0f                // Bottom-left
            ),
            Path.Direction.CW
        )
    }

    override fun draw(canvas: Canvas) {
        if (boundsF.isEmpty) return

        if (lastCornerRadiusDp != config.cornerRadiusTopDp) {
            updatePath()
        }

        // 1. Draw rounded translucent background
        val effectiveAlpha = if (regionalBlurActive) {
            BlurSafetyPolicy.regionalTintAlpha(config.backgroundAlpha)
        } else {
            config.backgroundAlpha
        }
        bgPaint.color = ColorResolver.getKeyboardBackgroundColor(isNight, effectiveAlpha)
        canvas.drawPath(bgPath, bgPaint)

        // 2. Draw subtle top highlight
        val topRadius = config.cornerRadiusTopDp * density
        val highlightColor = ColorResolver.getTopHighlightColor(isNight, config.highlightAlpha)
        strokeRenderer.drawTopHighlight(canvas, boundsF, topRadius, highlightColor, heightRatio = 0.12f)

        // 3. Draw faint top border stroke
        val strokeColor = ColorResolver.getKeyBorderColor(isNight, config.highlightAlpha * 0.7f)
        val strokeWidth = 0.75f * density
        strokeRenderer.drawInnerStroke(canvas, boundsF, topRadius, strokeWidth, strokeColor)

        // 4. One restrained rule unifies the candidate/tool strip with the
        // keyboard panel while preserving a clear material hierarchy.
        val dividerY = boundsF.top + 65f * density
        if (dividerY < boundsF.bottom - 80f * density) {
            dividerPaint.color = ColorResolver.getCandidateDividerColor(
                isNight,
                config.candidateDividerAlpha
            )
            dividerPaint.strokeWidth = 0.5f * density
            val inset = 18f * density
            canvas.drawLine(boundsF.left + inset, dividerY, boundsF.right - inset, dividerY, dividerPaint)
        }
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
