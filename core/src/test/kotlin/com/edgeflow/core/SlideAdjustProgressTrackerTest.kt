package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SlideAdjustProgressTrackerTest {

    @Test
    fun `swipe right produces positive adjustment steps`() {
        val tracker = SlideAdjustProgressTracker(Gesture.SWIPE_RIGHT, 10f, 20f, stepDistance = 12f)

        val update = tracker.update(x = 35f, y = 20f)

        assertEquals(2, update.deltaSteps)
        assertEquals(0.5625f, update.progress)
    }

    @Test
    fun `swipe up produces positive steps and reverses when dragged back`() {
        val tracker = SlideAdjustProgressTracker(Gesture.SWIPE_UP, 0f, 100f, stepDistance = 10f)

        assertEquals(3, tracker.update(0f, 65f).deltaSteps)
        assertEquals(-2, tracker.update(0f, 85f).deltaSteps)
    }

    @Test
    fun `movement is capped to a finite adjustment range`() {
        val tracker = SlideAdjustProgressTracker(Gesture.SWIPE_RIGHT, 0f, 0f, stepDistance = 1f)

        val update = tracker.update(x = 10_000f, y = 0f)

        assertEquals(16, update.deltaSteps)
        assertEquals(1f, update.progress)
        assertEquals(0, tracker.update(x = 10_000f, y = 0f).deltaSteps)
    }

    @Test
    fun `non-swipe gesture cannot start a slide adjustment tracker`() {
        assertFailsWith<IllegalArgumentException> {
            SlideAdjustProgressTracker(Gesture.TAP, 0f, 0f, stepDistance = 10f)
        }
    }
}
