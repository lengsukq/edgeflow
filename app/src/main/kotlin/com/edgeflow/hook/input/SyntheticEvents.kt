package com.edgeflow.hook.input

import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.edgeflow.hook.util.Reflect

/**
 * Builds the events the module synthesises.
 *
 * Kept separate from the executor so the flag combinations — which are easy to
 * get subtly wrong and impossible to read at a call site — are stated once, with
 * a comment explaining each.
 */
internal object SyntheticEvents {

    /** `InputDevice.SOURCE_TOUCHSCREEN`; the dispatcher routes this to the focused window. */
    private const val SOURCE_TOUCHSCREEN = 4098

    /** `InputDevice.SOURCE_KEYBOARD`; required for the event to reach the IME. */
    private const val SOURCE_KEYBOARD = 257

    /** How long a synthesised tap holds the finger down. */
    const val TAP_HOLD_MILLIS = 40L

    /**
     * A single-pointer touch event.
     *
     * The pointer is declared as a finger rather than a mouse or stylus because
     * several apps ignore synthetic events whose tool type they do not expect.
     */
    fun touch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(properties),
            arrayOf(coords),
            0,      // metaState
            0,      // buttonState
            1f,     // xPrecision
            1f,     // yPrecision
            0,      // deviceId
            0,      // edgeFlags
            SOURCE_TOUCHSCREEN,
            0,      // flags
        )
    }

    /** A generic mouse-wheel event, used to scroll the focused scrollable surface. */
    fun scroll(x: Float, y: Float, verticalAxis: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, x, y, 0).apply {
            source = android.view.InputDevice.SOURCE_MOUSE
            Reflect.callMethod(this, "setAxisValue", MotionEvent.AXIS_VSCROLL, verticalAxis)
        }
    }

    /** A key press or release. */
    fun key(
        action: Int,
        keyCode: Int,
        metaState: Int = 0,
        downTime: Long = SystemClock.uptimeMillis(),
    ): KeyEvent {
        val now = SystemClock.uptimeMillis().coerceAtLeast(downTime)
        return KeyEvent(
            downTime,
            now,
            action,
            keyCode,
            0,          // repeat
            metaState,
            -1,         // deviceId
            0,          // scancode
            0,          // flags
            SOURCE_KEYBOARD,
        )
    }

    /**
     * The key events needed to type [text] on the current keyboard layout.
     *
     * @return null when the text cannot be represented, which happens for
     *         characters outside the active layout
     */
    fun textEvents(text: String): Array<KeyEvent>? =
        android.view.KeyCharacterMap.load(-1).getEvents(text.toCharArray())

    /** Re-stamps an event so it appears to happen now, as the dispatcher requires. */
    fun restamp(event: KeyEvent): KeyEvent =
        KeyEvent.changeTimeRepeat(event, SystemClock.uptimeMillis(), 0)
}

/** The three phases of a synthesised tap. */
internal fun MotionEvent.isInjectedTouch(): Boolean = source and 0x0000000F == 2
