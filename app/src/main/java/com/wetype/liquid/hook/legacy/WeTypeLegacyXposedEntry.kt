package com.wetype.liquid.hook.legacy

import com.wetype.liquid.core.HookCallbackDispatcher
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.SafeHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class WeTypeLegacyXposedEntry : IXposedHookLoadPackage {

    companion object {
        const val TARGET_PACKAGE = "com.tencent.wetype"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        HookDiagnostics.currentProcessName = lpparam.processName
        SafeHook.log(SafeHook.LogLevel.INFO, message = "Legacy Xposed module loaded for ${lpparam.packageName}")

        SafeHook.runSafe("Legacy_handleLoadPackage") {
            LegacyHookInstaller.installHooks(lpparam.classLoader)
        }
    }
}
