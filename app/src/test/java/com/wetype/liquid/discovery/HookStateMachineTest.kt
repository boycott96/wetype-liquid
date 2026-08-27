package com.wetype.liquid.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookStateMachineTest {

    @Test
    fun testHookLifecycleTransitions() {
        val hookId = "Test_Lifecycle_Hook"

        // 1. Discovered
        HookDiagnostics.recordHookDiscovered(hookId, "TargetClass#test()", "UnitTesting")
        var detail = HookDiagnostics.getHookDetails().find { it.id == hookId }
        assertNotNull(detail)
        assertEquals(HookState.DISCOVERED, detail!!.state)
        assertEquals(0L, detail.hitCount.get())
        assertFalse(detail.isAttached)

        // 2. Installed
        HookDiagnostics.recordHookInstalled(hookId, "TargetClass#test()", "UnitTesting")
        detail = HookDiagnostics.getHookDetails().find { it.id == hookId }
        assertEquals(HookState.INSTALLED, detail!!.state)
        assertEquals(0L, detail.hitCount.get())
        assertFalse("Installed with 0 hits must NOT be attached", detail.isAttached)

        // 3. First Hit -> Attached
        HookDiagnostics.recordHookHit(hookId)
        detail = HookDiagnostics.getHookDetails().find { it.id == hookId }
        assertEquals(HookState.ATTACHED, detail!!.state)
        assertEquals(1L, detail.hitCount.get())
        assertTrue("Hit count > 0 must be attached", detail.isAttached)
        assertTrue("First hit timestamp must be recorded", detail.firstHitTimestamp > 0)

        // 4. Second Hit -> Still Attached with hitCount = 2
        HookDiagnostics.recordHookHit(hookId)
        assertEquals(2L, detail.hitCount.get())

        // 5. Unhooked
        HookDiagnostics.recordHookUnhooked(hookId)
        detail = HookDiagnostics.getHookDetails().find { it.id == hookId }
        assertEquals(HookState.UNHOOKED, detail!!.state)
    }

    @Test
    fun testHookFailureRecording() {
        val hookId = "Test_Failed_Hook"
        val exception = RuntimeException("NoSuchMethodException: Target method not found")

        HookDiagnostics.recordHookFailure(hookId, exception)
        val detail = HookDiagnostics.getHookDetails().find { it.id == hookId }
        assertNotNull(detail)
        assertEquals(HookState.INSTALL_FAILED, detail!!.state)
        assertEquals(exception.message, detail.lastError)
        assertFalse(detail.isAttached)

        val errors = HookDiagnostics.getRecentErrors()
        assertTrue(errors.any { it.contains("NoSuchMethodException") })
    }
}
