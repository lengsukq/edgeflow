package com.edgeflow.data

/**
 * This module explicitly declines onHotReloading: replacing the APK while
 * system_server keeps running cannot update its injected hook classes.
 * Compare boot wall-clock time with the package update time, allowing a
 * tolerance for timestamp rounding and Android's clock adjustments.
 */
object ModuleRestartPolicy {
    fun needsReboot(
        lastUpdateTimeMillis: Long,
        wallNowMillis: Long,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (lastUpdateTimeMillis <= 0 || wallNowMillis <= 0 || elapsedRealtimeMillis < 0) {
            return false
        }
        val estimatedBootMillis = wallNowMillis - elapsedRealtimeMillis
        return lastUpdateTimeMillis > estimatedBootMillis + 60_000L &&
            lastUpdateTimeMillis <= wallNowMillis + 60_000L
    }
}
