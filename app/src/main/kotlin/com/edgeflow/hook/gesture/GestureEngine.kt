package com.edgeflow.hook.gesture

import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture

/**
 * The phase a gesture is in. The machine is deliberately small: every edge
 * gesture is a short sequence of these, and keeping the state explicit makes
 * the transition table auditable.
 */
internal enum class GesturePhase {
    /** No pointer is down inside an edge zone. */
    IDLE,

    /** A pointer is down in a zone and we are waiting to see what it becomes. */
    PENDING,

    /** A long press has fired; the pointer may still move or lift. */
    HELD,

    /** The pointer moved far enough that the gesture is committed to a swipe. */
    SWIPING,

    /** We consumed the pointer and are waiting for it to lift. */
    CONSUMED,
}

/**
 * What the engine decided about one input event.
 *
 * [consumed] is the value to hand back to the input dispatcher: `true` lets the
 * event continue to the focused window, `false` swallows it. The gesture engine
 * only swallows once a gesture has actually matched a binding, so an edge zone
 * with nothing bound stays transparent to the app underneath.
 */
internal data class GestureOutcome(
    val consumed: Boolean,
    val fired: FiredGesture? = null,
) {
    companion object {
        /** Let the event through untouched. */
        val PassThrough = GestureOutcome(consumed = false)

        /** Swallow the event; a binding has claimed it. */
        val Swallow = GestureOutcome(consumed = true)
    }
}

/** A gesture that matched a binding and should be executed. */
internal data class FiredGesture(
    val zone: EdgeZone,
    val gesture: Gesture,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val currentX: Float = 0f,
    val currentY: Float = 0f,
    val pointerId: Int = -1,
    val stepDistance: Float = 48f,
)

/**
 * State machine that turns raw touch events inside an edge strip into
 * [Gesture] recognitions.
 *
 * ## Why a state machine rather than a GestureDetector
 *
 * `android.view.GestureDetector` needs a `View` and a `Looper`, neither of which
 * exists on the input filter thread. It also cannot express "wait for the double
 * tap timeout before firing a single tap", which is required here: firing a tap
 * immediately would make a double tap fire twice.
 *
 * ## Consumption rule
 *
 * The pointer is only swallowed once the engine knows a binding exists. Until
 * then every event passes through, so tapping an edge that has nothing bound
 * behaves exactly as if the module were not installed.
 *
 * State transitions are synchronized because timer callbacks run on a separate handler thread.
 */
