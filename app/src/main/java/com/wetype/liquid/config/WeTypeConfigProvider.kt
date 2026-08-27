package com.wetype.liquid.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process

class WeTypeConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.wetype.liquid.config.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")

        const val METHOD_GET_CONFIG = "getConfig"
        const val METHOD_SET_CONFIG = "setConfig"
        const val METHOD_REPORT_DIAGNOSTICS = "reportDiagnostics"
        const val METHOD_GET_DIAGNOSTICS = "getDiagnostics"

        const val KEY_CONFIG_JSON = "config_json"
        const val KEY_DIAGNOSTICS_JSON = "diagnostics_json"
        const val KEY_MODULE_ACTIVE = "module_active"

        const val PREF_NAME = "wetype_liquid_prefs"
        const val PREF_KEY_CONFIG = "module_config"
        const val PREF_KEY_DIAGNOSTICS = "module_diagnostics"
        const val PREF_KEY_LAST_HEARTBEAT = "module_last_heartbeat"

        const val TARGET_PACKAGE = "com.tencent.wetype"
    }

    private var prefs: SharedPreferences? = null

    override fun onCreate(): Boolean {
        prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return true
    }

    fun isCallerAllowed(method: String): Boolean {
        val callingUid = Binder.getCallingUid()
        val myUid = Process.myUid()

        // 1. Module application itself has full permission
        if (callingUid == myUid) {
            return true
        }

        // 2. Resolve target package UID
        val ctx = context ?: return false
        val targetUid = try {
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageUid(TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageUid(TARGET_PACKAGE, 0)
            }
        } catch (t: Throwable) {
            -1
        }

        // 3. Permissions per method
        return when (method) {
            METHOD_GET_CONFIG -> callingUid == targetUid
            METHOD_SET_CONFIG -> false // ONLY module app (callingUid == myUid) can modify user configuration
            METHOD_REPORT_DIAGNOSTICS -> callingUid == targetUid
            METHOD_GET_DIAGNOSTICS -> callingUid == targetUid
            else -> false
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (!isCallerAllowed(METHOD_GET_CONFIG)) {
            return null
        }
        val cursor = MatrixCursor(arrayOf(KEY_CONFIG_JSON))
        val json = prefs?.getString(PREF_KEY_CONFIG, null) ?: ModuleConfig().toJson()
        cursor.addRow(arrayOf(json))
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isCallerAllowed(method)) {
            return null
        }

        val result = Bundle()
        when (method) {
            METHOD_GET_CONFIG -> {
                val json = prefs?.getString(PREF_KEY_CONFIG, null) ?: ModuleConfig().toJson()
                result.putString(KEY_CONFIG_JSON, json)
            }
            METHOD_SET_CONFIG -> {
                val json = extras?.getString(KEY_CONFIG_JSON) ?: arg
                if (json != null) {
                    prefs?.edit()?.putString(PREF_KEY_CONFIG, json)?.apply()
                    context?.contentResolver?.notifyChange(CONTENT_URI, null)
                    result.putBoolean("success", true)
                }
            }
            METHOD_REPORT_DIAGNOSTICS -> {
                val diagJson = extras?.getString(KEY_DIAGNOSTICS_JSON) ?: arg
                if (diagJson != null) {
                    prefs?.edit()
                        ?.putString(PREF_KEY_DIAGNOSTICS, diagJson)
                        ?.putLong(PREF_KEY_LAST_HEARTBEAT, System.currentTimeMillis())
                        ?.apply()
                    result.putBoolean("success", true)
                }
            }
            METHOD_GET_DIAGNOSTICS -> {
                val diagJson = prefs?.getString(PREF_KEY_DIAGNOSTICS, null)
                val lastHeartbeat = prefs?.getLong(PREF_KEY_LAST_HEARTBEAT, 0L) ?: 0L
                val isActive = (System.currentTimeMillis() - lastHeartbeat) < 60_000L // Active within last 60 seconds
                result.putString(KEY_DIAGNOSTICS_JSON, diagJson)
                result.putBoolean(KEY_MODULE_ACTIVE, isActive)
            }
        }
        return result
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.wetype.liquid.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
