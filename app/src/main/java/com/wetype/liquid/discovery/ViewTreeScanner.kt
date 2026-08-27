package com.wetype.liquid.discovery

import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject

object ViewTreeScanner {

    data class ViewInfo(
        val className: String,
        val idHex: String,
        val idName: String,
        val width: Int,
        val height: Int,
        val x: Float,
        val y: Float,
        val visibility: String,
        val backgroundDrawable: String,
        val children: List<ViewInfo> = emptyList()
    ) {
        fun toFormattedString(depth: Int = 0): String {
            val indent = "  ".repeat(depth)
            val idStr = if (idName.isNotEmpty()) " #$idName" else if (idHex != "0x0") " (id=$idHex)" else ""
            val bgStr = if (backgroundDrawable != "null") " bg=$backgroundDrawable" else ""
            val selfStr = "$indent└─ ${className.substringAfterLast('.')}$idStr [${width}x${height} @(${x.toInt()},${y.toInt()})] $visibility$bgStr"
            val childrenStr = children.joinToString("\n") { it.toFormattedString(depth + 1) }
            return if (childrenStr.isNotEmpty()) "$selfStr\n$childrenStr" else selfStr
        }

        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("class", className)
            obj.put("idHex", idHex)
            obj.put("idName", idName)
            obj.put("width", width)
            obj.put("height", height)
            obj.put("x", x)
            obj.put("y", y)
            obj.put("visibility", visibility)
            obj.put("background", backgroundDrawable)
            if (children.isNotEmpty()) {
                val array = JSONArray()
                children.forEach { array.put(it.toJson()) }
                obj.put("children", array)
            }
            return obj
        }
    }

    fun scanViewTree(root: View?): ViewInfo? {
        if (root == null) return null
        return scanViewInternal(root)
    }

    private fun scanViewInternal(view: View): ViewInfo {
        val className = view.javaClass.name
        val idHex = "0x" + Integer.toHexString(view.id)
        val idName = try {
            if (view.id != View.NO_ID && view.resources != null) {
                view.resources.getResourceEntryName(view.id)
            } else ""
        } catch (t: Throwable) {
            ""
        }
        val visibility = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }
        val bgDrawable = view.background?.javaClass?.name ?: "null"

        val childrenList = mutableListOf<ViewInfo>()
        if (view is ViewGroup) {
            val count = view.childCount
            for (i in 0 until count) {
                val child = view.getChildAt(i)
                if (child != null) {
                    childrenList.add(scanViewInternal(child))
                }
            }
        }

        return ViewInfo(
            className = className,
            idHex = idHex,
            idName = idName,
            width = view.width,
            height = view.height,
            x = view.x,
            y = view.y,
            visibility = visibility,
            backgroundDrawable = bgDrawable,
            children = childrenList
        )
    }

    fun findViewByClassFuzzy(root: View?, partialClassName: String): View? {
        if (root == null) return null
        if (root.javaClass.name.contains(partialClassName, ignoreCase = true)) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByClassFuzzy(root.getChildAt(i), partialClassName)
                if (found != null) return found
            }
        }
        return null
    }

    fun findAllViewsMatching(root: View?, predicate: (View) -> Boolean): List<View> {
        val result = mutableListOf<View>()
        if (root == null) return result
        if (predicate(root)) {
            result.add(root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                result.addAll(findAllViewsMatching(root.getChildAt(i), predicate))
            }
        }
        return result
    }
}
