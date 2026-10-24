package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleDispatchGateTest {
    @Test fun `busy worker does not mark schedule as delivered`() {
        var commits = 0
        assertFalse(ScheduleDispatchGate.acceptThenMark(false, { false }, { commits++ }))
        assertEquals(0, commits)
        assertTrue(ScheduleDispatchGate.acceptThenMark(false, { true }, { commits++ }))
        assertEquals(1, commits)
    }

    @Test fun `previously delivered schedule never dispatches twice`() {
        var attempts = 0
        assertFalse(ScheduleDispatchGate.acceptThenMark(true, { attempts++; true }, {}))
        assertEquals(0, attempts)
    }
}
