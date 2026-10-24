package com.edgeflow.core

/**
 * Xposed Edge's Game Mode is an input-guard, not Android's GameManager mode.
 * Bits 0..3 select edges left/right/top/bottom; bits 0..5 select
 * Back/Home/Recents/Menu/Volume+/Volume-. Both masks require explicit opt-in.
 */
object GameModePolicy {
    fun nextEnabled(requested: Boolean?, currentlyEnabled: Boolean): Boolean =
        requested ?: !currentlyEnabled
    val edges = listOf("Left", "Right", "Top", "Bottom")
    val keys = listOf("Back", "Home", "Recents", "Menu", "Volume +", "Volume -")
    val androidKeyCodes = listOf(4, 3, 187, 82, 24, 25)

    fun ignoreEdge(zoneId: Int, mask: Int): Boolean =
        zoneId in 0..15 && ((mask ushr (zoneId / 4)) and 1) == 1

    fun interceptKey(keyCode: Int, mask: Int): Boolean {
        val slot = androidKeyCodes.indexOf(keyCode)
        return slot >= 0 && ((mask ushr slot) and 1) == 1
    }

    fun withBit(mask: Int, bit: Int, enabled: Boolean): Int {
        require(bit in 0..5)
        return if (enabled) mask or (1 shl bit) else mask and (1 shl bit).inv()
    }
}
