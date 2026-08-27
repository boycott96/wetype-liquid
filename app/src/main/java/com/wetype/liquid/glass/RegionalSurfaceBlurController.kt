package com.wetype.liquid.glass

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.discovery.SafeHook
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Owns a child Surface that is exactly the size of the keyboard root. The
 * compositor blurs only what is behind this child Surface instead of applying
 * blur to WeType's near-full-screen IME window.
 */
object RegionalSurfaceBlurController {
    private const val TAG = "RegionalKeyboardBlurSurface"

    private var currentHostRef: WeakReference<ViewGroup>? = null
    private var currentSurfaceRef: WeakReference<RegionalBlurSurfaceView>? = null
    private var stateCallback: ((Boolean, String?) -> Unit)? = null

    fun attachOrUpdate(
        root: View?,
        config: ModuleConfig,
        isNight: Boolean,
        density: Float,
        onStateChanged: (Boolean, String?) -> Unit
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || root == null || config.blurRadiusDp <= 0f) {
            detach()
            onStateChanged(false, null)
            return false
        }

        // WeType returns a ConstraintLayout as its input view. Adding an
        // unconstrained child to it changes its layout. Prefer the system
        // FrameLayout inputArea parent, which is exactly the keyboard region.
        val parent = root.parent as? ViewGroup
        val host = when {
            parent is FrameLayout -> parent
            root is FrameLayout -> root
            else -> null
        } ?: run {
            onStateChanged(false, "No safe FrameLayout keyboard-region host was found")
            return false
        }

        val radiusPx = (config.blurRadiusDp * density).toInt().coerceIn(1, 150)
        val cornerRadiusPx = config.cornerRadiusTopDp * density
        val tintAlpha = BlurSafetyPolicy.regionalTintAlpha(config.backgroundAlpha)
        val tintColor = ColorResolver.getKeyboardBackgroundColor(isNight, tintAlpha)

        val existingHost = currentHostRef?.get()
        val existingSurface = currentSurfaceRef?.get()
        if (existingHost === host && existingSurface != null) {
            stateCallback = onStateChanged
            existingSurface.updateStyle(radiusPx, cornerRadiusPx, tintColor)
            return true
        }

        detach()
        stateCallback = onStateChanged

        val surfaceView = RegionalBlurSurfaceView(
            host = host,
            sourceRoot = root,
            radiusPx = radiusPx,
            cornerRadiusPx = cornerRadiusPx,
            tintColor = tintColor,
            onResult = ::handleSurfaceResult
        )
        currentHostRef = WeakReference(host)
        currentSurfaceRef = WeakReference(surfaceView)

