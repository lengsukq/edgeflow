package com.edgeflow.hook

/** A latest-value mailbox: many offers before one drain collapse to the newest frame. */
internal class LatestValueCoalescer<T> {
    private var latest: T? = null
    private var drainPending = false

    @Synchronized
    fun offer(value: T): Boolean {
        latest = value
        if (drainPending) return false
        drainPending = true
        return true
    }

    @Synchronized
    fun take(): T? {
        if (!drainPending) return null
        val value = latest
        latest = null
        drainPending = false
        return value
    }

    @Synchronized
    fun hasPending(): Boolean = drainPending
}

internal data class GesturePointerPoint(val x: Float, val y: Float)

/** Pure geometry shared by the overlay and its unit tests. */
internal object GesturePointerGeometry {
    fun clampPoint(x: Float, y: Float, width: Int, height: Int): GesturePointerPoint {
        val maxX = (width - 1).coerceAtLeast(0).toFloat()
        val maxY = (height - 1).coerceAtLeast(0).toFloat()
        return GesturePointerPoint(
            x = (if (x.isFinite()) x else 0f).coerceIn(0f, maxX),
            y = (if (y.isFinite()) y else 0f).coerceIn(0f, maxY),
        )
    }

    fun radiusPx(density: Float, scalePercent: Int): Float {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val scale = scalePercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)
        // A 25% setting must not shrink the pointer below the minimum visible
        // radius; the renderer and existing test expect at least 6 dp.
        return (BASE_RADIUS_DP * safeDensity * scale / 100f).coerceAtLeast(6f * safeDensity)
    }

    const val MIN_SCALE_PERCENT = 25
    const val MAX_SCALE_PERCENT = 400
    private const val BASE_RADIUS_DP = 12f
}
