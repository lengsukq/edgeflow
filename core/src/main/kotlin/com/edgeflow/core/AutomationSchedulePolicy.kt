package com.edgeflow.core

import com.edgeflow.core.model.ScheduleEntry
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Device-local civil-time schedules. A spring-forward gap moves to the next
 * valid wall time; a fall-back overlap fires only once, at its first occurrence.
 * Lateness is bounded; missed jobs are not automatically replayed after hours.
 */
object AutomationSchedulePolicy {
    const val MAX_LATENESS_MILLIS = 5 * 60_000L

    fun nextRun(
        afterEpochMillis: Long,
        schedules: List<ScheduleEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val day = Instant.ofEpochMilli(afterEpochMillis).atZone(zone).toLocalDate()
        return schedules.asSequence().filter(::eligible).mapNotNull { schedule ->
            (0L..7L).asSequence().mapNotNull { offset ->
                val targetDay = day.plusDays(offset)
                if ((schedule.daysOfWeekMask and (1 shl (targetDay.dayOfWeek.value - 1))) == 0) return@mapNotNull null
                val occurrence = ZonedDateTime.ofLocal(
                    targetDay.atTime(schedule.hour, schedule.minute),
                    zone,
                    null,
                ).toInstant().toEpochMilli()
                occurrence.takeIf { it > afterEpochMillis }
            }.firstOrNull()
        }.minOrNull()
    }

    fun dueNow(
        schedule: ScheduleEntry,
        nowEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!eligible(schedule)) return false
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        if ((schedule.daysOfWeekMask and (1 shl (today.dayOfWeek.value - 1))) == 0) return false
        val target = ZonedDateTime.ofLocal(today.atTime(schedule.hour, schedule.minute), zone, null)
            .toInstant().toEpochMilli()
        return nowEpochMillis - target in 0L..MAX_LATENESS_MILLIS
    }

    private fun eligible(s: ScheduleEntry): Boolean =
        s.enabled && s.daysOfWeekMask != 0 && s.action.id != ActionId.NONE &&
            s.hour in 0..23 && s.minute in 0..59
}
