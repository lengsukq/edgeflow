package com.edgeflow.core

/** Prevents an unaccepted scheduled action from consuming its due window. */
object ScheduleDispatchGate {
    inline fun acceptThenMark(
        alreadyDelivered: Boolean,
        accept: () -> Boolean,
        markDelivered: () -> Unit,
    ): Boolean {
        if (alreadyDelivered || !accept()) return false
        markDelivered()
        return true
    }
}
