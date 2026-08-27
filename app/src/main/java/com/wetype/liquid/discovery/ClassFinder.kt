package com.wetype.liquid.discovery

import android.view.View
import dalvik.system.BaseDexClassLoader
import java.util.concurrent.ConcurrentHashMap

object ClassFinder {
    private val classCache = ConcurrentHashMap<String, Class<*>?>()
    private val scanResultsCache = ConcurrentHashMap<String, List<ClassScoreResult>>()

    val KNOWN_KEYBOARD_VIEW_CLASSES = listOf(
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.n",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.SelfDrawKeyboardView",
        "com.tencent.wetype.plugin.hld.keyboard.view.KeyboardView",
        "com.tencent.wetype.plugin.hld.keyboard.view.ImeKeyboardView",
        "com.tencent.wetype.plugin.hld.keyboard.KeyboardRootView",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.KeyboardView"
    )

    val KNOWN_DRAWMETHOD_CLASSES = listOf(
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.c",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.d",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.e",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.f",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.g",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.h",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.i",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.l",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.b",
        "com.tencent.wetype.plugin.hld.keyboard.selfdraw.drawmethod.a"
    )

    val KNOWN_CANDIDATE_CLASSES = listOf(
        "com.tencent.wetype.plugin.hld.candidate.ImeCandidateView",
        "com.tencent.wetype.plugin.hld.candidate.CandidateView",
        "com.tencent.wetype.plugin.hld.candidate.CandidateBarView",
        "com.tencent.wetype.plugin.hld.candidate.b"
    )

    val KNOWN_TOOLBAR_CLASSES = listOf(
        "com.tencent.wetype.plugin.hld.toolbar.ToolbarView",
        "com.tencent.wetype.plugin.hld.toolbar.ImeToolbarView",
        "com.tencent.wetype.plugin.hld.toolbar.a"
    )

    fun findClass(className: String, classLoader: ClassLoader): Class<*>? {
        return classCache.computeIfAbsent(className) {
            try {
                Class.forName(className, false, classLoader)
            } catch (t: Throwable) {
                null
            }
        }
    }

    /**
     * Attempts to find a known class, but validates its traits with ClassScorer before accepting.
     */
    fun findValidatedKnownClass(
        candidates: List<String>,
        classLoader: ClassLoader,
        validator: (Class<*>) -> Boolean
    ): Class<*>? {
        for (name in candidates) {
            val clazz = findClass(name, classLoader)
            if (clazz != null) {
                if (validator(clazz)) {
                    SafeHook.log(SafeHook.LogLevel.HOOK, message = "Matched and validated known class: $name")
                    return clazz
                } else {
                    SafeHook.log(SafeHook.LogLevel.WARN, message = "Known class name matched but rejected by trait validator: $name")
                }
            }
        }
        return null
    }

    fun discoverKeyboardViewClasses(classLoader: ClassLoader): List<ClassScoreResult> {
        val cacheKey = classLoader.hashCode().toString()
        val cached = scanResultsCache[cacheKey]
        if (cached != null) return cached

        val startTime = System.currentTimeMillis()
        val results = mutableListOf<ClassScoreResult>()

        // 1. Try known classes first
        for (name in KNOWN_KEYBOARD_VIEW_CLASSES) {
            val clazz = findClass(name, classLoader)
            if (clazz != null) {
                val scoreResult = ClassScorer.scoreKeyboardViewClass(clazz)
                if (scoreResult.isQualified) {
                    results.add(scoreResult)
                }
            }
        }

        // 2. Perform scoped DEX scan if not found in known classes
        if (results.isEmpty() && classLoader is BaseDexClassLoader) {
            try {
                val dexPathListField = MethodFinder.findFieldExact(BaseDexClassLoader::class.java, "pathList")
                val pathList = dexPathListField?.get(classLoader)
                if (pathList != null) {
                    val dexElementsField = MethodFinder.findFieldExact(pathList.javaClass, "dexElements")
                    val dexElements = dexElementsField?.get(pathList) as? Array<*>
                    if (dexElements != null) {
                        for (element in dexElements) {
                            if (element == null) continue
                            val dexFileField = MethodFinder.findFieldExact(element.javaClass, "dexFile")
                            val dexFile = dexFileField?.get(element) as? dalvik.system.DexFile ?: continue

                            val entries = dexFile.entries()
                            while (entries.hasMoreElements()) {
                                val className = entries.nextElement()
                                if (className.startsWith("com.tencent.wetype.plugin.hld.keyboard.")) {
                                    val clazz = findClass(className, classLoader) ?: continue
                                    val scoreResult = ClassScorer.scoreKeyboardViewClass(clazz)
                                    if (scoreResult.isQualified) {
                                        results.add(scoreResult)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                SafeHook.log(SafeHook.LogLevel.WARN, message = "Scoped DEX scan failed: ${t.message}")
            }
        }

        results.sortByDescending { it.score }
        scanResultsCache[cacheKey] = results

        val duration = System.currentTimeMillis() - startTime
        HookDiagnostics.lastScanDurationMs = duration
        HookDiagnostics.discoveredClassCount = results.size
        SafeHook.log(SafeHook.LogLevel.INFO, message = "Discovered ${results.size} keyboard view candidate classes in ${duration}ms")

        return results
    }
}
