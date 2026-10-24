package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.NamedAction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadialMenuGeometryTest {
    @Test fun `old settings without ring fields continue to use original list layout`() {
        val old = ConfigCodec.json.decodeFromString<AppSettings>(
            """{"floatingAutomationBarEnabled":true,"floatingAutomationBarActionIds":[4,5]}""",
        )
        assertEquals("list", old.floatingAutomationBarDisplayMode)
        assertEquals(2, old.floatingAutomationBarRingCount)
        assertEquals(listOf(4, 5), old.floatingAutomationBarActionIds)
        val upgraded = old.copy(
            floatingAutomationBarDisplayMode = "radial",
            floatingAutomationBarRingActionIds = mapOf(11 to 5),
        )
        assertEquals(upgraded,
            ConfigCodec.json.decodeFromString<AppSettings>(ConfigCodec.json.encodeToString(upgraded)))
    }

    @Test fun `all six sectors in two and three rings hit their exact slot`() {
        for (rings in 2..3) for (ring in 0 until rings) for (sector in 0..5) {
            val slot = ring * 6 + sector
            val angle = RadialMenuGeometry.centerAngle(slot)
            val band = RadialMenuGeometry.band(ring, rings)
            val distance = (band.start + band.endInclusive) / 2f * 100f
            assertEquals(slot, RadialMenuGeometry.slotAt(
                (cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat(),
                0f, 0f, 100f, rings,
            ))
        }
    }

    @Test fun `center gaps and invalid pointer positions cancel without an action`() {
        assertEquals(-1, RadialMenuGeometry.slotAt(0f, 0f, 0f, 0f, 100f, 2))
        assertEquals(-1, RadialMenuGeometry.slotAt(59f, 0f, 0f, 0f, 100f, 2))
        assertEquals(-1, RadialMenuGeometry.slotAt(120f, 0f, 0f, 0f, 100f, 3))
        assertEquals(-1, RadialMenuGeometry.slotAt(Float.NaN, 0f, 0f, 0f, 100f, 2))
    }

    @Test fun `ring assignments preserve old pins and ignore deleted tasks`() {
        val config = EdgeConfig(
            settings = AppSettings(
                floatingAutomationBarActionIds = listOf(1, 2),
                floatingAutomationBarRingActionIds = mapOf(6 to 3),
            ),
            savedMultiActions = listOf(
                NamedAction(1, "One", Action.Simple(ActionId.HOME)),
                NamedAction(2, "Two", Action.Simple(ActionId.BACK)),
                NamedAction(3, "Removed", Action.Simple(ActionId.HOME), deleted = true),
            ),
        )
        val slots = FloatingAutomationBarPolicy.ringSlots(config)
        assertEquals(12, slots.size)
        assertEquals(1, slots[0]?.id)
        assertEquals(2, slots[1]?.id)
        assertNull(slots[6])
        assertEquals(mapOf(0 to 12), FloatingAutomationBarPolicy.assignRingSlot(emptyMap(), 0, 12))
        assertEquals(mapOf(0 to 0), FloatingAutomationBarPolicy.assignRingSlot(mapOf(0 to 12), 0, null))
    }
}
