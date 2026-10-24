package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GameModeTransitionTest {
    @Test fun `explicit action states are idempotent`() {
        assertEquals(true, GameModePolicy.nextEnabled(true, false))
        assertEquals(true, GameModePolicy.nextEnabled(true, true))
        assertEquals(false, GameModePolicy.nextEnabled(false, true))
        assertEquals(false, GameModePolicy.nextEnabled(false, false))
    }

    @Test fun `toggle is calculated against current effective state`() {
        assertEquals(false, GameModePolicy.nextEnabled(null, true))
        assertEquals(true, GameModePolicy.nextEnabled(null, false))
    }
}
