package com.edgeflow.service

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class PieMenuGeometryTest {

    @Test
    fun `each rendered slot center maps to its matching slot across both rings`() {
        val centerX = 150f
        val centerY = 150f
        val radius = 100f
        val ringRatios = listOf(
            (PieMenuGeometry.INNER_RING_INNER_RATIO + PieMenuGeometry.INNER_RING_OUTER_RATIO) / 2f,
            (PieMenuGeometry.OUTER_RING_INNER_RATIO + PieMenuGeometry.OUTER_RING_OUTER_RATIO) / 2f,
        )

        ringRatios.forEachIndexed { ringIndex, ringRatio ->
            repeat(PieMenuGeometry.SLOTS_PER_RING) { slotInRing ->
                val angle = PieMenuGeometry.slotCenterAngleRadians(slotInRing)
                val labelRadius = radius * ringRatio
                val x = centerX + cos(angle).toFloat() * labelRadius
                val y = centerY + sin(angle).toFloat() * labelRadius
                val expectedSlot = ringIndex * PieMenuGeometry.SLOTS_PER_RING + slotInRing

                assertEquals(
                    expectedSlot,
                    PieMenuGeometry.slotAt(x, y, centerX, centerY, radius),
                    "ring=$ringIndex, slot=$slotInRing",
                )
            }
        }
    }

    @Test
    fun `the center and radial gaps do not hit a Pie slot`() {
        val centerX = 150f
        val centerY = 150f
        val radius = 100f

        assertEquals(-1, PieMenuGeometry.slotAt(centerX, centerY, centerX, centerY, radius))
        assertEquals(
            -1,
            PieMenuGeometry.slotAt(centerX + radius * 0.635f, centerY, centerX, centerY, radius),
        )
        assertEquals(-1, PieMenuGeometry.slotAt(centerX, centerY, centerX, centerY, 0f))
    }
}
