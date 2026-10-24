package com.edgeflow.hook.gesture

import android.view.MotionEvent
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureEngineTest {

    private val settings = AppSettings()
    private val geometry = EdgeGeometry.of(1080, 2400, 400f, settings)

    @Test
    fun `double tap fires when there is no single tap binding`() {
        var time = 1000L
        val resolver = TestBindings(setOf(Gesture.DOUBLE_TAP))
        val engine = GestureEngine(geometry, resolver, ManualScheduler(), clock = { time })
        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, time)
        engine.onTouch(MotionEvent.ACTION_UP, 5f, 1200f, 1, time)
        time += 100
        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 2, time)
        val result = engine.onTouch(MotionEvent.ACTION_UP, 5f, 1200f, 2, time)
        assertEquals(Gesture.DOUBLE_TAP, result.fired?.gesture)
        assertTrue(result.consumed)
    }

    @Test
    fun `taps on two different edges cannot become a double tap or lose the first tap`() {
        var time = 1000L
        val resolver = TestBindings(setOf(Gesture.TAP, Gesture.DOUBLE_TAP))
        val scheduler = ManualScheduler()
        val engine = GestureEngine(geometry, resolver, scheduler, clock = { time })
        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, time)
        engine.onTouch(MotionEvent.ACTION_UP, 5f, 1200f, 1, time)
        time += 50
        engine.onTouch(MotionEvent.ACTION_DOWN, 1075f, 1200f, 2, time)
        val result = engine.onTouch(MotionEvent.ACTION_UP, 1075f, 1200f, 2, time)
        assertNull(result.fired)
        scheduler.runAll()
        assertEquals(listOf(EdgeZone.LEFT_CENTER, EdgeZone.RIGHT_CENTER), resolver.fired.map { it.zone })
        assertTrue(resolver.fired.all { it.gesture == Gesture.TAP })
    }

    @Test
    fun `matched swipe is consumed and includes slide session start data`() {
        val resolver = TestBindings(setOf(Gesture.SWIPE_RIGHT))
        val engine = GestureEngine(geometry, resolver, NoOpScheduler())

        assertFalse(engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 7, 0L).consumed)
        val result = engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 7, 20L)

        assertTrue(result.consumed)
        assertEquals(EdgeZone.LEFT_CENTER, result.fired?.zone)
        assertEquals(Gesture.SWIPE_RIGHT, result.fired?.gesture)
        assertEquals(5f, result.fired?.startX)
        assertEquals(1200f, result.fired?.startY)
        assertEquals(200f, result.fired?.currentX)
        assertEquals(7, result.fired?.pointerId)
        assertEquals(geometry.swipeThresholdPx, result.fired?.stepDistance)
    }

    @Test
    fun `unbound swipe stays pass through`() {
        val resolver = TestBindings(setOf(Gesture.TAP))
        val engine = GestureEngine(geometry, resolver, NoOpScheduler())

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 2, 0L)
        val result = engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 2, 20L)

        assertFalse(result.consumed)
        assertNull(result.fired)
    }

    @Test
    fun `release after a matched swipe remains consumed`() {
        val resolver = TestBindings(setOf(Gesture.SWIPE_RIGHT))
        val engine = GestureEngine(geometry, resolver, NoOpScheduler())

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 3, 0L)
        engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 3, 20L)
        val release = engine.onTouch(MotionEvent.ACTION_UP, 220f, 1200f, 3, 40L)

        assertTrue(release.consumed)
        assertNull(release.fired)
    }

    @Test
    fun `additional pointer cancels a pending swipe and a later gesture works`() {
        val resolver = TestBindings(setOf(Gesture.SWIPE_RIGHT))
        val engine = GestureEngine(geometry, resolver, NoOpScheduler())

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, 0L)
        assertFalse(engine.onTouch(MotionEvent.ACTION_POINTER_DOWN, 5f, 1200f, 1, 10L).consumed)
        assertFalse(engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 1, 20L).consumed)
        engine.onTouch(MotionEvent.ACTION_UP, 200f, 1200f, 1, 30L)

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 2, 40L)
        val freshSwipe = engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 2, 50L)
        assertTrue(freshSwipe.consumed)
        assertEquals(Gesture.SWIPE_RIGHT, freshSwipe.fired?.gesture)
    }

    @Test
    fun `multi-touch after a claimed swipe stays consumed through final release`() {
        val resolver = TestBindings(setOf(Gesture.SWIPE_RIGHT))
        val engine = GestureEngine(geometry, resolver, NoOpScheduler())

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, 0L)
        assertTrue(engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 1, 10L).consumed)
        assertTrue(engine.onTouch(MotionEvent.ACTION_POINTER_DOWN, 200f, 1200f, 1, 20L).consumed)
        assertTrue(engine.onTouch(MotionEvent.ACTION_POINTER_UP, 200f, 1200f, 1, 30L).consumed)
        assertTrue(engine.onTouch(MotionEvent.ACTION_UP, 200f, 1200f, 1, 40L).consumed)

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 2, 50L)
        assertTrue(engine.onTouch(MotionEvent.ACTION_MOVE, 200f, 1200f, 2, 60L).consumed)
    }

    @Test
    fun `disabling cancels a pending long press`() {
        val resolver = TestBindings(setOf(Gesture.LONG_PRESS))
        val scheduler = ManualScheduler()
        val engine = GestureEngine(geometry, resolver, scheduler)

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, 0L)
        assertFalse(engine.disableForCurrentStream())
        scheduler.runAll()

        assertTrue(resolver.fired.isEmpty())
    }

    @Test
    fun `stale long-press callback cannot fire for a later pointer`() {
        val resolver = TestBindings(setOf(Gesture.LONG_PRESS))
        val scheduler = ManualScheduler()
        val engine = GestureEngine(geometry, resolver, scheduler)

        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 1, 0L)
        engine.onTouch(MotionEvent.ACTION_UP, 5f, 1200f, 1, 10L)
        engine.onTouch(MotionEvent.ACTION_DOWN, 5f, 1200f, 2, 20L)
        scheduler.runAt(0)
        assertTrue(resolver.fired.isEmpty())

        scheduler.runAt(1)
        assertEquals(Gesture.LONG_PRESS, resolver.fired.single().gesture)
    }

    private inner class TestBindings(private val boundGestures: Set<Gesture>) : BindingResolver {
        val fired = mutableListOf<FiredGesture>()
        override fun isZoneEnabled(zone: EdgeZone): Boolean = true
        override fun hasAnyBinding(zone: EdgeZone): Boolean = boundGestures.isNotEmpty()
        override fun hasBinding(zone: EdgeZone, gesture: Gesture): Boolean = gesture in boundGestures
        override fun onGesture(gesture: FiredGesture) {
            fired += gesture
        }
        override fun longPressTimeoutMillis(): Long = settings.longPressTimeoutMillis
        override fun doubleTapTimeoutMillis(): Long = settings.doubleTapTimeoutMillis
    }

    private class NoOpScheduler : GestureScheduler {
        override fun schedule(delayMillis: Long, action: () -> Unit): Cancellable = Cancellable { }
    }

    private class ManualScheduler : GestureScheduler {
        private val callbacks = mutableListOf<() -> Unit>()

        override fun schedule(delayMillis: Long, action: () -> Unit): Cancellable {
            callbacks += action
            return Cancellable { }
        }

        fun runAt(index: Int) = callbacks[index]()

        fun runAll() = callbacks.toList().forEach { it() }
    }
}
