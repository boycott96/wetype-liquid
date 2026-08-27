package com.wetype.liquid.core

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import com.wetype.liquid.config.ConfigBridge
import com.wetype.liquid.config.ModuleConfig
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.MethodFinder
import com.wetype.liquid.discovery.SafeHook
import com.wetype.liquid.discovery.ViewTreeScanner
import com.wetype.liquid.glass.BlurController
import com.wetype.liquid.glass.ColorResolver
import com.wetype.liquid.glass.GlassDrawable
import com.wetype.liquid.glass.GlassKeyDrawable
import com.wetype.liquid.glass.GlassRenderer
import com.wetype.liquid.glass.KeyType
import com.wetype.liquid.glass.KeycapRenderer
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HookCallbackDispatcher {
    @Volatile
    var currentConfig: ModuleConfig = ModuleConfig()
        private set

    private val isInitialized = AtomicBoolean(false)

    private var currentImsRef: WeakReference<InputMethodService>? = null
    private var currentKeyboardRootRef: WeakReference<View>? = null
    private var currentGlassDrawable: GlassDrawable? = null

    private val candidateViews = WeakHashMap<View, Boolean>()
    private val toolbarViews = WeakHashMap<View, Boolean>()

    fun ensureInitialized(context: Context) {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        val appContext = context.applicationContext
        DiagnosticsReporter.setContext(appContext)

        // 1. Force fetch configuration
        currentConfig = ConfigBridge.getConfig(appContext, forceRefresh = true)

        // 2. Read WeType target package info
        SafeHook.runSafe("ReadWeTypePackageInfo") {
            val pm = appContext.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appContext.packageName, 0)
            }
            HookDiagnostics.wetypeVersionName = pInfo.versionName ?: "Unknown"
            HookDiagnostics.wetypeVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            HookDiagnostics.currentProcessName = appContext.packageName
        }

        // 3. Register config change listener
        ConfigBridge.addChangeListener { newConfig ->
            onModuleConfigChanged(newConfig)
        }

        // 4. Initial diagnostics report
        DiagnosticsReporter.requestReport(appContext, immediate = true)
    }

    fun onModuleConfigChanged(newConfig: ModuleConfig) {
        val previousEnabled = currentConfig.enabled
        currentConfig = newConfig

        val ims = currentImsRef?.get()
        val root = currentKeyboardRootRef?.get()

        if (!newConfig.enabled) {
            // Instant rollback: Restore all original views and window state
            SafeHook.runSafe("InstantRollback") {
                HookStateRegistry.restoreAll()
                if (ims != null) {
                    val window = getImsWindow(ims)
                    BlurController.clearBlur(window, root)
                }
            }
            DiagnosticsReporter.requestReport(immediate = false)
            return
        }

        // Re-enabled or parameters adjusted
        if (ims != null && root != null) {
            val isNight = ColorResolver.isNightMode(ims)
            val density = ims.resources.displayMetrics.density

            // Keyboard root
            if (!previousEnabled) {
                HookStateRegistry.saveViewState(root, FeatureGroup.KEYBOARD_ROOT)
                val glassDrawable = GlassDrawable(newConfig, isNight, density)
                currentGlassDrawable = glassDrawable
                root.background = glassDrawable
                makeKeyboardHierarchyTransparent(root)
            } else {
                currentGlassDrawable?.updateState(newConfig, isNight)
            }

            // Window Blur
            val window = getImsWindow(ims)
            if (window != null) {
                if (!previousEnabled) {
                    HookStateRegistry.saveWindowState(window)
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }
                BlurController.applyWindowBlur(window, newConfig.blurRadiusDp, density, ims)
            }

            applyRegionalBlur(root, ims, isNight, density)

            // Candidate Bar
            if (newConfig.candidateGlassEnabled) {
                for (cView in candidateViews.keys) {
                    if (cView != null) {
                        applyCandidateStyleInternal(cView)
                    }
                }
            } else {
                HookStateRegistry.restoreGroup(FeatureGroup.CANDIDATE)
                HookStateRegistry.restoreGroup(FeatureGroup.CANDIDATE_PARENT)
            }

            // Toolbar
            if (newConfig.toolbarGlassEnabled) {
                for (tView in toolbarViews.keys) {
                    if (tView != null) {
                        applyToolbarStyleInternal(tView, newConfig)
                    }
                }
            } else {
                HookStateRegistry.restoreGroup(FeatureGroup.TOOLBAR_ROOT)
                HookStateRegistry.restoreGroup(FeatureGroup.TOOLBAR_ICON)
            }
        }

        DiagnosticsReporter.requestReport(immediate = false)
    }

    fun onWindowShown(ims: InputMethodService) {
        ensureInitialized(ims)
        currentImsRef = WeakReference(ims)
        if (!currentConfig.enabled) return

        SafeHook.runSafe("Dispatcher_onWindowShown") {
            val window = getImsWindow(ims) ?: return@runSafe
            val density = ims.resources.displayMetrics.density

            HookStateRegistry.saveWindowState(window)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            BlurController.applyWindowBlur(window, currentConfig.blurRadiusDp, density, ims)
            val root = currentKeyboardRootRef?.get()
            if (root != null) {
                makeKeyboardHierarchyTransparent(root)
                applyRegionalBlur(root, ims, ColorResolver.isNightMode(ims), density)
                scheduleSymbolRailStyling(root, ColorResolver.isNightMode(ims), density)
            }
            DiagnosticsReporter.requestReport(ims, immediate = false)
        }
    }

    fun onWindowHidden(ims: InputMethodService) {
        ensureInitialized(ims)
        if (!currentConfig.enabled) return
        SafeHook.runSafe("Dispatcher_onWindowHidden") {
            val window = getImsWindow(ims) ?: return@runSafe
            val root = currentKeyboardRootRef?.get()
            BlurController.clearBlur(window, root)
            currentGlassDrawable?.setRegionalBlurActive(false)
            DiagnosticsReporter.requestReport(ims, immediate = false)
        }
    }

    fun onInputViewCreated(inputView: View, ims: InputMethodService) {
        ensureInitialized(ims)
        currentImsRef = WeakReference(ims)
        currentKeyboardRootRef = WeakReference(inputView)

        if (!currentConfig.enabled) return

        SafeHook.runSafe("Dispatcher_onInputViewCreated") {
            HookStateRegistry.saveViewState(inputView, FeatureGroup.KEYBOARD_ROOT)

            val density = ims.resources.displayMetrics.density
            val isNight = ColorResolver.isNightMode(ims)

            val glassDrawable = GlassDrawable(currentConfig, isNight, density)
            currentGlassDrawable = glassDrawable
            inputView.background = glassDrawable

            // Clear opaque child layout backgrounds so Glass surface is translucent
            makeKeyboardHierarchyTransparent(inputView)

            applyRegionalBlur(inputView, ims, isNight, density)

            scheduleSymbolRailStyling(inputView, isNight, density)

            // Scan view tree for debugging if enabled
            if (currentConfig.debugLogs || currentConfig.viewTreeExport) {
                inputView.post {
                    SafeHook.runSafe("ScanViewTree") {
                        val treeInfo = ViewTreeScanner.scanViewTree(inputView)
                        val formatted = treeInfo?.toFormattedString() ?: ""
                        HookDiagnostics.lastScannedViewTree = formatted
                    }
                }
            }

            DiagnosticsReporter.requestReport(ims, immediate = true)
        }
    }

    fun onConfigChanged(ims: InputMethodService, newConfig: Configuration) {
        ensureInitialized(ims)
        if (!currentConfig.enabled) return
        SafeHook.runSafe("Dispatcher_onConfigChanged") {
            val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            currentGlassDrawable?.updateState(currentConfig, isNight)
            val root = currentKeyboardRootRef?.get()
            if (root != null) {
                applyRegionalBlur(root, ims, isNight, ims.resources.displayMetrics.density)
            }
        }
    }

    fun onCandidateViewAttached(candidateView: View) {
        ensureInitialized(candidateView.context)
        candidateViews[candidateView] = true
        if (!currentConfig.enabled || !currentConfig.candidateGlassEnabled) return

        SafeHook.runSafe("Dispatcher_onCandidateViewAttached") {
            applyCandidateStyleInternal(candidateView)
            DiagnosticsReporter.requestReport(candidateView.context, immediate = false)
        }
    }

    private fun applyCandidateStyleInternal(candidateView: View) {
        HookStateRegistry.saveViewState(candidateView, FeatureGroup.CANDIDATE)
        candidateView.background = ColorDrawable(Color.TRANSPARENT)

        val parent = candidateView.parent as? ViewGroup
        if (parent != null && parent.background != null && parent.background !is GlassDrawable) {
            HookStateRegistry.saveViewState(parent, FeatureGroup.CANDIDATE_PARENT)
            parent.background = ColorDrawable(Color.TRANSPARENT)
        }
    }

    fun onToolbarViewAttached(toolbarView: View) {
        ensureInitialized(toolbarView.context)
        toolbarViews[toolbarView] = true
        if (!currentConfig.enabled || !currentConfig.toolbarGlassEnabled) return

        SafeHook.runSafe("Dispatcher_onToolbarViewAttached") {
            applyToolbarStyleInternal(toolbarView, currentConfig)
            DiagnosticsReporter.requestReport(toolbarView.context, immediate = false)
        }
    }

    private fun applyToolbarStyleInternal(toolbarView: View, config: ModuleConfig) {
        HookStateRegistry.saveViewState(toolbarView, FeatureGroup.TOOLBAR_ROOT)
        toolbarView.background = ColorDrawable(Color.TRANSPARENT)
        styleToolbarIconsRecursive(toolbarView, config.toolbarIconAlpha)
    }

    private fun styleToolbarIconsRecursive(view: View, iconAlpha: Float) {
        if (view is ImageView) {
            HookStateRegistry.saveViewState(view, FeatureGroup.TOOLBAR_ICON)
            view.alpha = iconAlpha
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleToolbarIconsRecursive(view.getChildAt(i), iconAlpha)
            }
        }
    }

    fun makeKeyboardHierarchyTransparent(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.background != null && child.background !is GlassDrawable) {
                    HookStateRegistry.saveViewState(child, FeatureGroup.KEYBOARD_ROOT)
                    child.background = ColorDrawable(Color.TRANSPARENT)
                }
                makeKeyboardHierarchyTransparent(child)
            }
        }
    }

    private fun styleSymbolRail(root: View, isNight: Boolean, density: Float) {
        if (root.javaClass.name.contains("ImeSboAndSybKeysScrollView") && root is ViewGroup) {
            styleSymbolRailDescendants(root, isNight, density)
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                styleSymbolRail(root.getChildAt(i), isNight, density)
            }
        }
    }

    private fun scheduleSymbolRailStyling(root: View, isNight: Boolean, density: Float) {
        root.postDelayed({ styleSymbolRail(root, isNight, density) }, 80L)
        root.postDelayed({ styleSymbolRail(root, isNight, density) }, 240L)
    }

    private fun styleSymbolRailDescendants(view: View, isNight: Boolean, density: Float) {
        if (view.javaClass.name == "androidx.recyclerview.widget.RecyclerView" && view is ViewGroup) {
            val insetX = (2.5f * density).toInt()
            val insetY = (3.5f * density).toInt()
            for (i in 0 until view.childCount) {
                val item = view.getChildAt(i)
                HookStateRegistry.saveViewState(item, FeatureGroup.KEY_VIEW)
                item.background = InsetDrawable(
                    GlassKeyDrawable(currentConfig, isNight, density, KeyType.NORMAL),
                    insetX,
                    insetY,
                    insetX,
                    insetY
                )
                item.invalidate()
            }
            SafeHook.log(
                SafeHook.LogLevel.INFO,
                message = "Styled ${view.childCount} symbol rail key surfaces"
            )
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleSymbolRailDescendants(view.getChildAt(i), isNight, density)
            }
        }
    }

    fun onBeforeKeyboardViewDraw(view: View, canvas: Canvas) {
        if (!currentConfig.enabled) return

        val density = view.resources.displayMetrics.density
        val isNight = ColorResolver.isNightMode(view.context)

        // Make view background transparent
        if (view.background != null && view.background !is GlassDrawable) {
            HookStateRegistry.saveViewState(view, FeatureGroup.KEYBOARD_ROOT)
            view.background = ColorDrawable(Color.TRANSPARENT)
        }

        // True per-key rendering across individual key bounds
        KeycapRenderer.renderAllKeys(
            canvas = canvas,
            view = view,
            config = currentConfig,
            isNight = isNight,
            density = density
        )
    }

    /**
     * Intercepts WeType's single key background drawer (e(Canvas, j)).
     * Replaces the opaque background with the Liquid Glass keycap!
     * Returns true if handled (suppresses original opaque background), false otherwise.
     */
    fun onDrawKeyBackground(canvas: Canvas, button: Any): Boolean {
        if (!currentConfig.enabled) return false

        val bClass = button.javaClass
        val getRectMethod = MethodFinder.findMethodExact(bClass, "t")
            ?: MethodFinder.findMethodExact(bClass, "getBounds")
            ?: MethodFinder.findMethodExact(bClass, "getRect")

        val rect = getRectMethod?.invoke(button) as? Rect ?: return false
        if (rect.isEmpty) return false

        val bounds = RectF(rect)
        val ims = currentImsRef?.get()
        val canvasDensity = canvas.density.toFloat() / 160f
        val density = if (canvasDensity > 0f) {
            canvasDensity
        } else {
            ims?.resources?.displayMetrics?.density ?: 3.0f
        }
        val isNight = ColorResolver.isNightMode(ims)
        val keyType = KeycapRenderer.resolveKeyType(bounds, button)

        GlassRenderer.renderKeyDirect(
            canvas = canvas,
            bounds = bounds,
            config = currentConfig,
            isNight = isNight,
            density = density,
            isPressed = false,
            keyType = keyType
        )

        return true
    }

    fun onKeyTouchEvent(view: View, event: MotionEvent) {
        if (!currentConfig.enabled || !currentConfig.pressAnimationEnabled) return
        KeycapRenderer.handleTouchEvent(view, event, currentConfig)
    }

    private fun applyRegionalBlur(root: View, ims: InputMethodService, isNight: Boolean, density: Float) {
        BlurController.applyRegionalSurfaceBlur(
            root = root,
            config = currentConfig,
            isNight = isNight,
            density = density
        ) { active ->
            currentGlassDrawable?.setRegionalBlurActive(active)
            DiagnosticsReporter.requestReport(ims, immediate = false)
        }
    }

    private fun getImsWindow(ims: InputMethodService): Window? {
        return try {
            val windowField = MethodFinder.findFieldExact(InputMethodService::class.java, "mWindow")
            val dialog = windowField?.get(ims) as? Dialog
            dialog?.window
        } catch (t: Throwable) {
            null
        }
    }
}
