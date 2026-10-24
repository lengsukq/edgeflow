package com.edgeflow.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleStatusGuidanceTest {
    private fun state(confirmed: Boolean, scopePresent: Boolean) = ModuleStatus(
        frameworkDetected = confirmed,
        frameworkName = if (confirmed) "Xposed" else null,
        apiVersion = if (confirmed) 102 else null,
        scopeConfigured = scopePresent,
        androidRelease = "16",
        androidSdk = 36,
    )

    @Test fun aMissingReplyDoesNotClaimFrameworkIsAbsent() {
        assertEquals(Guidance.SYSTEM_HOOK_UNCONFIRMED, state(false, true).guidance)
    }

    @Test fun aLiveSystemReplyConfirmsRuntime() {
        assertEquals(Guidance.READY, state(true, true).guidance)
    }

    @Test fun aBadApkMetadataMustBeRepairedEvenIfPreviouslyConfirmed() {
        assertEquals(Guidance.MODULE_PACKAGE_INVALID, state(true, false).guidance)
    }
}
