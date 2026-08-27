package com.wetype.liquid.hook.modern

import com.wetype.liquid.core.HookCallbackDispatcher
import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.SafeHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class WeTypeLibXposedEntry : XposedModule() {

    companion object {
        const val TARGET_PACKAGE = "com.tencent.wetype"
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)

        if (param.packageName != TARGET_PACKAGE) {
            return
        }

        HookDiagnostics.currentProcessName = param.packageName
        SafeHook.log(SafeHook.LogLevel.INFO, message = "Modern libxposed onPackageLoaded for ${param.packageName}")

        SafeHook.runSafe("Modern_onPackageLoaded") {
            ModernHookInstaller.installSystemHooks(this, param.defaultClassLoader)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        if (param.packageName != TARGET_PACKAGE) {
            return
        }

        SafeHook.log(SafeHook.LogLevel.INFO, message = "Modern libxposed onPackageReady for ${param.packageName}")

        SafeHook.runSafe("Modern_onPackageReady") {
            ModernHookInstaller.installTargetHooks(this, param.classLoader)
        }
    }
}
