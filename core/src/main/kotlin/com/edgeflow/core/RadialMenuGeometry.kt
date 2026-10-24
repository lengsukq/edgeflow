package com.edgeflow.core

import kotlin.math.atan2
import kotlin.math.hypot

/** Pure hit testing; same slot indices drive rendering, configuration and dispatch. */
object RadialMenuGeometry {
    const val SECTORS = 6
    const val START_ANGLE = -120.0
    private val BANDS = arrayOf(
        0.24f..0.43f,
        0.48f..0.69f,
        0.75f..0.98f,
    )

    fun band(ring: Int, rings: Int): ClosedFloatingPointRange<Float> {
        require(rings in 2..3 && ring in 0 until rings)
        return if (rings == 2) {
            if (ring == 0) 0.29f..0.57f else 0.64f..0.95f
        } else BANDS[ring]
    }

    fun slotAt(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float, rings: Int): Int {
        if (rings !in 2..3 || !radius.isFinite() || radius <= 0f || !x.isFinite() || !y.isFinite()) return -1
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx, dy) / radius
        val ring = (0 until rings).firstOrNull { distance in band(it, rings) } ?: return -1
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val relative = ((angle - START_ANGLE) % 360.0 + 360.0) % 360.0
        return ring * SECTORS + (relative / (360.0 / SECTORS)).toInt().coerceIn(0, SECTORS - 1)
    }

    fun centerAngle(slot: Int): Double = Math.toRadians(START_ANGLE + (slot % SECTORS + 0.5) * 60.0)
}
