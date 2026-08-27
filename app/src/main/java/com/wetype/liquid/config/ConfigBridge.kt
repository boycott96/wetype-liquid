package com.wetype.liquid.config

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wetype.liquid.discovery.HookDiagnostics
import java.util.concurrent.CopyOnWriteArrayList

object ConfigBridge {
    private const val TAG = "WeTypeLiquidGlass"

    @Volatile
    private var cachedConfig: ModuleConfig = ModuleConfig()

    private var contentObserverRegistered = false
    private val listeners = CopyOnWriteArrayList<(ModuleConfig) -> Unit>()

    fun getConfig(context: Context? = null, forceRefresh: Boolean = false): ModuleConfig {
        if (context == null) {
            return cachedConfig
        }

        // Register ContentObserver once in target process to avoid polling overhead
        if (!contentObserverRegistered) {
            registerContentObserver(context)
        }

        if (forceRefresh) {
            fetchConfigInternal(context)
        }

        return cachedConfig
    }

    private fun registerContentObserver(context: Context) {
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    fetchConfigInternal(context)
                }
            }
            context.contentResolver.registerContentObserver(
                WeTypeConfigProvider.CONTENT_URI,
                true,
                observer
            )
            contentObserverRegistered = true
        } catch (t: Throwable) {
            Log.d(TAG, "ContentObserver registration skipped: ${t.message}")
        }
    }

    private fun fetchConfigInternal(context: Context) {
        try {
            val bundle = context.contentResolver.call(
                WeTypeConfigProvider.CONTENT_URI,
                WeTypeConfigProvider.METHOD_GET_CONFIG,
                null,
                null
            )
            val json = bundle?.getString(WeTypeConfigProvider.KEY_CONFIG_JSON)
            if (!json.isNullOrBlank()) {
                val newConfig = ModuleConfig.fromJson(json)
                cachedConfig = newConfig
                notifyListeners(newConfig)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Config fetch from provider failed: ${t.message}")
        }
    }

    fun saveConfig(context: Context, config: ModuleConfig) {
        cachedConfig = config
        val json = config.toJson()

        // 1. Save to local shared preferences
        val prefs = context.getSharedPreferences(WeTypeConfigProvider.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(WeTypeConfigProvider.PREF_KEY_CONFIG, json).apply()

        // 2. Notify ContentProvider
        try {
            val bundle = Bundle().apply {
                putString(WeTypeConfigProvider.KEY_CONFIG_JSON, json)
            }
            context.contentResolver.call(
                WeTypeConfigProvider.CONTENT_URI,
                WeTypeConfigProvider.METHOD_SET_CONFIG,
                null,
                bundle
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Config push to provider failed: ${t.message}")
        }

        notifyListeners(config)
    }

    fun reportDiagnostics(context: Context) {
        try {
            val reportJson = HookDiagnostics.generateReportJson()
            val bundle = Bundle().apply {
                putString(WeTypeConfigProvider.KEY_DIAGNOSTICS_JSON, reportJson)
            }
            context.contentResolver.call(
                WeTypeConfigProvider.CONTENT_URI,
                WeTypeConfigProvider.METHOD_REPORT_DIAGNOSTICS,
                null,
                bundle
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to report diagnostics: ${t.message}")
        }
    }

    fun fetchRemoteDiagnostics(context: Context): Pair<String?, Boolean> {
        return try {
            val bundle = context.contentResolver.call(
                WeTypeConfigProvider.CONTENT_URI,
                WeTypeConfigProvider.METHOD_GET_DIAGNOSTICS,
                null,
                null
            )
            val json = bundle?.getString(WeTypeConfigProvider.KEY_DIAGNOSTICS_JSON)
            val active = bundle?.getBoolean(WeTypeConfigProvider.KEY_MODULE_ACTIVE, false) ?: false
            Pair(json, active)
        } catch (t: Throwable) {
            Pair(null, false)
        }
    }

    fun addChangeListener(listener: (ModuleConfig) -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: (ModuleConfig) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(config: ModuleConfig) {
        for (l in listeners) {
            try {
                l.invoke(config)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in config listener: ${t.message}")
            }
        }
    }
}
