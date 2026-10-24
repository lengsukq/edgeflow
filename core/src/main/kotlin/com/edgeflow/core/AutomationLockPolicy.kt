package com.edgeflow.core

import com.edgeflow.core.model.Action

/**
 * Locked-device default is deny. Structural actions may be traversed, but
 * every concrete leaf is rechecked. Only the platform unlock request is
 * allowed on a locked device; it must never bypass Android authentication.
 */
object AutomationLockPolicy {
    fun mayDispatch(action: Action, locked: Boolean): Boolean {
        if (!locked) return true
        return when (action) {
            is Action.Sequence, is Action.Conditional, is Action.Repeat,
            is Action.EditableAction, is Action.SavedMultiAction -> true
            is Action.Simple -> action.id == ActionId.DISMISS_KEYGUARD
            is Action.StopAll -> true
            else -> false
        }
    }
}
