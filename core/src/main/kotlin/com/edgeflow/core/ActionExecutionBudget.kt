package com.edgeflow.core

/** Bounds a single automation run, including nested/repeated actions. */
class ActionExecutionBudget(
    private val startedAtMillis: Long,
    private val maxDurationMillis: Long = 120_000L,
    private val maxSteps: Int = 2_000,
) {
    init { require(maxDurationMillis > 0 && maxSteps > 0) }
    private var steps = 0

    fun canContinue(nowMillis: Long): Boolean =
        nowMillis - startedAtMillis < maxDurationMillis && steps < maxSteps

    fun tryEnterStep(nowMillis: Long): Boolean {
        if (!canContinue(nowMillis)) return false
        steps++
        return true
    }

    fun remainingMillis(nowMillis: Long): Long =
        (maxDurationMillis - (nowMillis - startedAtMillis)).coerceAtLeast(0L)
}
