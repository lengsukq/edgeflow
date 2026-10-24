package com.edgeflow.core

import com.edgeflow.core.model.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomationRunLogTest {
    @Test fun `log distinguishes dispatched steps from completed app side effects`() {
        val log = AutomationRunLog()
        log.begin("manual")
        log.dispatched(ActionId.HOME)
        log.finish(false)
        assertEquals("DISPATCHED", log.snapshot().state)
        assertEquals(1, log.snapshot().dispatchedSteps)
        assertEquals(0, log.snapshot().failedSteps)
    }

    @Test fun `failure is observable without recording command content`() {
        val log = AutomationRunLog()
        log.begin("shell")
        log.dispatched(ActionId.SHELL)
        log.fail(ActionId.SHELL, "timeout")
        log.finish(false)
        assertEquals("FAILED", log.snapshot().state)
        assertTrue(log.snapshot().recentSteps.contains("timeout"))
    }

    @Test fun `only eight recent step records remain`() {
        val log = AutomationRunLog()
        log.begin("test")
        repeat(20) { log.dispatched(it + 1) }
        assertEquals(8, log.snapshot().recentSteps.split(" | ").size)
    }

    @Test fun `locked device rejects effects but permits standard unlock requests`() {
        assertFalse(AutomationLockPolicy.mayDispatch(Action.InjectTap(1, 2), true))
        assertFalse(AutomationLockPolicy.mayDispatch(Action.Shell("whoami"), true))
        assertTrue(AutomationLockPolicy.mayDispatch(Action.Simple(ActionId.DISMISS_KEYGUARD), true))
        assertTrue(AutomationLockPolicy.mayDispatch(Action.Sequence(listOf(Action.None)), true))
        assertTrue(AutomationLockPolicy.mayDispatch(Action.InjectTap(1, 2), false))
    }
}