        return try {
            host.addView(
                surfaceView,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    root.height.coerceAtLeast(1)
                ).apply {
                    gravity = Gravity.BOTTOM
                }
            )
            SafeHook.log(
                SafeHook.LogLevel.INFO,
                message = "Attached regional blur SurfaceView: " +
                    "root=${root.javaClass.name}(${root.width}x${root.height}), " +
                    "host=${host.javaClass.name}(${host.width}x${host.height})"
            )
            true
        } catch (t: Throwable) {
            currentHostRef = null
            currentSurfaceRef = null
            stateCallback = null
            onStateChanged(false, "Unable to attach regional blur surface: ${t.message}")
            false
        }
    }

    fun isAttached(): Boolean = currentSurfaceRef?.get() != null

    fun isActive(): Boolean = currentSurfaceRef?.get()?.isBlurActive == true

    fun detach() {
        val host = currentHostRef?.get()
        val surfaceView = currentSurfaceRef?.get()

        currentHostRef = null
        currentSurfaceRef = null
        stateCallback = null

        if (surfaceView != null) {
            surfaceView.releaseBlur()
            try {
                (surfaceView.parent as? ViewGroup ?: host)?.removeView(surfaceView)
            } catch (_: Throwable) {
            }
        }
    }

    fun refreshOrReapply() {
        val surfaceView = currentSurfaceRef?.get() ?: return
        surfaceView.requestReapplyBlur()
    }

    private fun handleSurfaceResult(surfaceView: RegionalBlurSurfaceView, active: Boolean, error: String?) {
        if (currentSurfaceRef?.get() !== surfaceView) return
        stateCallback?.invoke(active, error)
    }

    private class RegionalBlurSurfaceView(
        private val host: ViewGroup,
        sourceRoot: View,
        radiusPx: Int,
        cornerRadiusPx: Float,
        tintColor: Int,
        private val onResult: (RegionalBlurSurfaceView, Boolean, String?) -> Unit
    ) : SurfaceView(host.context), SurfaceHolder.Callback {

        private var radiusPx: Int = radiusPx
        private var cornerRadiusPx: Float = cornerRadiusPx
        private var tintColor: Int = tintColor

        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tintPath = Path()
        private val tintBounds = RectF()
        private val sourceRootRef = WeakReference(sourceRoot)
        private val sourceLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateLayoutSize(view.width, view.height)
        }

        var isBlurActive: Boolean = false
            private set

        init {
            tag = TAG
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setZOrderOnTop(false)
            setZOrderMediaOverlay(false)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this)
            sourceRoot.addOnLayoutChangeListener(sourceLayoutListener)
            sourceRoot.post {
                updateLayoutSize(sourceRoot.width, sourceRoot.height)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            applyBlurAndTint(0)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            applyBlurAndTint(0)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            clearSurfaceBlur()
            isBlurActive = false
            onResult(this, false, null)
        }

        fun requestReapplyBlur() {
            if (holder.surface.isValid) {
                applyBlurAndTint(0)
            }
        }

        fun updateStyle(radiusPx: Int, cornerRadiusPx: Float, tintColor: Int) {
            this.radiusPx = radiusPx
            this.cornerRadiusPx = cornerRadiusPx
            this.tintColor = tintColor
            if (holder.surface.isValid) {
                applyBlurAndTint(0)
            }
        }

        fun releaseBlur() {
            sourceRootRef.get()?.removeOnLayoutChangeListener(sourceLayoutListener)
            holder.removeCallback(this)
            clearSurfaceBlur()
            isBlurActive = false
        }

        private fun updateLayoutSize(sourceWidth: Int, sourceHeight: Int) {
            if (sourceWidth <= 0 || sourceHeight <= 0) return
            val params = layoutParams as? FrameLayout.LayoutParams ?: return
            if (params.width == ViewGroup.LayoutParams.MATCH_PARENT && params.height == sourceHeight &&
                params.gravity == Gravity.BOTTOM
            ) {
                return
            }
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = sourceHeight
            params.gravity = Gravity.BOTTOM
            layoutParams = params
        }

        private fun applyBlurAndTint(retryAttempt: Int = 0) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                onResult(this, false, "Regional Surface blur requires Android 12+")
                return
            }
            if (width <= 0 || height <= 0 || !holder.surface.isValid) {
                if (retryAttempt < 3) {
                    postDelayed({
                        if (currentSurfaceRef?.get() === this) {
                            applyBlurAndTint(retryAttempt + 1)
                        }
                    }, 80L * (retryAttempt + 1))
                }
                return
            }

            drawTintBuffer()

            val surfaceControl = try {
                surfaceControl
            } catch (t: Throwable) {
                onResult(this, false, "SurfaceControl unavailable: ${t.message}")
                if (retryAttempt < 3) {
                    postDelayed({
                        if (currentSurfaceRef?.get() === this) {
                            applyBlurAndTint(retryAttempt + 1)
                        }
                    }, 100L * (retryAttempt + 1))
                }
                return
            }
            if (!surfaceControl.isValid) {
                onResult(this, false, "SurfaceControl is invalid")
                if (retryAttempt < 3) {
                    postDelayed({
                        if (currentSurfaceRef?.get() === this) {
                            applyBlurAndTint(retryAttempt + 1)
                        }
                    }, 100L * (retryAttempt + 1))
                }
                return
            }

            val error = SurfaceBlurReflection.apply(
                surfaceControl = surfaceControl,
                radiusPx = radiusPx,
                cornerRadiusPx = cornerRadiusPx
            )
            isBlurActive = error == null
            onResult(this, isBlurActive, error)

            if (!isBlurActive && retryAttempt < 3) {
                postDelayed({
                    if (currentSurfaceRef?.get() === this && !isBlurActive) {
                        applyBlurAndTint(retryAttempt + 1)
                    }
                }, 120L * (retryAttempt + 1))
            }
        }

        private fun drawTintBuffer() {
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                tintPaint.color = tintColor
                tintBounds.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
                tintPath.reset()
                tintPath.addRoundRect(
                    tintBounds,
                    floatArrayOf(
                        cornerRadiusPx, cornerRadiusPx,
                        cornerRadiusPx, cornerRadiusPx,
                        0f, 0f,
                        0f, 0f
                    ),
                    Path.Direction.CW
                )
                canvas.drawPath(tintPath, tintPaint)
            } catch (t: Throwable) {
                SafeHook.log(SafeHook.LogLevel.WARN, message = "Unable to draw regional blur tint: ${t.message}")
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun clearSurfaceBlur() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val surfaceControl = try {
                surfaceControl
            } catch (_: Throwable) {
                null
            }
            if (surfaceControl != null && surfaceControl.isValid) {
                SurfaceBlurReflection.clear(surfaceControl)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object SurfaceBlurReflection {
        private var resolutionAttempted = false
        private var setBlurMethod: Method? = null
        private var setCornerRadiusMethod: Method? = null

        @Synchronized
        @SuppressLint("BlockedPrivateApi", "SoonBlockedPrivateApi")
        private fun resolveMethods(): Boolean {
            if (resolutionAttempted) return setBlurMethod != null
            resolutionAttempted = true

            return try {
                setBlurMethod = SurfaceControl.Transaction::class.java.getDeclaredMethod(
                    "setBackgroundBlurRadius",
                    SurfaceControl::class.java,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true }

                setCornerRadiusMethod = try {
                    SurfaceControl.Transaction::class.java.getDeclaredMethod(
                        "setCornerRadius",
                        SurfaceControl::class.java,
                        Float::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                } catch (_: Throwable) {
                    null
                }
                true
            } catch (t: Throwable) {
                SafeHook.log(
                    SafeHook.LogLevel.WARN,
                    message = "Regional Surface blur API is unavailable: ${t.javaClass.simpleName}: ${t.message}"
                )
                false
            }
        }

        fun apply(
            surfaceControl: SurfaceControl,
            radiusPx: Int,
            cornerRadiusPx: Float
        ): String? {
            if (!resolveMethods()) return "SurfaceControl background blur API is unavailable"

            val transaction = SurfaceControl.Transaction()
            return try {
                transaction.setAlpha(surfaceControl, 1f)
                setBlurMethod?.invoke(transaction, surfaceControl, radiusPx)
                setCornerRadiusMethod?.invoke(transaction, surfaceControl, cornerRadiusPx)
                transaction.apply()
                null
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                "${cause.javaClass.simpleName}: ${cause.message}"
            } finally {
                transaction.close()
            }
        }

        fun clear(surfaceControl: SurfaceControl) {
            if (!resolveMethods()) return
            val transaction = SurfaceControl.Transaction()
            try {
                setBlurMethod?.invoke(transaction, surfaceControl, 0)
                transaction.apply()
            } catch (_: Throwable) {
            } finally {
                transaction.close()
            }
        }
    }
}
