package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.ScheduleEntry
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomationSchedulePolicyTest {
    private val ny = ZoneId.of("America/New_York")
    private fun job(hour: Int = 9, minute: Int = 30, mask: Int = 0x7f) =
        ScheduleEntry(id = 1, hour = hour, minute = minute, daysOfWeekMask = mask, action = Action.Simple(ActionId.HOME))

    @Test fun `next wall-time occurrence respects timezone and weekday mask`() {
        val saturday = Instant.parse("2026-10-24T10:00:00Z").toEpochMilli()
        val mondayOnly = job(mask = 1)
        assertEquals(
            Instant.parse("2026-10-26T13:30:00Z").toEpochMilli(),
            AutomationSchedulePolicy.nextRun(saturday, listOf(mondayOnly), ny),
        )
    }

    @Test fun `a due alarm permits five minutes of delivery delay but no replay hours later`() {
        val target = Instant.parse("2026-10-24T13:30:00Z").toEpochMilli()
        assertTrue(AutomationSchedulePolicy.dueNow(job(), target + 120_000, ny))
        assertFalse(AutomationSchedulePolicy.dueNow(job(), target + 10 * 60_000, ny))
        assertFalse(AutomationSchedulePolicy.dueNow(job(), target - 1000, ny))
    }

    @Test fun `disabled or unbound schedule creates no alarm`() {
        val after = Instant.parse("2026-10-24T10:00:00Z").toEpochMilli()
        assertEquals(null, AutomationSchedulePolicy.nextRun(after, listOf(job().copy(enabled = false)), ny))
        assertEquals(null, AutomationSchedulePolicy.nextRun(after, listOf(job().copy(action = Action.None)), ny))
    }

    @Test fun `spring forward gap rolls forward and fall back overlap runs only at first occurrence`() {
        val beforeSpring = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
            AutomationSchedulePolicy.nextRun(beforeSpring, listOf(job(hour = 2)), ny),
        )
        val beforeFall = Instant.parse("2026-11-01T04:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
            AutomationSchedulePolicy.nextRun(beforeFall, listOf(job(hour = 1)), ny),
        )
        assertFalse(AutomationSchedulePolicy.dueNow(
            job(hour = 1),
            Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(),
            ny,
        ))
    }
}
