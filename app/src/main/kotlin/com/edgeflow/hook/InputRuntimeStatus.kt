package com.edgeflow.hook

import android.os.SystemClock
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.ActionId
import com.edgeflow.core.model.EdgeConfig
import java.util.concurrent.atomic.AtomicLong

/** Read-only diagnostics; never record touch coordinates or user-entered text. */
internal object InputRuntimeStatus {
    @Volatile var runtimeStage = "system-server-loaded"
    @Volatile var hookInstalled = false
    @Volatile var nativeBridgeBound = false
    @Volatile var nativeGateRequested = false
    @Volatile var configVersion = 0
    @Volatile var enabledZoneCount = 0
    @Volatile var boundEnabledActionCount = 0
    @Volatile var lastInputElapsedMs = 0L
    @Volatile var displayWidth = 0
    @Volatile var displayHeight = 0
    val edgeDownEvents = AtomicLong()
    val activeZoneDowns = AtomicLong()
    val keyguardBlockedDowns = AtomicLong()
    val volumeKeyEvents = AtomicLong()
    val volumeKeysConsumed = AtomicLong()
    val interceptedTouchCount = AtomicLong()
    val triggeredGestureCount = AtomicLong()
    /** Native injection acceptance is NOT confirmation of app-side effects. */
    val injectionAttempts = AtomicLong()
    val injectionAccepted = AtomicLong()
    val injectionRejected = AtomicLong()
    @Volatile var lastInjectionResult = -1

    fun injectionResult(code: Int) {
        injectionAttempts.incrementAndGet()
        lastInjectionResult = code
        if (code == 0) injectionAccepted.incrementAndGet()
        else injectionRejected.incrementAndGet()
    }

    fun configUpdated(config: EdgeConfig) {
        configVersion = config.version
        enabledZoneCount = EdgeZone.entries.count { !it.isFullEdge && config.gestures.isZoneEnabled(it) }
        boundEnabledActionCount = EdgeZone.entries.filter { !it.isFullEdge && config.gestures.isZoneEnabled(it) }
            .sumOf { zone -> Gesture.entries.count { gesture ->
                config.gestures.resolve(zone, gesture).id != ActionId.NONE
            } }
    }

    fun observedTouch() {
        interceptedTouchCount.incrementAndGet()
        lastInputElapsedMs = SystemClock.elapsedRealtime()
    }
}
