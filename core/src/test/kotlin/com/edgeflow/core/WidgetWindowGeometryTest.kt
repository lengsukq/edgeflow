package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.GestureTable
import com.edgeflow.core.model.NamedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetWindowGeometryTest {
    @Test fun `preferred and custom dimensions are capped by display bounds`() {
        assertEquals(280, WidgetWindowGeometry.sizeDp(0, 280, 400, 160))
        assertEquals(160, WidgetWindowGeometry.sizeDp(40, 280, 400, 160))
        assertEquals(400, WidgetWindowGeometry.sizeDp(1200, 280, 400, 160))
        assertEquals(90, WidgetWindowGeometry.sizeDp(180, 280, 90, 160))
    }

    @Test fun `positions clamp to free space and round trip percentages`() {
        assertEquals(200, WidgetWindowGeometry.pixelPosition(400, 50))
        assertEquals(0, WidgetWindowGeometry.pixelPosition(400, -20))
        assertEquals(400, WidgetWindowGeometry.pixelPosition(400, 120))
        assertEquals(25, WidgetWindowGeometry.positionPercent(400, 100))
        assertEquals(0, WidgetWindowGeometry.positionPercent(0, 50))
    }

    @Test fun `timeout bounds are safe and zero means no auto close`() {
        assertEquals(0L, WidgetWindowGeometry.timeoutMillis(0))
        assertEquals(45_000L, WidgetWindowGeometry.timeoutMillis(45))
        assertEquals(3_600_000L, WidgetWindowGeometry.timeoutMillis(Int.MAX_VALUE))
    }

    @Test fun `management discovers deeply nested widget references`() {
        val config = EdgeConfig(
            savedMultiActions = listOf(
                NamedAction(1, "widget", Action.Sequence(listOf(
                    Action.FloatingWidget(101),
                    Action.Conditional(
                        thenAction = Action.Repeat(listOf(Action.FloatingWidget(202)), 2),
                        elseAction = Action.Home,
                    ),
                ))),
            ),
            gestures = GestureTable(bindings = mapOf(
                3 to mapOf(0 to Action.SubGestures(swipeRight = Action.FloatingWidget(303))),
            )),
        )
        assertEquals(setOf(101, 202, 303), WidgetReferences.referencedIds(config))
        assertTrue(404 !in WidgetReferences.referencedIds(config))
    }
}
