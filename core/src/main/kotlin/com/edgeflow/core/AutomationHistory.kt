package com.edgeflow.core

import kotlinx.serialization.Serializable

/** A bounded, content-free history of automation execution outcomes. */
@Serializable
data class AutomationHistoryRecord(
    val id: String,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val origin: String,
    val state: String,
    val dispatchedSteps: Int,
    val failedSteps: Int,
    val recentSteps: String,
)

object AutomationHistory {
    const val MAX_RECORDS = 80

    fun retainWith(previous: List<AutomationHistoryRecord>, next: AutomationHistoryRecord): List<AutomationHistoryRecord> =
        (listOf(next) + previous.filterNot { it.id == next.id }).take(MAX_RECORDS)

    fun sanitize(record: AutomationHistoryRecord): AutomationHistoryRecord =
        record.copy(
            id = record.id.take(80),
            origin = record.origin.take(72),
            state = record.state.takeIf { it in setOf("DISPATCHED", "STOPPED", "FAILED") } ?: "FAILED",
            durationMillis = record.durationMillis.coerceIn(0L, 24 * 60 * 60 * 1000L),
            dispatchedSteps = record.dispatchedSteps.coerceIn(0, 2_000),
            failedSteps = record.failedSteps.coerceIn(0, 2_000),
            recentSteps = record.recentSteps.take(920),
        )
}
