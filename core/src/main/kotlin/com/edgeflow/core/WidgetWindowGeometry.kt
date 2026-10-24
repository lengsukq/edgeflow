package com.edgeflow.core

import kotlin.math.roundToInt

/** Pure window sizing and placement rules for floating Android AppWidgets. */
object WidgetWindowGeometry {
    fun sizeDp(requestedDp: Int, preferredDp: Int, availableDp: Int, minDp: Int): Int {
        val upper = availableDp.coerceAtLeast(1)
        val lower = minDp.coerceAtMost(upper).coerceAtLeast(1)
        return (if (requestedDp > 0) requestedDp else preferredDp).coerceIn(lower, upper)
    }

    fun pixelPosition(freeSpacePx: Int, percent: Int): Int =
        (freeSpacePx.coerceAtLeast(0).toLong() * percent.coerceIn(0, 100) / 100L).toInt()

    fun positionPercent(freeSpacePx: Int, offsetPx: Int): Int =
        if (freeSpacePx <= 0) 0
        else (offsetPx.coerceIn(0, freeSpacePx).toDouble() / freeSpacePx * 100).roundToInt()
            .coerceIn(0, 100)

    fun timeoutMillis(seconds: Int): Long = seconds.coerceIn(0, 3600) * 1000L
}
