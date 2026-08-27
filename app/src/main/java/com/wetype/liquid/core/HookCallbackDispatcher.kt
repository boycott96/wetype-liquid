package com.wetype.liquid.core

import android.app.Dialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
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
import com.wetype.liquid.glass.GlassRenderer
import com.wetype.liquid.glass.KeyType
import com.wetype.liquid.glass.KeycapRenderer
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HookCallbackDispatcher {
    private const val SYMBOL_RAIL_CLASS_FRAGMENT = "ImeSboAndSybKeysScrollView"
    private const val SYMBOL_RAIL_DIVIDER_INSET_DP = 8f

    @Volatile
    var currentConfig: ModuleConfig = ModuleConfig()
        private set

    private val isInitialized = AtomicBoolean(false)
    private var screenLifecycleReceiver: BroadcastReceiver? = null
    @Volatile
    private var screenWasOff = false
    @Volatile
    private var screenTransitionGeneration = 0

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

        // 4. Register dynamic system cross-window blur listener (Android 12+)
        BlurController.registerCrossWindowBlurListener(appContext) { enabled ->
            val ims = currentImsRef?.get() ?: return@registerCrossWindowBlurListener
            val root = currentKeyboardRootRef?.get() ?: return@registerCrossWindowBlurListener
            val isNight = ColorResolver.isNightMode(ims)
            val density = ims.resources.displayMetrics.density
            applyRegionalBlur(root, ims, isNight, density)
        }

        registerScreenLifecycleReceiver(appContext)

        // 5. Initial diagnostics report
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

        if (screenWasOff) {
            rebindBlurAfterUnlock()
        }

        SafeHook.runSafe("Dispatcher_onWindowShown") {
            val window = getImsWindow(ims) ?: return@runSafe
            val density = ims.resources.displayMetrics.density
            val isNight = ColorResolver.isNightMode(ims)

            HookStateRegistry.saveWindowState(window)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            BlurController.applyWindowBlur(window, currentConfig.blurRadiusDp, density, ims)
            val root = currentKeyboardRootRef?.get()
            if (root != null) {
                ensureGlassDrawable(root, isNight, density)
                makeKeyboardHierarchyTransparent(root)
                applyRegionalBlur(root, ims, isNight, density)
                scheduleSymbolRailDividerInset(root, density)
            }
            DiagnosticsReporter.requestReport(ims, immediate = false)
        }
    }

    fun onStartInputView(ims: InputMethodService, info: Any?, restarting: Boolean) {
        ensureInitialized(ims)
        currentImsRef = WeakReference(ims)
        if (!currentConfig.enabled) return

        if (screenWasOff) {
            rebindBlurAfterUnlock()
        }

        SafeHook.runSafe("Dispatcher_onStartInputView") {
            val window = getImsWindow(ims)
            val density = ims.resources.displayMetrics.density
            val isNight = ColorResolver.isNightMode(ims)

            if (window != null) {
                HookStateRegistry.saveWindowState(window)
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                BlurController.applyWindowBlur(window, currentConfig.blurRadiusDp, density, ims)
            }

            val root = currentKeyboardRootRef?.get()
            if (root != null) {
                ensureGlassDrawable(root, isNight, density)
                makeKeyboardHierarchyTransparent(root)
                applyRegionalBlur(root, ims, isNight, density)
                scheduleSymbolRailDividerInset(root, density)
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

    private fun registerScreenLifecycleReceiver(context: Context) {
        if (screenLifecycleReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                    Intent.ACTION_USER_PRESENT -> rebindBlurAfterUnlock()
                    Intent.ACTION_SCREEN_ON -> {
                        val keyguard = receiverContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (keyguard?.isKeyguardLocked != true) {
                            rebindBlurAfterUnlock()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        SafeHook.runSafe("RegisterScreenLifecycleReceiver") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            screenLifecycleReceiver = receiver
        }
    }

    private fun handleScreenOff() {
        screenWasOff = true
        screenTransitionGeneration++
        SafeHook.runSafe("Dispatcher_handleScreenOff") {
            val ims = currentImsRef?.get() ?: return@runSafe
            val root = currentKeyboardRootRef?.get()
            BlurController.clearBlur(getImsWindow(ims), root)
            currentGlassDrawable?.setRegionalBlurActive(false)
            SafeHook.log(SafeHook.LogLevel.INFO, message = "Released regional blur surface for screen off")
        }
    }

    private fun rebindBlurAfterUnlock() {
        if (!screenWasOff || !currentConfig.enabled) return

        SafeHook.runSafe("Dispatcher_rebindBlurAfterUnlock") {
            val ims = currentImsRef?.get() ?: return@runSafe
            val root = currentKeyboardRootRef?.get() ?: return@runSafe
            val generation = screenTransitionGeneration

            performBlurRebind(ims, root, "after user unlock")
            screenWasOff = false

            // OriginOS continues replacing compositor layers briefly after
            // USER_PRESENT. Recreate once more after that transition settles so
            // the blur samples the resumed app rather than a keyguard layer.
            root.postDelayed({
                if (generation == screenTransitionGeneration && !screenWasOff) {
                    performBlurRebind(ims, root, "after unlock compositor settle")
                }
            }, 650L)
        }
    }

    private fun performBlurRebind(ims: InputMethodService, root: View, reason: String) {
        val window = getImsWindow(ims)
        val density = ims.resources.displayMetrics.density
        val isNight = ColorResolver.isNightMode(ims)

        BlurController.clearBlur(window, root)
        if (window != null) {
            HookStateRegistry.saveWindowState(window)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            BlurController.applyWindowBlur(window, currentConfig.blurRadiusDp, density, ims)
        }

        ensureGlassDrawable(root, isNight, density)
        makeKeyboardHierarchyTransparent(root)
        applyRegionalBlur(root, ims, isNight, density)
        scheduleSymbolRailDividerInset(root, density)
        root.invalidate()
        SafeHook.log(SafeHook.LogLevel.INFO, message = "Recreated regional blur surface $reason")
    }

    private fun ensureGlassDrawable(root: View, isNight: Boolean, density: Float): GlassDrawable {
        val drawable = root.background as? GlassDrawable
            ?: GlassDrawable(currentConfig, isNight, density).also { root.background = it }
        drawable.updateState(currentConfig, isNight)
        currentGlassDrawable = drawable
        return drawable
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

            scheduleSymbolRailDividerInset(inputView, density)

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
        // The symbol rail is a native scrollable list with its own panel,
        // dividers and pressed states. Preserve that entire subtree instead of
        // treating its rows as keyboard keycaps.
        if (isSymbolRailRoot(view)) return

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (isSymbolRailRoot(child)) continue
                if (child.background != null && child.background !is GlassDrawable) {
                    HookStateRegistry.saveViewState(child, FeatureGroup.KEYBOARD_ROOT)
                    child.background = ColorDrawable(Color.TRANSPARENT)
                }
                makeKeyboardHierarchyTransparent(child)
            }
        }
    }

    private fun isSymbolRailRoot(view: View): Boolean =
        view.javaClass.name.contains(SYMBOL_RAIL_CLASS_FRAGMENT)

    private fun scheduleSymbolRailDividerInset(root: View, density: Float) {
        root.postDelayed({ insetSymbolRailDividers(root, density) }, 80L)
        root.postDelayed({ insetSymbolRailDividers(root, density) }, 240L)
    }

    private fun insetSymbolRailDividers(root: View, density: Float) {
        if (isSymbolRailRoot(root) && root is ViewGroup) {
            insetSymbolRailRecyclerContent(root, density)
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                insetSymbolRailDividers(root.getChildAt(i), density)
            }
        }
    }

    private fun insetSymbolRailRecyclerContent(view: View, density: Float) {
        if (view.javaClass.name == "androidx.recyclerview.widget.RecyclerView" && view is ViewGroup) {
            val horizontalInset = (SYMBOL_RAIL_DIVIDER_INSET_DP * density).toInt()
            if (!HookStateRegistry.hasSavedState(view)) {
                HookStateRegistry.saveViewState(view, FeatureGroup.KEY_VIEW)
                view.setPadding(
                    view.paddingLeft + horizontalInset,
                    view.paddingTop,
                    view.paddingRight + horizontalInset,
                    view.paddingBottom
                )
                view.requestLayout()
            }
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                insetSymbolRailRecyclerContent(view.getChildAt(i), density)
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

    /**
     * WeType normally uses a light label for ACTION keys because their native
     * keycap is dark. Once ACTION uses the ordinary white glass material, keep
     * the original alpha but switch the label RGB to the normal key text color.
     */
    fun onResolveKeyTextColor(button: Any, originalColor: Int): Int {
        if (!currentConfig.enabled || !currentConfig.textContrastEnhanced) return originalColor

        return SafeHook.runSafe("Dispatcher_onResolveKeyTextColor", originalColor) {
            val buttonClass = button.javaClass
            val boundsMethod = MethodFinder.findMethodExact(buttonClass, "t")
                ?: MethodFinder.findMethodExact(buttonClass, "getBounds")
                ?: MethodFinder.findMethodExact(buttonClass, "getRect")
            val rect = boundsMethod?.invoke(button) as? Rect ?: return@runSafe originalColor
            if (KeycapRenderer.resolveKeyType(RectF(rect), button) != KeyType.ACTION) {
                return@runSafe originalColor
            }

            val isNight = ColorResolver.isNightMode(currentImsRef?.get())
            val normalTextColor = ColorResolver.getPrimaryTextColor(isNight)
            (originalColor and Color.BLACK) or (normalTextColor and 0x00FFFFFF)
        }
    }

    fun onKeyTouchEvent(view: View, event: MotionEvent) {
        if (!currentConfig.enabled || !currentConfig.pressAnimationEnabled) return
        KeycapRenderer.handleTouchEvent(view, event, currentConfig)
    }

    private fun applyRegionalBlur(root: View, ims: InputMethodService, isNight: Boolean, density: Float) {
        val visibleGlassDrawable = (root.background as? GlassDrawable)?.also {
            currentGlassDrawable = it
        }
        BlurController.applyRegionalSurfaceBlur(
            root = root,
            config = currentConfig,
            isNight = isNight,
            density = density
        ) { active ->
            visibleGlassDrawable?.setRegionalBlurActive(active)
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
