package com.edgeflow.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GesturePointerSupportTest {

    @Test
    fun `coalescer retains only the newest value until drained`() {
        val mailbox = LatestValueCoalescer<Int>()

        assertTrue(mailbox.offer(1))
        assertFalse(mailbox.offer(2))
        assertFalse(mailbox.offer(3))
        assertTrue(mailbox.hasPending())
        assertEquals(3, mailbox.take())
        assertFalse(mailbox.hasPending())
        assertEquals(null, mailbox.take())
        assertTrue(mailbox.offer(4))
        assertEquals(4, mailbox.take())
    }

    @Test
    fun `point coordinates are clamped and non-finite values become zero`() {
        assertEquals(
            GesturePointerPoint(0f, 0f),
            GesturePointerGeometry.clampPoint(Float.NaN, Float.NEGATIVE_INFINITY, 1080, 2400),
        )
        assertEquals(
            GesturePointerPoint(1079f, 2399f),
            GesturePointerGeometry.clampPoint(5000f, 5000f, 1080, 2400),
        )
        assertEquals(
            GesturePointerPoint(0f, 0f),
            GesturePointerGeometry.clampPoint(-3f, -8f, 0, 0),
        )
    }

    @Test
    fun `radius applies density and clamps configured scale`() {
        assertEquals(24f, GesturePointerGeometry.radiusPx(density = 2f, scalePercent = 100))
        assertEquals(6f, GesturePointerGeometry.radiusPx(density = 1f, scalePercent = 0))
        assertEquals(48f, GesturePointerGeometry.radiusPx(density = 1f, scalePercent = 1000))
        assertEquals(12f, GesturePointerGeometry.radiusPx(density = Float.NaN, scalePercent = 100))
    }
}