internal class GestureEngine(
    private var geometry: EdgeGeometry,
    private val bindings: BindingResolver,
    private val scheduler: GestureScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var phase = GesturePhase.IDLE
    private var activeZone: EdgeZone? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    /** Largest distance the pointer has travelled from [downX],[downY]. */
    private var maxTravelSquared = 0f

    /** Timestamp of the previous completed tap, for double-tap detection. */
    private var lastTapAt = 0L
    /** A double tap must stay in the same edge zone. */
    private var lastTapZone: EdgeZone? = null
    private var pendingTapHasAction = false

    /** Pending single tap, cancelled if a second tap arrives in time. */
    private var pendingTapToken: Cancellable? = null

    /** Pending long press, cancelled if the pointer lifts first. */
    private var pendingLongPressToken: Cancellable? = null
    private var tapGeneration = 0L
    private var longPressGeneration = 0L

    /** Pointer id we locked onto; events from other fingers are ignored. */
    private var activePointerId = -1

    @Synchronized
    fun updateGeometry(newGeometry: EdgeGeometry) {
        geometry = newGeometry
    }

    /**
     * Feeds one touch event through the machine.
     *
     * @param actionMasked the masked action of the event
     * @param rawX raw screen X, unaffected by any window offset
     * @param rawY raw screen Y
     * @param pointerId the pointer this event belongs to
     * @param eventTime the event's own timestamp, so scheduling stays consistent
     */
    @Synchronized
    fun onTouch(
        actionMasked: Int,
        rawX: Float,
        rawY: Float,
        pointerId: Int,
        eventTime: Long,
    ): GestureOutcome = when (actionMasked) {
        ACTION_DOWN -> onDown(rawX, rawY, pointerId)
        ACTION_MOVE -> onMove(rawX, rawY, pointerId)
        ACTION_UP -> onUp(rawX, rawY, pointerId)
        ACTION_CANCEL -> onCancel()
        ACTION_POINTER_DOWN -> onAdditionalPointerDown()
        ACTION_POINTER_UP -> onAdditionalPointerUp()
        else -> GestureOutcome.PassThrough
    }

    /** Abandons any in-flight gesture. Called when the screen turns off. */
    @Synchronized
    fun reset() {
        cancelTimers()
        phase = GesturePhase.IDLE
        activeZone = null
        activePointerId = -1
        maxTravelSquared = 0f
        lastTapAt = 0L
        lastTapZone = null
        pendingTapHasAction = false
    }

    /** Cancels recognition while keeping an already-claimed input stream swallowed. */
    @Synchronized
    fun disableForCurrentStream(forceConsume: Boolean = false): Boolean {
        val wasClaimed = forceConsume || phase == GesturePhase.SWIPING || phase == GesturePhase.HELD || phase == GesturePhase.CONSUMED
        if (!wasClaimed) {
            reset()
            return false
        }
        cancelTimers()
        phase = GesturePhase.CONSUMED
        activeZone = null
        activePointerId = -1
        maxTravelSquared = 0f
        lastTapAt = 0L
        return true
    }

    private fun onDown(rawX: Float, rawY: Float, pointerId: Int): GestureOutcome {
        // A second finger arriving during a gesture is not ours to interpret;
        // multi-touch belongs to the app (pinch, rotate) and we stay out of it.
        if (phase != GesturePhase.IDLE) {
            return if (phase == GesturePhase.CONSUMED) GestureOutcome.Swallow else GestureOutcome.PassThrough
        }

        val zone = geometry.hitTest(rawX, rawY) ?: return GestureOutcome.PassThrough
        if (!bindings.isZoneEnabled(zone)) return GestureOutcome.PassThrough

        // A tap in another edge is not the second half of this edge's double
        // tap. Finish the earlier single tap instead of silently discarding it.
        if (pendingTapToken != null && lastTapZone != zone) {
            val previousZone = lastTapZone
            val hadAction = pendingTapHasAction
            cancelPendingTap()
            lastTapAt = 0L
            lastTapZone = null
            if (hadAction && previousZone != null) {
                bindings.onGesture(FiredGesture(previousZone, Gesture.TAP))
            }
        }

        activeZone = zone
        activePointerId = pointerId
        downX = rawX
        downY = rawY
        lastX = rawX
        lastY = rawY
        maxTravelSquared = 0f

        // Only start tracking when this zone can actually produce something.
        // Otherwise an unbound edge would swallow every touch near the screen
        // edge, which would break system back gestures.
        if (!bindings.hasAnyBinding(zone)) {
            phase = GesturePhase.IDLE
            activeZone = null
            return GestureOutcome.PassThrough
        }

        phase = GesturePhase.PENDING
        scheduleLongPress()
        return GestureOutcome.PassThrough
    }

    private fun onMove(rawX: Float, rawY: Float, pointerId: Int): GestureOutcome {
        if (phase == GesturePhase.IDLE) return GestureOutcome.PassThrough
        if (pointerId != activePointerId) {
            return if (phase == GesturePhase.CONSUMED) GestureOutcome.Swallow else GestureOutcome.PassThrough
        }

        lastX = rawX
        lastY = rawY
        val travel = GestureGeometry.distanceSquared(downX, downY, rawX, rawY)
        if (travel > maxTravelSquared) maxTravelSquared = travel

        if (phase == GesturePhase.PENDING || phase == GesturePhase.HELD) {
            val direction = GestureGeometry.classifySwipe(
                fromX = downX,
                fromY = downY,
                toX = rawX,
                toY = rawY,
                slopSquared = geometry.swipeThresholdSquared,
            )
            if (direction != GestureGeometry.TAP) {
                // The pointer is committed to a swipe: a long press can no
                // longer fire, and neither can a tap.
                cancelPendingLongPress()
                cancelPendingTap()

                val gesture = Gesture.of(direction) ?: return GestureOutcome.PassThrough
                val zone = activeZone ?: return GestureOutcome.PassThrough
                if (!bindings.hasBinding(zone, gesture)) {
                    // Nothing bound to this direction: hand the gesture back to
                    // the app so a swipe from the edge still scrolls.
                    reset()
                    return GestureOutcome.PassThrough
                }
                phase = GesturePhase.SWIPING
                return GestureOutcome(
                    consumed = true,
                    fired = FiredGesture(
                        zone = zone,
                        gesture = gesture,
                        startX = downX,
                        startY = downY,
                        currentX = rawX,
                        currentY = rawY,
                        pointerId = pointerId,
                        stepDistance = kotlin.math.sqrt(geometry.swipeThresholdSquared).coerceAtLeast(1f),
                    ),
                )
            }
        }
        return if (phase == GesturePhase.CONSUMED || phase == GesturePhase.SWIPING) {
            GestureOutcome.Swallow
        } else {
            GestureOutcome.PassThrough
        }
    }

    private fun onUp(rawX: Float, rawY: Float, pointerId: Int): GestureOutcome {
        if (phase == GesturePhase.IDLE) return GestureOutcome.PassThrough
        if (phase == GesturePhase.CONSUMED) {
            reset()
            return GestureOutcome.Swallow
        }
        if (pointerId != activePointerId) {
            return if (phase == GesturePhase.CONSUMED) GestureOutcome.Swallow else GestureOutcome.PassThrough
        }

        cancelPendingLongPress()

        val zone = activeZone
        val wasSwiping = phase == GesturePhase.SWIPING
        val wasHeld = phase == GesturePhase.HELD

        // A swipe already fired on the move; only the release is left to swallow.
        if (wasSwiping) {
            reset()
            return GestureOutcome.Swallow
        }

        // A long press already fired while held; swallow the release too, so the
        // app never sees a stray tap.
        if (wasHeld) {
            reset()
            return GestureOutcome.Swallow
        }

        if (zone == null) {
            reset()
            return GestureOutcome.PassThrough
        }

        val now = clock()
        val withinDoubleTapWindow = lastTapZone == zone && lastTapAt > 0L &&
            now - lastTapAt in 0..bindings.doubleTapTimeoutMillis()
        val stationary = maxTravelSquared <= geometry.tapSlopSquared

        if (!stationary) {
            reset()
            return GestureOutcome.PassThrough
        }

        if (withinDoubleTapWindow && bindings.hasBinding(zone, Gesture.DOUBLE_TAP)) {
            cancelPendingTap()
            lastTapAt = 0L
            reset()
            return GestureOutcome(consumed = true, fired = FiredGesture(zone, Gesture.DOUBLE_TAP))
        }

        val hasTapAction = bindings.hasBinding(zone, Gesture.TAP)
        val hasDoubleTapAction = bindings.hasBinding(zone, Gesture.DOUBLE_TAP)
        if (!hasTapAction && !hasDoubleTapAction) {
            // Nothing to recognize after the release.
            reset()
            return GestureOutcome.PassThrough
        }

        // Even without a single-tap action, retain the first tap when the
        // zone has a double-tap binding. Otherwise double-tap-only zones fail.
        lastTapAt = now
        lastTapZone = zone
        pendingTapHasAction = hasTapAction
        val capturedZone = zone
        val emitTap = hasTapAction
        val generation = ++tapGeneration
        pendingTapToken = scheduler.schedule(bindings.doubleTapTimeoutMillis()) {
            val shouldFire = synchronized(this) {
                if (generation != tapGeneration) {
                    false
                } else {
                    pendingTapToken = null
                    lastTapAt = 0L
                    lastTapZone = null
                    pendingTapHasAction = false
                    emitTap
                }
            }
            if (shouldFire) bindings.onGesture(FiredGesture(capturedZone, Gesture.TAP))
        }
        reset(keepDoubleTapState = true)
        return GestureOutcome.PassThrough
    }

    private fun onCancel(): GestureOutcome {
        val wasConsumed = phase == GesturePhase.CONSUMED || phase == GesturePhase.SWIPING || phase == GesturePhase.HELD
        reset()
        return if (wasConsumed) GestureOutcome.Swallow else GestureOutcome.PassThrough
    }

    private fun onAdditionalPointerDown(): GestureOutcome {
        val wasConsumed = phase == GesturePhase.CONSUMED || phase == GesturePhase.SWIPING || phase == GesturePhase.HELD
        if (wasConsumed) {
            cancelTimers()
            phase = GesturePhase.CONSUMED
            activeZone = null
            activePointerId = -1
            maxTravelSquared = 0f
            return GestureOutcome.Swallow
        }

        reset(keepDoubleTapState = pendingTapToken != null)
        return GestureOutcome.PassThrough
    }

    private fun onAdditionalPointerUp(): GestureOutcome {
        if (phase == GesturePhase.SWIPING || phase == GesturePhase.HELD) {
            cancelTimers()
            phase = GesturePhase.CONSUMED
            activeZone = null
            activePointerId = -1
            maxTravelSquared = 0f
        }
        return if (phase == GesturePhase.CONSUMED) GestureOutcome.Swallow else GestureOutcome.PassThrough
    }

    private fun scheduleLongPress() {
        val timeout = bindings.longPressTimeoutMillis()
        cancelPendingLongPress()
        val generation = longPressGeneration
        pendingLongPressToken = scheduler.schedule(timeout) {
            val gesture = synchronized(this) {
                if (generation != longPressGeneration || phase != GesturePhase.PENDING) {
                    return@synchronized null
                }
                pendingLongPressToken = null
                val zone = activeZone ?: return@synchronized null
                if (maxTravelSquared > geometry.tapSlopSquared || !bindings.hasBinding(zone, Gesture.LONG_PRESS)) {
                    return@synchronized null
                }

                phase = GesturePhase.HELD
                // The pointer is still down. Keep its identity/coordinates so
                // a second-stage hold or swipe can begin without a new DOWN.
                FiredGesture(
                    zone = zone,
                    gesture = Gesture.LONG_PRESS,
                    startX = downX,
                    startY = downY,
                    currentX = lastX,
                    currentY = lastY,
                    pointerId = activePointerId,
                    stepDistance = kotlin.math.sqrt(geometry.swipeThresholdSquared).coerceAtLeast(1f),
                )
            }
            gesture?.let(bindings::onGesture)
        }
    }

    private fun cancelPendingTap() {
        tapGeneration++
        pendingTapToken?.cancel()
        pendingTapToken = null
        pendingTapHasAction = false
    }

    private fun cancelPendingLongPress() {
        longPressGeneration++
        pendingLongPressToken?.cancel()
        pendingLongPressToken = null
    }

    @Synchronized
    fun reset(keepDoubleTapState: Boolean = false) {
        cancelPendingLongPress()
        if (!keepDoubleTapState) {
            cancelPendingTap()
            lastTapAt = 0L
            lastTapZone = null
        }
        phase = GesturePhase.IDLE
        activeZone = null
        activePointerId = -1
        maxTravelSquared = 0f
    }

    private fun cancelTimers() {
        cancelPendingTap()
        cancelPendingLongPress()
    }

    private companion object {
        const val ACTION_DOWN = android.view.MotionEvent.ACTION_DOWN
        const val ACTION_MOVE = android.view.MotionEvent.ACTION_MOVE
        const val ACTION_UP = android.view.MotionEvent.ACTION_UP
        const val ACTION_CANCEL = android.view.MotionEvent.ACTION_CANCEL
        const val ACTION_POINTER_DOWN = android.view.MotionEvent.ACTION_POINTER_DOWN
        const val ACTION_POINTER_UP = android.view.MotionEvent.ACTION_POINTER_UP
    }
}

/** A scheduled callback that can be withdrawn before it runs. */
internal fun interface Cancellable {
    fun cancel()
}

/** Abstraction over timeouts so the engine can be tested without real timers. */
internal interface GestureScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Cancellable
}

/**
 * Answers "what does this zone and gesture do?" for the engine.
 *
 * Implemented by the runtime so the engine never needs to know about the config
 * model, the current foreground app, or how actions are executed.
 */
internal interface BindingResolver {

    /** Whether gestures are globally enabled for this zone. */
    fun isZoneEnabled(zone: EdgeZone): Boolean

    /** Whether any gesture at all is bound in this zone. */
    fun hasAnyBinding(zone: EdgeZone): Boolean

    /** Whether this specific gesture is bound in this zone. */
    fun hasBinding(zone: EdgeZone, gesture: Gesture): Boolean

    /** Deliver a recognised gesture for execution. */
    fun onGesture(gesture: FiredGesture)

    fun longPressTimeoutMillis(): Long

    fun doubleTapTimeoutMillis(): Long
}
