package com.edgeflow.core

/**
 * Device state, not an assumption that wakeUp() has dismissed a keyguard.
 * Classification is deliberately conservative: unknown security state blocks
 * automation instead of treating the screen as unlocked.
 */
data class UnlockSnapshot(
    val interactive: Boolean,
    val keyguardLocked: Boolean,
    val deviceLocked: Boolean,
    val secure: Boolean,
)

enum class UnlockDecision {
    /** Both the keyguard and the device lock are confirmed gone. */
    UNLOCKED,
    /** Wake the display, then obtain a fresh snapshot before proceeding. */
    WAKE_AND_RECHECK,
    /** Let the OS dismiss a non-secure keyguard from a visible Activity. */
    REQUEST_DISMISSAL,
    /** Android must handle secure device authentication; no automated bypass. */
    REQUIRE_AUTHENTICATION,
}

object UnlockPolicy {
    fun classify(snapshot: UnlockSnapshot): UnlockDecision = when {
        !snapshot.interactive -> UnlockDecision.WAKE_AND_RECHECK
        !snapshot.keyguardLocked && !snapshot.deviceLocked -> UnlockDecision.UNLOCKED
        snapshot.secure || snapshot.deviceLocked -> UnlockDecision.REQUIRE_AUTHENTICATION
        else -> UnlockDecision.REQUEST_DISMISSAL
    }

    /** A requested dismissal is not proof of success; only a new snapshot is. */
    fun confirmedUnlocked(snapshot: UnlockSnapshot): Boolean =
        snapshot.interactive && !snapshot.keyguardLocked && !snapshot.deviceLocked
}
