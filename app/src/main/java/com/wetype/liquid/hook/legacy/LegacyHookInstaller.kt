package com.wetype.liquid.hook.legacy

import android.content.res.Configuration
import android.graphics.Canvas
import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.wetype.liquid.core.HookCallbackDispatcher
import com.wetype.liquid.discovery.ClassFinder
import com.wetype.liquid.discovery.ClassScorer
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.MethodFinder
import com.wetype.liquid.discovery.SafeHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.CopyOnWriteArrayList

object LegacyHookInstaller {
    private val unhooks = CopyOnWriteArrayList<XC_MethodHook.Unhook>()

    fun installHooks(classLoader: ClassLoader) {
        HookDiagnostics.isModernLibXposed = false
        HookDiagnostics.frameworkName = "Legacy XposedBridge"
        HookDiagnostics.frameworkVersion = "API ${XposedBridge.getXposedVersion()}"
        HookDiagnostics.frameworkApi = XposedBridge.getXposedVersion()

        SafeHook.log(SafeHook.LogLevel.INFO, message = "Installing Legacy Xposed hooks on XposedBridge v${XposedBridge.getXposedVersion()}")

        val imsClass = InputMethodService::class.java

        // 1. Hook onWindowShown
        val onWindowShown = MethodFinder.findMethodExact(imsClass, "onWindowShown")
        if (onWindowShown != null) {
            val hookId = "Legacy_IMS_onWindowShown"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onWindowShown()", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(onWindowShown, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = param.thisObject as? InputMethodService
                        if (ims != null) {
                            HookCallbackDispatcher.onWindowShown(ims)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onWindowShown()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 2. Hook onWindowHidden
        val onWindowHidden = MethodFinder.findMethodExact(imsClass, "onWindowHidden")
        if (onWindowHidden != null) {
            val hookId = "Legacy_IMS_onWindowHidden"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onWindowHidden()", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(onWindowHidden, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = param.thisObject as? InputMethodService
                        if (ims != null) {
                            HookCallbackDispatcher.onWindowHidden(ims)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onWindowHidden()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 3. Hook onStartInputView
        val onStartInputView = MethodFinder.findMethodExact(
            imsClass,
            "onStartInputView",
            EditorInfo::class.java,
            java.lang.Boolean.TYPE
        )
        if (onStartInputView != null) {
            val hookId = "Legacy_IMS_onStartInputView"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onStartInputView(EditorInfo, boolean)", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(onStartInputView, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = param.thisObject as? InputMethodService
                        val info = param.args[0]
                        val restarting = param.args[1] as? Boolean ?: false
                        if (ims != null) {
                            HookCallbackDispatcher.onStartInputView(ims, info, restarting)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onStartInputView(EditorInfo, boolean)", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 4. Hook onCreateInputView
        val onCreateInputView = MethodFinder.findMethodExact(imsClass, "onCreateInputView")
        if (onCreateInputView != null) {
            val hookId = "Legacy_IMS_onCreateInputView"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onCreateInputView()", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(onCreateInputView, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val inputView = param.result as? View
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = param.thisObject as? InputMethodService
                        if (inputView != null && ims != null) {
                            HookCallbackDispatcher.onInputViewCreated(inputView, ims)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onCreateInputView()", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 4. Hook setInputView
        val setInputView = MethodFinder.findMethodExact(imsClass, "setInputView", View::class.java)
        if (setInputView != null) {
            val hookId = "Legacy_IMS_setInputView"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#setInputView(View)", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(setInputView, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val inputView = param.args[0] as? View
                        val ims = param.thisObject as? InputMethodService
                        if (inputView != null && ims != null) {
                            HookCallbackDispatcher.onInputViewCreated(inputView, ims)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#setInputView(View)", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 5. Hook onConfigurationChanged
        val onConfigChanged = MethodFinder.findMethodExact(imsClass, "onConfigurationChanged", Configuration::class.java)
        if (onConfigChanged != null) {
            val hookId = "Legacy_IMS_onConfigurationChanged"
            HookDiagnostics.recordHookDiscovered(hookId, "${imsClass.name}#onConfigurationChanged(Configuration)", "IMS_Lifecycle")
            try {
                val unhook = XposedBridge.hookMethod(onConfigChanged, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val ims = param.thisObject as? InputMethodService
                        val newConfig = param.args[0] as? Configuration
                        if (ims != null && newConfig != null) {
                            HookCallbackDispatcher.onConfigChanged(ims, newConfig)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${imsClass.name}#onConfigurationChanged(Configuration)", "IMS_Lifecycle")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        // 6. Discover & Hook Candidate View
        val candidateClass = ClassFinder.findValidatedKnownClass(ClassFinder.KNOWN_CANDIDATE_CLASSES, classLoader) {
            ClassScorer.validateCandidateViewClass(it)
        }
        if (candidateClass != null) {
            HookDiagnostics.recordDiscoveredClass("CandidateView", candidateClass.name, 100, listOf("KnownValidated"))
            hookCandidateClass(candidateClass)
        }

        // 7. Discover & Hook Toolbar View
        val toolbarClass = ClassFinder.findValidatedKnownClass(ClassFinder.KNOWN_TOOLBAR_CLASSES, classLoader) {
            ClassScorer.validateToolbarViewClass(it)
        }
        if (toolbarClass != null) {
            HookDiagnostics.recordDiscoveredClass("Toolbar", toolbarClass.name, 100, listOf("KnownValidated"))
            hookToolbarClass(toolbarClass)
        }

        // 8. Discover & Hook Keyboard Canvas View
        val keyboardClasses = ClassFinder.discoverKeyboardViewClasses(classLoader)
        for (scoreResult in keyboardClasses) {
            val kClass = scoreResult.clazz
            HookDiagnostics.recordDiscoveredClass("KeyboardView", kClass.name, scoreResult.score, scoreResult.matchedTraits)
            hookKeyboardViewClass(kClass)
            break
        }

        // 9. Hook DrawMethod single key background drawing
        hookDrawMethodClasses(classLoader)
    }

    private fun hookCandidateClass(clazz: Class<*>) {
        val onAttached = MethodFinder.findMethodExact(clazz, "onAttachedToWindow")
        if (onAttached != null) {
            val hookId = "Legacy_Candidate_onAttached"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onAttachedToWindow()", "CandidateBarIntegration")
            try {
                val unhook = XposedBridge.hookMethod(onAttached, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val view = param.thisObject as? View
                        if (view != null) {
                            HookCallbackDispatcher.onCandidateViewAttached(view)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onAttachedToWindow()", "CandidateBarIntegration")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookToolbarClass(clazz: Class<*>) {
        val onAttached = MethodFinder.findMethodExact(clazz, "onAttachedToWindow")
        if (onAttached != null) {
            val hookId = "Legacy_Toolbar_onAttached"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onAttachedToWindow()", "ToolbarIntegration")
            try {
                val unhook = XposedBridge.hookMethod(onAttached, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val view = param.thisObject as? View
                        if (view != null) {
                            HookCallbackDispatcher.onToolbarViewAttached(view)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onAttachedToWindow()", "ToolbarIntegration")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookKeyboardViewClass(clazz: Class<*>) {
        val onDraw = MethodFinder.findMethodExact(clazz, "onDraw", Canvas::class.java)
        if (onDraw != null) {
            val hookId = "Legacy_Keyboard_onDraw"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onDraw(Canvas)", "KeyboardCanvasRender")
            try {
                val unhook = XposedBridge.hookMethod(onDraw, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View
                        val canvas = param.args[0] as? Canvas
                        if (view != null && canvas != null) {
                            HookCallbackDispatcher.onBeforeKeyboardViewDraw(view, canvas)
                        }
                        HookDiagnostics.recordHookHit(hookId)
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onDraw(Canvas)", "KeyboardCanvasRender")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        val onTouch = MethodFinder.findMethodExact(clazz, "onTouchEvent", MotionEvent::class.java)
        if (onTouch != null) {
            val hookId = "Legacy_Keyboard_onTouchEvent"
            HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#onTouchEvent(MotionEvent)", "KeyboardTouchAnimation")
            try {
                val unhook = XposedBridge.hookMethod(onTouch, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookDiagnostics.recordHookHit(hookId)
                        val view = param.thisObject as? View
                        val event = param.args[0] as? MotionEvent
                        if (view != null && event != null) {
                            HookCallbackDispatcher.onKeyTouchEvent(view, event)
                        }
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#onTouchEvent(MotionEvent)", "KeyboardTouchAnimation")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }
    }

    private fun hookDrawMethodClasses(classLoader: ClassLoader) {
        val jClass = ClassFinder.findClass("com.tencent.wetype.plugin.hld.keyboard.selfdraw.j", classLoader)

        val mainTextColorMethod = jClass?.let { MethodFinder.findMethodExact(it, "q") }
        if (mainTextColorMethod != null && mainTextColorMethod.returnType == Integer.TYPE) {
            val hookId = "Legacy_ImeButton_mainTextColor"
            HookDiagnostics.recordHookDiscovered(hookId, "${jClass.name}#q()", "KeycapTextColor")
            try {
                val unhook = XposedBridge.hookMethod(mainTextColorMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val originalColor = param.result as? Int ?: return
                        param.result = HookCallbackDispatcher.onResolveKeyTextColor(param.thisObject, originalColor)
                        HookDiagnostics.recordHookHit(hookId)
                    }
                })
                unhooks.add(unhook)
                HookDiagnostics.recordHookInstalled(hookId, "${jClass.name}#q()", "KeycapTextColor")
            } catch (t: Throwable) {
                HookDiagnostics.recordHookFailure(hookId, t)
            }
        }

        for (className in ClassFinder.KNOWN_DRAWMETHOD_CLASSES) {
            val clazz = ClassFinder.findClass(className, classLoader) ?: continue

            val eMethod = if (jClass != null) {
                MethodFinder.findMethodExact(clazz, "e", Canvas::class.java, jClass)
            } else {
                clazz.declaredMethods.find { it.name == "e" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Canvas::class.java }
            }

            if (eMethod != null) {
                val hookId = "Legacy_DrawMethod_e_${clazz.simpleName}"
                HookDiagnostics.recordHookDiscovered(hookId, "${clazz.name}#e(Canvas, j)", "KeycapBackgroundDrawer")
                try {
                    val unhook = XposedBridge.hookMethod(eMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val canvas = param.args[0] as? Canvas
                            val button = param.args[1]

                            var handled = false
                            if (canvas != null && button != null) {
                                handled = HookCallbackDispatcher.onDrawKeyBackground(canvas, button)
                            }
                            HookDiagnostics.recordHookHit(hookId)

                            if (handled) {
                                param.result = null // Suppress original background draw
                            }
                        }
                    })
                    unhooks.add(unhook)
                    HookDiagnostics.recordHookInstalled(hookId, "${clazz.name}#e(Canvas, j)", "KeycapBackgroundDrawer")
                } catch (t: Throwable) {
                    HookDiagnostics.recordHookFailure(hookId, t)
                }
            }
        }
    }
}
