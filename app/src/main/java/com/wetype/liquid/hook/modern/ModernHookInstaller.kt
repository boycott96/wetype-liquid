package com.wetype.liquid.hook.modern

import android.content.res.Configuration
import android.graphics.Canvas
import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import com.wetype.liquid.core.HookCallbackDispatcher
import com.wetype.liquid.discovery.ClassFinder
import com.wetype.liquid.discovery.ClassScorer
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.MethodFinder
import com.wetype.liquid.discovery.SafeHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList

object ModernHookInstaller {
    private val hookHandles = CopyOnWriteArrayList<XposedInterface.HookHandle>()

    fun installSystemHooks(module: XposedModule, defaultClassLoader: ClassLoader) {
        HookDiagnostics.isModernLibXposed = true
        HookDiagnostics.frameworkName = module.frameworkName
        HookDiagnostics.frameworkVersion = module.frameworkVersion
        HookDiagnostics.frameworkApi = module.apiVersion

        SafeHook.log(SafeHook.LogLevel.INFO, message = "Installing Modern libxposed system hooks on ${module.frameworkName} API ${module.apiVersion}")

        val imsClass = InputMethodService::class.java

        // 1. Hook onWindowShown
        val onWindowShown = MethodFinder.findMethodExact(imsClass, "onWindowShown")
        if (onWindowShown != null) {
            val hookId = "Modern_IMS_onWindowShown"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onWindowShown()", "IMS_Lifecycle")
            try {
                val handle = module.hook(onWindowShown)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = chain.thisObject as? InputMethodService
                        if (ims != null) {
                            HookCallbackDispatcher.onWindowShown(ims)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onWindowShown()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 2. Hook onWindowHidden
        val onWindowHidden = MethodFinder.findMethodExact(imsClass, "onWindowHidden")
        if (onWindowHidden != null) {
            val hookId = "Modern_IMS_onWindowHidden"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onWindowHidden()", "IMS_Lifecycle")
            try {
                val handle = module.hook(onWindowHidden)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = chain.thisObject as? InputMethodService
                        if (ims != null) {
                            HookCallbackDispatcher.onWindowHidden(ims)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onWindowHidden()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 3. Hook onCreateInputView
        val onCreateInputView = MethodFinder.findMethodExact(imsClass, "onCreateInputView")
        if (onCreateInputView != null) {
            val hookId = "Modern_IMS_onCreateInputView"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onCreateInputView()", "IMS_Lifecycle")
            try {
                val handle = module.hook(onCreateInputView)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val inputView = chain.proceed() as? View
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = chain.thisObject as? InputMethodService
                        if (inputView != null && ims != null) {
                            HookCallbackDispatcher.onInputViewCreated(inputView, ims)
                        }
                        inputView
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onCreateInputView()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 4. Hook setInputView
        val setInputView = MethodFinder.findMethodExact(imsClass, "setInputView", View::class.java)
        if (setInputView != null) {
            val hookId = "Modern_IMS_setInputView"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#setInputView(View)", "IMS_Lifecycle")
            try {
                val handle = module.hook(setInputView)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val inputView = chain.getArg(0) as? View
                        val ims = chain.thisObject as? InputMethodService
                        if (inputView != null && ims != null) {
                            HookCallbackDispatcher.onInputViewCreated(inputView, ims)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#setInputView(View)", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 5. Hook onConfigurationChanged
        val onConfigChanged = MethodFinder.findMethodExact(imsClass, "onConfigurationChanged", Configuration::class.java)
        if (onConfigChanged != null) {
            val hookId = "Modern_IMS_onConfigurationChanged"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onConfigurationChanged(Configuration)", "IMS_Lifecycle")
            try {
                val handle = module.hook(onConfigChanged)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = chain.thisObject as? InputMethodService
                        val newConfig = chain.getArg(0) as? Configuration
                        if (ims != null && newConfig != null) {
                            HookCallbackDispatcher.onConfigChanged(ims, newConfig)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onConfigurationChanged(Configuration)", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    fun installTargetHooks(module: XposedModule, targetClassLoader: ClassLoader) {
        SafeHook.log(SafeHook.LogLevel.INFO, message = "Installing Modern libxposed target hooks on WeType classloader")

        // 1. Discover & Hook Candidate View
        val candidateClass = ClassFinder.findValidatedKnownClass(ClassFinder.KNOWN_CANDIDATE_CLASSES, targetClassLoader) {
            ClassScorer.validateCandidateViewClass(it)
        }
        if (candidateClass != null) {
            HookDiagnostics.recordDiscoveredClass("CandidateView", candidateClass.name, 100, listOf("KnownValidated"))
            hookCandidateClass(module, candidateClass)
        }

        // 2. Discover & Hook Toolbar View
        val toolbarClass = ClassFinder.findValidatedKnownClass(ClassFinder.KNOWN_TOOLBAR_CLASSES, targetClassLoader) {
            ClassScorer.validateToolbarViewClass(it)
        }
        if (toolbarClass != null) {
            HookDiagnostics.recordDiscoveredClass("Toolbar", toolbarClass.name, 100, listOf("KnownValidated"))
            hookToolbarClass(module, toolbarClass)
        }

        // 3. Discover & Hook Keyboard Canvas View
        val keyboardClasses = ClassFinder.discoverKeyboardViewClasses(targetClassLoader)
        for (scoreResult in keyboardClasses) {
            val kClass = scoreResult.clazz
            HookDiagnostics.recordDiscoveredClass("KeyboardView", kClass.name, scoreResult.score, scoreResult.matchedTraits)
            hookKeyboardViewClass(module, kClass)
            break
        }

        // 4. Hook DrawMethod single key background drawing
        hookDrawMethodClasses(module, targetClassLoader)
    }

    private fun hookCandidateClass(module: XposedModule, clazz: Class<*>) {
        val onAttached = MethodFinder.findMethodExact(clazz, "onAttachedToWindow")
        if (onAttached != null) {
            val hookId = "Modern_Candidate_onAttached"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onAttachedToWindow()", "CandidateBarIntegration")
            try {
                val handle = module.hook(onAttached)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val view = chain.thisObject as? View
                        if (view != null) {
                            HookCallbackDispatcher.onCandidateViewAttached(view)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onAttachedToWindow()", "CandidateBarIntegration")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookToolbarClass(module: XposedModule, clazz: Class<*>) {
        val onAttached = MethodFinder.findMethodExact(clazz, "onAttachedToWindow")
        if (onAttached != null) {
            val hookId = "Modern_Toolbar_onAttached"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onAttachedToWindow()", "ToolbarIntegration")
            try {
                val handle = module.hook(onAttached)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val view = chain.thisObject as? View
                        if (view != null) {
                            HookCallbackDispatcher.onToolbarViewAttached(view)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onAttachedToWindow()", "ToolbarIntegration")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookKeyboardViewClass(module: XposedModule, clazz: Class<*>) {
        val onDraw = MethodFinder.findMethodExact(clazz, "onDraw", Canvas::class.java)
        if (onDraw != null) {
            val hookId = "Modern_Keyboard_onDraw"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onDraw(Canvas)", "KeyboardCanvasRender")
            try {
                val handle = module.hook(onDraw)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val view = chain.thisObject as? View
                        val canvas = chain.getArg(0) as? Canvas
                        if (view != null && canvas != null) {
                            HookCallbackDispatcher.onBeforeKeyboardViewDraw(view, canvas)
                        }
                        HookDiagnostics.recordHookHit(hookId)
                        chain.proceed()
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onDraw(Canvas)", "KeyboardCanvasRender")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        val onTouch = MethodFinder.findMethodExact(clazz, "onTouchEvent", MotionEvent::class.java)
        if (onTouch != null) {
            val hookId = "Modern_Keyboard_onTouchEvent"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onTouchEvent(MotionEvent)", "KeyboardTouchAnimation")
            try {
                val handle = module.hook(onTouch)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        HookDiagnostics.recordHookHit(hookId)
                        val view = chain.thisObject as? View
                        val event = chain.getArg(0) as? MotionEvent
                        if (view != null && event != null) {
                            HookCallbackDispatcher.onKeyTouchEvent(view, event)
                        }
                        result
                    }
                hookHandles.add(handle)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onTouchEvent(MotionEvent)", "KeyboardTouchAnimation")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookDrawMethodClasses(module: XposedModule, classLoader: ClassLoader) {
        val jClass = ClassFinder.findClass("com.tencent.wetype.plugin.hld.keyboard.selfdraw.j", classLoader)

        for (className in ClassFinder.KNOWN_DRAWMETHOD_CLASSES) {
            val clazz = ClassFinder.findClass(className, classLoader) ?: continue

            // 1. Hook e(Canvas, j) - key background drawing
            val eMethod = if (jClass != null) {
                MethodFinder.findMethodExact(clazz, "e", Canvas::class.java, jClass)
            } else {
                clazz.declaredMethods.find { it.name == "e" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Canvas::class.java }
            }

            if (eMethod != null) {
                val hookId = "Modern_DrawMethod_e_${clazz.simpleName}"
                HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#e(Canvas, j)", "KeycapBackgroundDrawer")
                try {
                    val handle = module.hook(eMethod)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            val canvas = chain.getArg(0) as? Canvas
                            val button = chain.getArg(1)

                            var handled = false
                            if (canvas != null && button != null) {
                                handled = HookCallbackDispatcher.onDrawKeyBackground(canvas, button)
                            }
                            HookDiagnostics.recordHookHit(hookId)

                            if (!handled) {
                                chain.proceed()
                            } else {
                                null
                            }
                        }
                    hookHandles.add(handle)
                    HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#e(Canvas, j)", "KeycapBackgroundDrawer")
                } catch (t: Throwable) {
                    HookDiagnostics.recordHookFailure(hookId, t)
                }
            }
        }
    }
}
