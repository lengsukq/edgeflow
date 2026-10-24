package com.edgeflow.hook.gesture

/**
 * Direction classification for a moving pointer.
 *
 * Pure functions only, so the state machine stays readable and this logic can be
 * reasoned about — and tested — without an Android device.
 */
internal object GestureGeometry {

    /** The pointer did not move far enough to be a swipe. */
    const val NONE = -1

    /** Matches `Gesture.TAP.index`. */
    const val TAP = 0

    /** Matches `Gesture.SWIPE_LEFT.index`. */
    const val SWIPE_LEFT = 3

    /** Matches `Gesture.SWIPE_RIGHT.index`. */
    const val SWIPE_RIGHT = 4

    /** Matches `Gesture.SWIPE_UP.index`. */
    const val SWIPE_UP = 5

    /** Matches `Gesture.SWIPE_DOWN.index`. */
    const val SWIPE_DOWN = 6

    /**
     * Classifies the movement between two points.
     *
     * The dominant axis wins, which is what makes a diagonal drag resolve to the
     * direction the user was mostly heading rather than being rejected.
     *
     * @param slopSquared squared distance below which the pointer counts as still
     * @return one of the `SWIPE_*` constants, or [TAP] when within [slopSquared]
     */
    fun classifySwipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        slopSquared: Float,
    ): Int {
        val dx = toX - fromX
        val dy = toY - fromY
        val dxSquared = dx * dx
        val dySquared = dy * dy
        if (dxSquared + dySquared < slopSquared) return TAP
        return if (dxSquared > dySquared) {
            if (dx < 0f) SWIPE_LEFT else SWIPE_RIGHT
        } else {
            if (dy < 0f) SWIPE_UP else SWIPE_DOWN
        }
    }

    /** Squared distance between two points; comparing squares avoids a sqrt per event. */
    fun distanceSquared(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
        val dx = toX - fromX
        val dy = toY - fromY
        return dx * dx + dy * dy
    }
}
