package com.edgeflow.hook.util

import android.util.Log

/**
 * Logging facade for injected code.
 *
 * Everything the hook logs goes through here so a single switch can silence it,
 * and so a tag change does not have to be chased through the whole module.
 */
object XLog {
    const val TAG = "EdgeFlow"

    @Volatile
    var verbose: Boolean = true

    @Volatile
    private var sink: ((String) -> Unit)? = null

    /** Installs an extra sink, used to mirror logs into a file the app can read. */
    fun setSink(newSink: ((String) -> Unit)?) {
        sink = newSink
    }

    fun d(message: String) {
        if (!verbose) return
        runCatching { Log.d(TAG, message) }
        sink?.invoke(message)
    }

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }
        sink?.invoke(message)
    }

    fun w(message: String, t: Throwable? = null) {
        runCatching { if (t != null) Log.w(TAG, message, t) else Log.w(TAG, message) }
        sink?.invoke(message)
    }

    fun e(message: String, t: Throwable? = null) {
        runCatching { if (t != null) Log.e(TAG, message, t) else Log.e(TAG, message) }
        sink?.invoke(message)
    }

    /**
     * Runs [block], reporting rather than propagating failures. Hook callbacks
     * must never throw into the framework: an exception here can take down
     * system_server.
     */
    inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            e("$what failed: ${t.message}", t)
        }
    }

    inline fun <T> guarded(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            e("$what failed: ${t.message}", t)
            fallback
        }
}
