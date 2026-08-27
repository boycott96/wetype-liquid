package com.wetype.liquid.ui

import com.wetype.liquid.discovery.HookDiagnostics
import com.wetype.liquid.discovery.HookState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDiagnosticsTest {

    @Before
    fun setUp() {
        HookDiagnostics.resetForTest()
        HookDiagnostics.frameworkName = "Vector Framework"
        HookDiagnostics.frameworkVersion = "1.0.0"
        HookDiagnostics.frameworkApi = 102
        HookDiagnostics.isModernLibXposed = true
        HookDiagnostics.wetypeVersionName = "1.2.3"
        HookDiagnostics.wetypeVersionCode = 123456L
        HookDiagnostics.activeBlurBackend = "Window Behind Blur (Hardware Accelerate)"
    }

    @Test
    fun testJsonSerializationAndParsing() {
        HookDiagnostics.recordHookDiscovered("TestHook_1", "com.test.Class#method()", "TestReason")
        HookDiagnostics.recordHookInstalled("TestHook_1", "com.test.Class#method()", "TestReason")
        HookDiagnostics.recordHookHit("TestHook_1")

        HookDiagnostics.recordDiscoveredClass("KeyboardView", "com.tencent.wetype.KeyboardView", 85, listOf("ViewGroupSubclass", "DrawsCanvas"))

        val json = HookDiagnostics.generateReportJson()
        assertNotNull(json)
        assertTrue(json.contains("Vector Framework"))
        assertTrue(json.contains("com.tencent.wetype"))

        val parsed = RemoteDiagnosticsUiState.parseFromJson(json, isActiveHeartbeat = true)
        assertTrue(parsed.hasReport)
        assertEquals(RemoteStatus.ACTIVE, parsed.status)
        assertEquals("Vector Framework", parsed.frameworkName)
        assertEquals(102, parsed.frameworkApi)
        assertTrue(parsed.isModernLibXposed)
        assertEquals("1.2.3", parsed.wetypeVersionName)
        assertEquals(123456L, parsed.wetypeVersionCode)

        assertEquals(1, parsed.discoveredClasses.size)
        assertEquals("com.tencent.wetype.KeyboardView", parsed.discoveredClasses[0].className)
        assertEquals(85, parsed.discoveredClasses[0].score)

        assertEquals(1, parsed.hooks.size)
        assertEquals("TestHook_1", parsed.hooks[0].id)
        assertEquals("ATTACHED", parsed.hooks[0].state)
        assertEquals(1L, parsed.hooks[0].hitCount)
        assertTrue(parsed.hooks[0].isAttached)
    }

    @Test
    fun testStaleStatusWhenHeartbeatIsOld() {
        // Report timestamp from 2 minutes ago
        val oldTimestamp = System.currentTimeMillis() - 120_000L
        val fakeOldJson = """
        {
            "reportTimestamp": $oldTimestamp,
            "targetApp": { "versionName": "1.0.0" }
        }
        """.trimIndent()

        val parsed = RemoteDiagnosticsUiState.parseFromJson(fakeOldJson, isActiveHeartbeat = false)
        assertEquals(RemoteStatus.STALE, parsed.status)
    }

    @Test
    fun testEmptyOrNullJsonFallback() {
        val emptyState = RemoteDiagnosticsUiState.parseFromJson(null, false)
        assertFalse(emptyState.hasReport)
        assertEquals(RemoteStatus.NO_REPORT, emptyState.status)

        val corruptedState = RemoteDiagnosticsUiState.parseFromJson("{corrupted}", false)
        assertFalse(corruptedState.hasReport)
        assertEquals(RemoteStatus.NO_REPORT, corruptedState.status)
    }
}
