package com.edgeflow.core

import com.edgeflow.core.model.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutomationContinuationTest {
    @Test fun `resume does not repeat already dispatched steps`() {
        val steps = listOf(
            Action.Simple(ActionId.HOME),
            Action.Simple(ActionId.DISMISS_KEYGUARD),
            Action.Delay(200),
            Action.InjectTap(10, 20),
        )
        assertEquals(
            Action.Sequence(listOf(Action.Delay(200), Action.InjectTap(10, 20))),
            AutomationContinuation.remainingAfter(steps, 1, null),
        )
    }

    @Test fun `nested continuation precedes remaining outer steps`() {
        val outer = listOf(Action.Simple(ActionId.DISMISS_KEYGUARD), Action.InjectTap(1, 2))
        val inner = Action.Sequence(listOf(Action.Delay(500)))
        assertEquals(
            Action.Sequence(listOf(inner, Action.InjectTap(1, 2))),
            AutomationContinuation.remainingAfter(outer, 0, inner),
        )
    }

    @Test fun `nothing to resume when unlock is final action`() {
        assertNull(AutomationContinuation.remainingAfter(listOf(Action.Simple(ActionId.DISMISS_KEYGUARD)), 0, null))
    }
}
