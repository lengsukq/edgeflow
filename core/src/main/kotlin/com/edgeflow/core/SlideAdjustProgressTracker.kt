package com.edgeflow.core

/** Quantizes a bound swipe into adjustment steps without retaining platform events. */
class SlideAdjustProgressTracker(
    private val gesture: Gesture,
    private val startX: Float,
    private val startY: Float,
    stepDistance: Float,
    maxSteps: Int = DEFAULT_MAX_STEPS,
) {

    private val stepDistance = stepDistance.takeIf { it > 0f } ?: DEFAULT_STEP_DISTANCE
    private val maxSteps = maxSteps.coerceAtLeast(1)
    private var appliedSteps = 0

    init {
        require(gesture in SWIPE_GESTURES) { "slide adjustment requires a swipe gesture" }
    }

    /** Returns the newly crossed step count and normalized indicator position. */
    fun update(x: Float, y: Float): SlideAdjustUpdate {
        val distance = when (gesture) {
            Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT -> x - startX
            Gesture.SWIPE_UP, Gesture.SWIPE_DOWN -> startY - y
            else -> 0f
        }
        val targetSteps = (distance / stepDistance).toInt().coerceIn(-maxSteps, maxSteps)
        val deltaSteps = targetSteps - appliedSteps
        appliedSteps = targetSteps
        val progress = (0.5f + targetSteps / (2f * maxSteps)).coerceIn(0f, 1f)
        return SlideAdjustUpdate(deltaSteps, progress)
    }

    private companion object {
        const val DEFAULT_MAX_STEPS = 16
        const val DEFAULT_STEP_DISTANCE = 48f
        val SWIPE_GESTURES = setOf(
            Gesture.SWIPE_LEFT,
            Gesture.SWIPE_RIGHT,
            Gesture.SWIPE_UP,
            Gesture.SWIPE_DOWN,
        )
    }
}

data class SlideAdjustUpdate(val deltaSteps: Int, val progress: Float)
