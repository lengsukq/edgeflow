package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockPolicyTest {
    @Test fun wakeIsNotAnUnlock() {
        val locked = UnlockSnapshot(interactive = false, keyguardLocked = true, deviceLocked = true, secure = true)
        assertEquals(UnlockDecision.WAKE_AND_RECHECK, UnlockPolicy.classify(locked))
        assertFalse(UnlockPolicy.confirmedUnlocked(locked))
    }

    @Test fun insecureSwipeLockRequestsPlatformDismissal() {
        val snapshot = UnlockSnapshot(true, true, false, false)
        assertEquals(UnlockDecision.REQUEST_DISMISSAL, UnlockPolicy.classify(snapshot))
        assertFalse(UnlockPolicy.confirmedUnlocked(snapshot))
    }

    @Test fun secureKeyguardCannotBeMarkedUnlockedUntilAuthentication() {
        val snapshot = UnlockSnapshot(true, true, true, true)
        assertEquals(UnlockDecision.REQUIRE_AUTHENTICATION, UnlockPolicy.classify(snapshot))
        assertFalse(UnlockPolicy.confirmedUnlocked(snapshot))
    }

    @Test fun unlockedStateRequiresBothLocksToBeGone() {
        val deviceStillLocked = UnlockSnapshot(true, false, true, true)
        assertEquals(UnlockDecision.REQUIRE_AUTHENTICATION, UnlockPolicy.classify(deviceStillLocked))
        assertFalse(UnlockPolicy.confirmedUnlocked(deviceStillLocked))
        val unlocked = UnlockSnapshot(true, false, false, true)
        assertEquals(UnlockDecision.UNLOCKED, UnlockPolicy.classify(unlocked))
        assertTrue(UnlockPolicy.confirmedUnlocked(unlocked))
    }
}
