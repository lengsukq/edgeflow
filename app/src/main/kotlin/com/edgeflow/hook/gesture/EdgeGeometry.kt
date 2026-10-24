package com.edgeflow.hook.gesture

import com.edgeflow.core.EdgeZone
import com.edgeflow.core.model.AppSettings

/**
 * Hit-tests a touch point against the configured edge strips.
 *
 * ## Units
 *
 * All distances are expressed in inches and converted with the display's
 * physical density, so a strip covers the same physical area on every device.
 * Working in pixels directly would make the zone unusably thin on a high-density
 * screen and comically large on a low-density one.
 *
 * ## Immutability
 *
 * [withDisplaySize] returns a new instance rather than mutating. The input thread
 * must never observe a half-updated geometry while the user is dragging a slider.
 *
 * ## No Android types
 *
 * Deliberately free of Android dependencies so the geometry can be tested on the
 * JVM. Callers supply the display metrics.
 */
internal class EdgeGeometry(
    private val widthPx: Int,
    private val heightPx: Int,
    private val xdpi: Float,
    private val ydpi: Float,
    private val settings: AppSettings,
) {

    /** Thickness of the left and right strips, in pixels. */
    private val sideThicknessPx: Float = settings.edgeWidthInches * xdpi

    /** Thickness of the top and bottom strips, in pixels. */
    private val endThicknessPx: Float = settings.edgeHeightInches * xdpi

    /** Space reserved at each end of a strip so corners stay reachable by the system. */
    private val cornerReservedPx: Float = settings.edgeCornerReservedInches * xdpi

    /** Movement, in pixels, above which a pointer counts as swiping rather than resting. */
    val swipeThresholdPx: Float = settings.swipeThresholdInches * xdpi

    /** Squared swipe threshold; comparing squares avoids a square root per event. */
    val swipeThresholdSquared: Float = swipeThresholdPx * swipeThresholdPx

    /**
     * Squared movement below which a gesture is treated as a stationary tap.
     *
     * Derived from the swipe threshold rather than configured separately, so the
     * two cannot drift apart when the user tunes one of them.
     */
    val tapSlopSquared: Float = swipeThresholdSquared * TAP_SLOP_FRACTION * TAP_SLOP_FRACTION

    val screenWidth: Int get() = widthPx
    val screenHeight: Int get() = heightPx

    /**
     * Resolves which edge zone contains [x],[y].
     *
     * Order matters. A point near a corner belongs to two strips; sides win over
     * top and bottom, and the corner reserves mean a point inside the reserved
     * band falls through to the perpendicular strip. That is what lets a user
     * still pull down the notification shade from a screen corner.
     *
     * @return the matching [EdgeZone], or `null` when the point is in no strip
     */
    fun hitTest(x: Float, y: Float): EdgeZone? {
        val withinVerticalSpan = y > cornerReservedPx && y < heightPx - cornerReservedPx
        val withinHorizontalSpan = x > cornerReservedPx && x < widthPx - cornerReservedPx

        if (x <= sideThicknessPx && withinVerticalSpan) {
            return EdgeZone.of(segmentIndex(y, cornerReservedPx, heightPx) + EdgeZone.LEFT_TOP.id)
        }
        if (x >= widthPx - sideThicknessPx && withinVerticalSpan) {
            return EdgeZone.of(segmentIndex(y, cornerReservedPx, heightPx) + EdgeZone.RIGHT_TOP.id)
        }
        if (y <= endThicknessPx && withinHorizontalSpan) {
            return EdgeZone.of(segmentIndex(x, cornerReservedPx, widthPx) + EdgeZone.TOP_LEFT.id)
        }
        if (y >= heightPx - endThicknessPx && withinHorizontalSpan) {
            return EdgeZone.of(segmentIndex(x, cornerReservedPx, widthPx) + EdgeZone.BOTTOM_LEFT.id)
        }
        return null
    }

    /**
     * Splits the usable length of a strip into three equal parts.
     *
     * @return `0` for the leading third, `1` for the middle, `2` for the trailing third
     */
    private fun segmentIndex(position: Float, reserved: Float, total: Int): Int {
        val usable = total - reserved * 2f
        if (usable <= 0f) return 1
        val offset = position - reserved
        val third = usable / 3f
        return when {
            offset < third -> 0
            offset > third * 2f -> 2
            else -> 1
        }
    }

    /**
     * @return this instance when the size is unchanged, so callers can rebuild
     *         on every configuration change without allocating needlessly
     */
    fun withDisplaySize(width: Int, height: Int): EdgeGeometry =
        if (width == widthPx && height == heightPx) this
        else EdgeGeometry(width, height, xdpi, ydpi, settings)

    /** Whether this geometry was built from the same settings. */
    fun hasSettings(other: AppSettings): Boolean = settings == other

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EdgeGeometry) return false
        return widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            xdpi == other.xdpi &&
            ydpi == other.ydpi &&
            settings == other.settings
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + xdpi.hashCode()
        result = 31 * result + ydpi.hashCode()
        result = 31 * result + settings.hashCode()
        return result
    }

    companion object {
        /** A tap may drift this fraction of the swipe threshold before it becomes a swipe. */
        private const val TAP_SLOP_FRACTION = 0.35f

        /**
         * Builds geometry from display metrics.
         *
         * A density of zero or less is reported by some emulators and by folding
         * devices mid-fold; falling back to the density bucket keeps the module
         * usable instead of producing a zero-thickness strip.
         */
        fun of(
            widthPx: Int,
            heightPx: Int,
            xdpi: Float,
            ydpi: Float,
            densityDpi: Int,
            settings: AppSettings,
        ): EdgeGeometry = EdgeGeometry(
            widthPx = widthPx,
            heightPx = heightPx,
            xdpi = xdpi.takeIf { it > 0f } ?: densityDpi.toFloat().coerceAtLeast(1f),
            ydpi = ydpi.takeIf { it > 0f } ?: densityDpi.toFloat().coerceAtLeast(1f),
            settings = settings,
        )

        /** Convenience for tests and for callers that already have a single density. */
        fun of(widthPx: Int, heightPx: Int, density: Float, settings: AppSettings): EdgeGeometry =
            EdgeGeometry(widthPx, heightPx, density, density, settings)
    }
}
