package com.edgeflow.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleRestartPolicyTest {
    @Test fun apkInstalledHoursAfterBootNeedsReboot() {
        val now = 1_800_000_000_000L
        assertTrue(ModuleRestartPolicy.needsReboot(
            lastUpdateTimeMillis = now - 5 * 60_000L,
            wallNowMillis = now,
            elapsedRealtimeMillis = 14 * 60 * 60_000L,
        ))
    }

    @Test fun deviceRebootedSinceApkInstallation() {
        val now = 1_800_000_000_000L
        assertFalse(ModuleRestartPolicy.needsReboot(
            lastUpdateTimeMillis = now - 2 * 60 * 60_000L,
            wallNowMillis = now,
            elapsedRealtimeMillis = 20 * 60_000L,
        ))
    }

    @Test fun clockSkewAndUnknownTimestampsDoNotForceRestart() {
        assertFalse(ModuleRestartPolicy.needsReboot(0L, 50L, 40L))
        assertFalse(ModuleRestartPolicy.needsReboot(9_000L, 8_000L, 7_000L))
        assertFalse(ModuleRestartPolicy.needsReboot(120_000L, 120_000L, 30_000L))
    }
}
