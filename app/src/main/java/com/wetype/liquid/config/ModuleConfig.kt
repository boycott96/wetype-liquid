package com.wetype.liquid.config

import com.google.gson.Gson
import java.io.Serializable

enum class GlassPreset(val displayName: String, val description: String) {
    LIQUID("Liquid", "Balanced translucent glass with subtle top highlight and soft blur"),
    CLEAR("Clear", "High transparency with crisp delicate borders and bright accents"),
    SOFT("Soft", "Warm frosted texture with deeper blur and gentle press feedback"),
    MINIMAL("Minimal", "Ultra-clean aesthetic with minimal borders and subtle keys"),
    DARK_GLASS("Dark Glass", "Refined obsidian deep glass tailored for dark environments"),
    CUSTOM("Custom", "User customized visual parameters")
}

data class ModuleConfig(
    // Master switch
    var enabled: Boolean = true,

    // Preset
    var preset: GlassPreset = GlassPreset.LIQUID,

    // Keyboard Background
    var backgroundAlpha: Float = 0.72f,
    var blurRadiusDp: Float = 28f,
    var cornerRadiusTopDp: Float = 28f,
    var highlightAlpha: Float = 0.18f,
    var shadowAlpha: Float = 0.08f,

    // Keycaps
    var keyFillAlphaLight: Float = 0.96f,
    var keyFillAlphaDark: Float = 0.34f,
    var keyRadiusDp: Float = 14f,
    var keyBorderWidthDp: Float = 0.45f,
    var keyBorderAlpha: Float = 0.28f,
    var keyTopHighlightAlpha: Float = 0.18f,
    var keyBottomShadowAlpha: Float = 0.10f,

    // Press Feedback
    var pressAnimationEnabled: Boolean = true,
    var pressScale: Float = 0.97f,
    var pressBrightnessBoost: Float = 0.03f,
    var pressOpacityBoost: Float = 0.05f,
    var pressDurationMs: Long = 100L,

    // Functional & Space Keys
    var spaceKeyContrastBoost: Float = 0.05f,
    var functionalKeyContrastBoost: Float = 0.06f,

    // Candidate Bar
    var candidateGlassEnabled: Boolean = true,
    var candidateHighlightAlpha: Float = 0.15f,
    var candidateDividerAlpha: Float = 0.08f,

    // Toolbar
    var toolbarGlassEnabled: Boolean = true,
    var toolbarIconAlpha: Float = 0.70f,
    var toolbarPressEffect: Boolean = true,

    // Text appearance
    var textContrastEnhanced: Boolean = true,

    // Diagnostics & Debug
    var debugLogs: Boolean = false,
    var viewTreeExport: Boolean = false
) : Serializable {

    fun applyPreset(newPreset: GlassPreset): ModuleConfig {
        this.preset = newPreset
        when (newPreset) {
            GlassPreset.LIQUID -> {
                backgroundAlpha = 0.72f
                blurRadiusDp = 28f
                cornerRadiusTopDp = 28f
                highlightAlpha = 0.18f
                shadowAlpha = 0.08f
                keyFillAlphaLight = 0.96f
                keyFillAlphaDark = 0.34f
                keyRadiusDp = 14f
                keyBorderWidthDp = 0.45f
                keyBorderAlpha = 0.28f
                keyTopHighlightAlpha = 0.18f
                keyBottomShadowAlpha = 0.10f
                pressScale = 0.97f
                spaceKeyContrastBoost = 0.05f
                functionalKeyContrastBoost = 0.06f
                candidateHighlightAlpha = 0.15f
                toolbarIconAlpha = 0.70f
            }
            GlassPreset.CLEAR -> {
                backgroundAlpha = 0.55f
                blurRadiusDp = 35f
                cornerRadiusTopDp = 24f
                highlightAlpha = 0.22f
                shadowAlpha = 0.05f
                keyFillAlphaLight = 0.28f
                keyFillAlphaDark = 0.14f
                keyRadiusDp = 12f
                keyBorderWidthDp = 0.75f
                keyBorderAlpha = 0.24f
                keyTopHighlightAlpha = 0.16f
                keyBottomShadowAlpha = 0.04f
                pressScale = 0.96f
                spaceKeyContrastBoost = 0.04f
                functionalKeyContrastBoost = 0.05f
                candidateHighlightAlpha = 0.18f
                toolbarIconAlpha = 0.80f
            }
            GlassPreset.SOFT -> {
                backgroundAlpha = 0.80f
                blurRadiusDp = 24f
                cornerRadiusTopDp = 20f
                highlightAlpha = 0.12f
                shadowAlpha = 0.10f
                keyFillAlphaLight = 0.44f
                keyFillAlphaDark = 0.26f
                keyRadiusDp = 10f
                keyBorderWidthDp = 0.50f
                keyBorderAlpha = 0.14f
                keyTopHighlightAlpha = 0.08f
                keyBottomShadowAlpha = 0.08f
                pressScale = 0.98f
                spaceKeyContrastBoost = 0.06f
                functionalKeyContrastBoost = 0.07f
                candidateHighlightAlpha = 0.12f
                toolbarIconAlpha = 0.65f
            }
            GlassPreset.MINIMAL -> {
                backgroundAlpha = 0.65f
                blurRadiusDp = 20f
                cornerRadiusTopDp = 18f
                highlightAlpha = 0.08f
                shadowAlpha = 0.04f
                keyFillAlphaLight = 0.22f
                keyFillAlphaDark = 0.12f
                keyRadiusDp = 9f
                keyBorderWidthDp = 0.40f
                keyBorderAlpha = 0.10f
                keyTopHighlightAlpha = 0.06f
                keyBottomShadowAlpha = 0.02f
                pressScale = 0.98f
                spaceKeyContrastBoost = 0.03f
                functionalKeyContrastBoost = 0.04f
                candidateHighlightAlpha = 0.10f
                toolbarIconAlpha = 0.60f
            }
            GlassPreset.DARK_GLASS -> {
                backgroundAlpha = 0.75f
                blurRadiusDp = 30f
                cornerRadiusTopDp = 22f
                highlightAlpha = 0.14f
                shadowAlpha = 0.12f
                keyFillAlphaLight = 0.35f
                keyFillAlphaDark = 0.22f
                keyRadiusDp = 11f
                keyBorderWidthDp = 0.60f
                keyBorderAlpha = 0.16f
                keyTopHighlightAlpha = 0.10f
                keyBottomShadowAlpha = 0.08f
                pressScale = 0.97f
                spaceKeyContrastBoost = 0.05f
                functionalKeyContrastBoost = 0.06f
                candidateHighlightAlpha = 0.14f
                toolbarIconAlpha = 0.75f
            }
            GlassPreset.CUSTOM -> {
                // Keep current customized values
            }
        }
        return this
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String?): ModuleConfig {
            if (json.isNullOrBlank()) return ModuleConfig()
            return try {
                Gson().fromJson(json, ModuleConfig::class.java) ?: ModuleConfig()
            } catch (t: Throwable) {
                ModuleConfig()
            }
        }
    }
}
