package com.edgeflow.hook

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import com.edgeflow.core.Ipc
import com.edgeflow.core.model.AppSettings
import com.edgeflow.hook.util.XLog

internal data class GesturePointerFrame(
    val visible: Boolean,
    val x: Float,
    val y: Float,
    val scalePercent: Int,
    val color: Long,
)

/**
 * Sends coalesced pointer frames to the app-process OverlayHost. Touch callbacks
 * only replace a value and enqueue a handler task; broadcast IPC never runs on
 * the InputManager filter thread.
 */
internal class GesturePointerPublisher(
    private val contextProvider: () -> Context?,
) {
    private val thread = HandlerThread("EdgeFlow-pointer-overlay").apply { start() }
    private val handler = Handler(thread.looper)
    private val pending = LatestValueCoalescer<GesturePointerFrame>()
    private val scheduleLock = Any()
    private var drainScheduled = false
    @Volatile private var closed = false

    private val drain = Runnable {
        val frame = pending.take()
        synchronized(scheduleLock) { drainScheduled = false }
        frame?.let(::send)
        if (pending.hasPending()) scheduleDrain(MOVE_FRAME_INTERVAL_MS)
    }

    fun publish(
        visible: Boolean,
        x: Float,
        y: Float,
        settings: AppSettings,
        immediate: Boolean = false,
    ) {
        if (closed) return
        pending.offer(
            GesturePointerFrame(
                visible = visible,
                x = x,
                y = y,
                scalePercent = settings.gesturePointerScalePercent,
                color = settings.gesturePointerColor,
            ),
        )
        scheduleDrain(if (immediate || !visible) 0L else MOVE_FRAME_INTERVAL_MS)
    }

    /** Explicitly hides the view before the owning system-server component exits. */
    fun shutdown(settings: AppSettings) {
        publish(visible = false, x = 0f, y = 0f, settings = settings, immediate = true)
        handler.postDelayed(
            {
                closed = true
                thread.quitSafely()
            },
            SHUTDOWN_GRACE_PERIOD_MS,
        )
    }

    private fun scheduleDrain(delayMillis: Long) {
        synchronized(scheduleLock) {
            if (closed) return
            if (!drainScheduled) {
                drainScheduled = true
                handler.postDelayed(drain, delayMillis)
            } else if (delayMillis == 0L) {
                // A terminal hide must not wait behind a frame-throttled move.
                handler.removeCallbacks(drain)
                handler.post(drain)
            }
        }
    }

    private fun send(frame: GesturePointerFrame) {
        val context = contextProvider() ?: return
        try {
            context.sendBroadcast(
                Intent(Ipc.SHOW_OVERLAY)
                    .setPackage(Ipc.APP_PACKAGE)
                    .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_GESTURE_POINTER)
                    .putExtra(Ipc.EXTRA_POINTER_VISIBLE, frame.visible)
                    .putExtra(Ipc.EXTRA_POINTER_X, frame.x)
                    .putExtra(Ipc.EXTRA_POINTER_Y, frame.y)
                    .putExtra(Ipc.EXTRA_POINTER_SCALE, frame.scalePercent)
                    .putExtra(Ipc.EXTRA_POINTER_COLOR, frame.color),
            )
        } catch (t: Throwable) {
            XLog.w("could not update gesture pointer overlay: ${t.message}")
        }
    }

    private companion object {
        /** At most one move broadcast per display frame, regardless of touch sampling rate. */
        const val MOVE_FRAME_INTERVAL_MS = 16L
        const val SHUTDOWN_GRACE_PERIOD_MS = 100L
    }
}
