package com.edgeflow.hook

import com.edgeflow.core.StateTriggerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StateBroadcastMappingTest {

    @Test
    fun `system user switch with a valid user handle maps to the extension trigger`() {
        assertEquals(
            StateTriggerId.USER_SWITCHED,
            StateBroadcastMapping.userSwitchedTriggerId(
                StateBroadcastMapping.ACTION_USER_SWITCHED,
                userHandle = 10,
                senderUid = 1_000,
            ),
        )
    }

    @Test
    fun `non-system source cannot trigger a user switch`() {
        assertNull(
            StateBroadcastMapping.userSwitchedTriggerId(
                StateBroadcastMapping.ACTION_USER_SWITCHED,
                userHandle = 10,
                senderUid = 10_123,
            ),
        )
    }

    @Test
    fun `invalid user handle or unrelated action is ignored`() {
        assertNull(
            StateBroadcastMapping.userSwitchedTriggerId(
                StateBroadcastMapping.ACTION_USER_SWITCHED,
                userHandle = -1,
                senderUid = 1_000,
            ),
        )
        assertNull(
            StateBroadcastMapping.userSwitchedTriggerId(
                "android.intent.action.USER_PRESENT",
                userHandle = 10,
                senderUid = 1_000,
            ),
        )
    }
}
