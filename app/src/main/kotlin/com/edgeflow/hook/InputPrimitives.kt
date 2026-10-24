package com.edgeflow.hook

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.edgeflow.hook.util.XLog
import java.util.concurrent.atomic.AtomicLong

/**
 * Gesture and key state machines run on the input dispatch thread, which must
 * never block: a stall here freezes touch for the whole device.
 *
 * This guard therefore only *observes* how long a callback took and reports
 * outliers. It does not synchronise, retry or queue anything.
 */
internal class InputThreadWatchdog(private val warnAfterMillis: Long = 16) {

    private val lastWarnAt = AtomicLong(0)

    inline fun <T> measure(what: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            if (elapsedMillis > warnAfterMillis) report(what, elapsedMillis)
        }
    }

    private fun report(what: String, elapsedMillis: Long) {
        // Rate-limit: a slow device would otherwise flood the log.
        val now = System.currentTimeMillis()
        val previous = lastWarnAt.get()
        if (now - previous > WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(previous, now)) {
            XLog.w("$what held the input thread for ${elapsedMillis}ms")
        }
    }

    private companion object {
        const val WARN_INTERVAL_MILLIS = 2_000L
    }
}

/**
 * Mark module-generated input as already processed by the native input filter.
 *
 * A call-stack check is NOT a valid self-injection detector: legitimate touches
 * also pass through our own com.edgeflow hooks, so the old test discarded every
 * real user touch before the gesture state machine could observe it.
 */
internal object InputInjectionPolicy {
    // AOSP frameworks/native/include/input/Input.h.
    const val FILTERED = 0x04000000
    const val PASS_TO_USER = 0x40000000

    /**
     * Synthetic events originate in our action worker, not an original
     * platform filter callback. Without PASS_TO_USER, FILTERED makes the native
     * dispatcher skip intercept*BeforeQueueing, so no policy stage ever sets
     * the delivery bit and the HOME/BACK/MENU action may never be delivered.
     */
    fun markFiltered(policyFlags: Int): Int = policyFlags or FILTERED or PASS_TO_USER
}

/** Narrow adapters so call sites do not repeat the same action constants. */
internal fun MotionEvent.isTouchDown() = actionMasked == MotionEvent.ACTION_DOWN

internal fun MotionEvent.isTouchUp() = actionMasked == MotionEvent.ACTION_UP

internal fun MotionEvent.isTouchCancel() = actionMasked == MotionEvent.ACTION_CANCEL

internal fun KeyEvent.isDown() = action == KeyEvent.ACTION_DOWN

/** Log-friendly description of an event, without allocating on the hot path. */
internal fun InputEvent.kind(): String = when (this) {
    is MotionEvent -> "motion/${actionMasked}"
    is KeyEvent -> "key/${keyCode}"
    else -> javaClass.simpleName
}
