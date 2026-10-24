package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrightnessStepsTest {

    @Test
    fun `increase advances to the next perceptual level`() {
        val next = BrightnessSteps.nextValue(128, minimum = 1, maximum = 255, increase = true)

        assertTrue(next != null && next > 128)
    }

    @Test
    fun `decrease retreats to the previous perceptual level`() {
        val next = BrightnessSteps.nextValue(128, minimum = 1, maximum = 255, increase = false)

        assertTrue(next != null && next < 128)
    }

    @Test
    fun `brightness does not move beyond configured bounds`() {
        assertNull(BrightnessSteps.nextValue(255, minimum = 1, maximum = 255, increase = true))
        assertNull(BrightnessSteps.nextValue(1, minimum = 1, maximum = 255, increase = false))
    }

    @Test
    fun `exact brightness level moves to its adjacent step`() {
        val previous = BrightnessSteps.nextValue(255, minimum = 1, maximum = 255, increase = false)
        val next = BrightnessSteps.nextValue(1, minimum = 1, maximum = 255, increase = true)

        assertEquals(224, previous)
        assertEquals(2, next)
    }
}
