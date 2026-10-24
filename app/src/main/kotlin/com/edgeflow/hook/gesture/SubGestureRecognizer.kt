package com.edgeflow.hook.gesture

import com.edgeflow.core.ActionId
import com.edgeflow.core.model.Action

/**
 * Second stage of an already claimed edge gesture. No Android dependencies:
 * the input hook owns stream consumption and the timeout, this class only
 * resolves a direction or hold to its configured child action.
 *
 * A parent gesture must already have claimed the stream. Never use this to
 * intercept an unbound DOWN, or a system back gesture could be swallowed.
 */
internal class SubGestureRecognizer(
    private val config: Action.SubGestures,
    val pointerId: Int,
    private val originX: Float,
    private val originY: Float,
    thresholdPx: Float,
) {
    private val thresholdSquared = thresholdPx.coerceAtLeast(1f).let { it * it }
    private var resolved = false
    private var moved = false

    /** Returns the first matching configured action; later calls cannot fire twice. */
    fun move(pointer: Int, x: Float, y: Float): Action? {
        if (resolved || pointer != pointerId) return null
        val distance = GestureGeometry.distanceSquared(originX, originY, x, y)
        if (!distance.isFinite()) return null
        if (distance < thresholdSquared) return null
        moved = true
        val direction = GestureGeometry.classifySwipe(
            originX, originY, x, y, thresholdSquared,
        )
        resolved = true
        return config.actionFor(direction).takeIf { it.id != ActionId.NONE }
    }

    /** Hold fires only if the finger has not passed the secondary swipe threshold. */
    fun hold(): Action? {
        if (resolved || moved) return null
        resolved = true
        return config.hold.takeIf { it.id != ActionId.NONE }
    }

    fun isResolved(): Boolean = resolved
}
