package com.edgeflow.hook

import com.edgeflow.core.StateTriggerId

/** Pure validation/mapping for broadcasts that drive configured system-state actions. */
internal object StateBroadcastMapping {
    const val ACTION_USER_SWITCHED = "android.intent.action.USER_SWITCHED"
    private const val SYSTEM_UID = 1_000

    fun userSwitchedTriggerId(action: String?, userHandle: Int, senderUid: Int): Int? {
        if (action != ACTION_USER_SWITCHED || userHandle < 0 || senderUid != SYSTEM_UID) return null
        return StateTriggerId.USER_SWITCHED
    }
}
