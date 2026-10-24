package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomationHistoryTest {
    private fun sample(id: String) = AutomationHistoryRecord(id, 1000, 25, "gesture", "DISPATCHED", 3, 0, "#1 action 3")

    @Test fun `newest records are first with bounded retention`() {
        var records = emptyList<AutomationHistoryRecord>()
        repeat(120) { records = AutomationHistory.retainWith(records, sample(it.toString())) }
        assertEquals(80, records.size)
        assertEquals("119", records.first().id)
        assertFalse(records.any { it.id == "0" })
    }

    @Test fun `duplicate delivery does not duplicate a run`() {
        val result = AutomationHistory.retainWith(listOf(sample("a")), sample("a"))
        assertEquals(1, result.size)
    }

    @Test fun `unsupported states and overlong metadata are normalized`() {
        val record = AutomationHistory.sanitize(sample("a").copy(
            origin = "x".repeat(400), state = "CUSTOM", recentSteps = "e".repeat(3000), failedSteps = -1,
        ))
        assertEquals(72, record.origin.length)
        assertEquals("FAILED", record.state)
        assertEquals(920, record.recentSteps.length)
        assertEquals(0, record.failedSteps)
    }
}
