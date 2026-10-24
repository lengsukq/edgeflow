package com.edgeflow.core

/**
 * Bounded execution diagnostics. "Dispatched" is deliberately different from
 * "succeeded": native injection acceptance cannot prove the target app acted.
 * Never store task parameters, shell commands, typed text or touch coordinates.
 */
class AutomationRunLog {
    data class Snapshot(
        val state: String,
        val origin: String,
        val dispatchedSteps: Int,
        val failedSteps: Int,
        val recentSteps: String,
    )

    private var state = "IDLE"
    private var origin = ""
    private var steps = 0
    private var failures = 0
    private val recent = ArrayDeque<String>()

    @Synchronized fun begin(source: String) {
        state = "RUNNING"
        origin = source.take(72)
        steps = 0
        failures = 0
        recent.clear()
    }

    @Synchronized fun dispatched(actionId: Int) {
        steps++
        append("#$steps action $actionId dispatched")
    }

    @Synchronized fun fail(actionId: Int, reason: String) {
        failures++
        state = "FAILED"
        append("action $actionId failed: " + reason.replace(Regex("[\r\n]"), " ").take(88))
    }

    @Synchronized fun finish(stopped: Boolean) {
        state = when {
            failures > 0 -> "FAILED"
            stopped -> "STOPPED"
            else -> "DISPATCHED"
        }
    }

    @Synchronized fun snapshot(): Snapshot =
        Snapshot(state, origin, steps, failures, recent.joinToString(" | "))

    private fun append(item: String) {
        if (recent.size == 8) recent.removeFirst()
        recent.addLast(item)
    }
}
