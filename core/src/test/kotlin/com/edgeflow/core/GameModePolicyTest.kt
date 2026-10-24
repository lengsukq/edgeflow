package com.edgeflow.core

import com.edgeflow.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameModePolicyTest {
    @Test fun edgesFollowOriginalFourWayMapping() {
        for (zone in 0..15) {
            assertTrue(GameModePolicy.ignoreEdge(zone, 1 shl (zone / 4)))
            assertFalse(GameModePolicy.ignoreEdge(zone, 0))
        }
        assertFalse(GameModePolicy.ignoreEdge(-1, 0xF))
        assertFalse(GameModePolicy.ignoreEdge(16, 0xF))
    }

    @Test fun keyGuardOnlyTouchesExplicitChoices() {
        assertFalse(GameModePolicy.interceptKey(26, 0x3F)) // never power
        assertFalse(GameModePolicy.interceptKey(4, 0)) // back remains functional by default
        GameModePolicy.androidKeyCodes.forEachIndexed { slot, code ->
            assertTrue(GameModePolicy.interceptKey(code, 1 shl slot))
        }
        assertEquals(0b1010, GameModePolicy.withBit(0b1000, 1, true))
        assertEquals(0b1000, GameModePolicy.withBit(0b1010, 1, false))
        assertEquals(0b1111, AppSettings().gameIgnoreEdgesMask)
        assertEquals(0, AppSettings().gameInterceptKeysMask)
    }
}
