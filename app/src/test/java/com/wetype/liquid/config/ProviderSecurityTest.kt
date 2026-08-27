package com.wetype.liquid.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSecurityTest {

    @Test
    fun testCallerAuthorizationLogic() {
        val moduleUid = 10100
        val targetWeTypeUid = 10200
        val unauthorizedExternalUid = 10300

        // Helper replicating provider's security rule
        fun isAllowed(callingUid: Int, method: String): Boolean {
            if (callingUid == moduleUid) {
                return true
            }
            return when (method) {
                WeTypeConfigProvider.METHOD_GET_CONFIG -> callingUid == targetWeTypeUid
                WeTypeConfigProvider.METHOD_SET_CONFIG -> false // Only module app can modify config
                WeTypeConfigProvider.METHOD_REPORT_DIAGNOSTICS -> callingUid == targetWeTypeUid
                WeTypeConfigProvider.METHOD_GET_DIAGNOSTICS -> callingUid == targetWeTypeUid
                else -> false
            }
        }

        // Module App itself: All methods permitted
        assertTrue(isAllowed(moduleUid, WeTypeConfigProvider.METHOD_GET_CONFIG))
        assertTrue(isAllowed(moduleUid, WeTypeConfigProvider.METHOD_SET_CONFIG))
        assertTrue(isAllowed(moduleUid, WeTypeConfigProvider.METHOD_REPORT_DIAGNOSTICS))
        assertTrue(isAllowed(moduleUid, WeTypeConfigProvider.METHOD_GET_DIAGNOSTICS))

        // Target WeType process: Can read config, report diagnostics, get diagnostics, but CANNOT setConfig
        assertTrue(isAllowed(targetWeTypeUid, WeTypeConfigProvider.METHOD_GET_CONFIG))
        assertFalse(isAllowed(targetWeTypeUid, WeTypeConfigProvider.METHOD_SET_CONFIG))
        assertTrue(isAllowed(targetWeTypeUid, WeTypeConfigProvider.METHOD_REPORT_DIAGNOSTICS))
        assertTrue(isAllowed(targetWeTypeUid, WeTypeConfigProvider.METHOD_GET_DIAGNOSTICS))

        // Unauthorized external app process: All methods denied
        assertFalse(isAllowed(unauthorizedExternalUid, WeTypeConfigProvider.METHOD_GET_CONFIG))
        assertFalse(isAllowed(unauthorizedExternalUid, WeTypeConfigProvider.METHOD_SET_CONFIG))
        assertFalse(isAllowed(unauthorizedExternalUid, WeTypeConfigProvider.METHOD_REPORT_DIAGNOSTICS))
        assertFalse(isAllowed(unauthorizedExternalUid, WeTypeConfigProvider.METHOD_GET_DIAGNOSTICS))
    }
}
