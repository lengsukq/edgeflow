package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionExecutionBudgetTest {
    @Test fun `step budget prevents infinite repeats`() {
        val budget = ActionExecutionBudget(1_000L, maxSteps = 3)
        repeat(3) { assertTrue(budget.tryEnterStep(1_000L)) }
        assertFalse(budget.tryEnterStep(1_000L))
        assertFalse(budget.canContinue(1_000L))
    }
    @Test fun `timeout applies across nested actions`() {
        val budget = ActionExecutionBudget(2_000L, maxDurationMillis = 200)
        assertTrue(budget.tryEnterStep(2_000L))
        assertEquals(1L, budget.remainingMillis(2_199L))
        assertFalse(budget.canContinue(2_200L))
        assertFalse(budget.tryEnterStep(2_201L))
    }
    @Test fun `remaining duration clamps at zero`() {
        assertEquals(0L, ActionExecutionBudget(100L, maxDurationMillis = 50).remainingMillis(500L))
    }
}
