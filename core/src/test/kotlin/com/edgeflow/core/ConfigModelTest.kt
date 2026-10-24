package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.ActionId as Id
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.GestureTable
import com.edgeflow.core.model.KeyTable
import com.edgeflow.core.model.PieConfig
import com.edgeflow.core.model.PieEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the parts of the model that carry behaviour rather than data.
 *
 * These are the pieces where a mistake is silent: a wrong fallback order or a
 * mis-indexed slot produces a gesture that simply does nothing, which is hard to
 * notice by inspection and easy to notice here.
 */
class ConfigModelTest {

    // ---------------------------------------------------------------- zones

    @Test
    fun `sub-zones map to their full-length parent`() {
        assertEquals(EdgeZone.LEFT, EdgeZone.LEFT_TOP.parent)
        assertEquals(EdgeZone.LEFT, EdgeZone.LEFT_CENTER.parent)
        assertEquals(EdgeZone.LEFT, EdgeZone.LEFT_BOTTOM.parent)
        assertEquals(EdgeZone.RIGHT, EdgeZone.RIGHT_TOP.parent)
        assertEquals(EdgeZone.TOP, EdgeZone.TOP_RIGHT.parent)
        assertEquals(EdgeZone.BOTTOM, EdgeZone.BOTTOM_LEFT.parent)
    }

    @Test
    fun `full-length zones are their own parent`() {
        listOf(EdgeZone.LEFT, EdgeZone.RIGHT, EdgeZone.TOP, EdgeZone.BOTTOM).forEach { edge ->
            assertEquals(edge, edge.parent)
            assertTrue(edge.isFullEdge)
        }
    }

    @Test
    fun `each edge has exactly three segments in visual order`() {
        assertEquals(
            listOf(EdgeZone.LEFT_TOP, EdgeZone.LEFT_CENTER, EdgeZone.LEFT_BOTTOM),
            EdgeZone.segmentsOf(EdgeZone.LEFT),
        )
        assertEquals(
            listOf(EdgeZone.BOTTOM_LEFT, EdgeZone.BOTTOM_CENTER, EdgeZone.BOTTOM_RIGHT),
            EdgeZone.segmentsOf(EdgeZone.BOTTOM),
        )
        assertTrue(EdgeZone.segmentsOf(EdgeZone.LEFT_TOP).isEmpty())
    }

    @Test
    fun `parentOf resolves every zone id`() {
        EdgeZone.entries.forEach { zone ->
            assertEquals(zone.parent.id, EdgeZone.parentOf(zone.id))
        }
    }

    // ------------------------------------------------------------ gestures

    @Test
    fun `gesture indices are contiguous from zero`() {
        // The gesture table is indexed by these values, so a gap would silently
        // shift every binding after it.
        assertEquals(
            Gesture.entries.indices.toList(),
            Gesture.entries.map(Gesture::index),
        )
    }

    @Test
    fun `key gesture indices are contiguous from zero`() {
        assertEquals(
            KeyGesture.entries.indices.toList(),
            KeyGesture.entries.map(KeyGesture::index),
        )
    }

    // -------------------------------------------------- gesture resolution

    @Test
    fun `a bound segment wins over its parent edge`() {
        val tap = Action.Simple(Id.HOME)
        val parentTap = Action.Simple(Id.BACK)
        val table = GestureTable()
            .with(EdgeZone.LEFT, Gesture.TAP, parentTap)
            .with(EdgeZone.LEFT_TOP, Gesture.TAP, tap)

        assertEquals(tap, table.resolve(EdgeZone.LEFT_TOP, Gesture.TAP))
        assertEquals(parentTap, table.resolve(EdgeZone.LEFT, Gesture.TAP))
    }

    @Test
    fun `an unbound segment falls back to its parent edge`() {
        val parentTap = Action.Simple(Id.BACK)
        val table = GestureTable().with(EdgeZone.LEFT, Gesture.TAP, parentTap)

        assertEquals(parentTap, table.resolve(EdgeZone.LEFT_CENTER, Gesture.TAP))
    }

    @Test
    fun `an explicit default binding falls back to the parent edge`() {
        val parentTap = Action.Simple(Id.BACK)
        val table = GestureTable()
            .with(EdgeZone.LEFT, Gesture.TAP, parentTap)
            .with(EdgeZone.LEFT_TOP, Gesture.TAP, Action.Default)

        assertEquals(parentTap, table.resolve(EdgeZone.LEFT_TOP, Gesture.TAP))
    }

    @Test
    fun `default on a full edge or a parent without an action resolves to no action`() {
        val table = GestureTable()
            .with(EdgeZone.LEFT, Gesture.TAP, Action.Default)
            .with(EdgeZone.RIGHT_TOP, Gesture.TAP, Action.Default)

        assertEquals(Id.NONE, table.resolve(EdgeZone.LEFT, Gesture.TAP).id)
        assertEquals(Id.NONE, table.resolve(EdgeZone.RIGHT_TOP, Gesture.TAP).id)
    }

    @Test
    fun `an unbound gesture resolves to no action`() {
        val table = GestureTable().with(EdgeZone.LEFT, Gesture.TAP, Action.Simple(Id.BACK))

        assertEquals(Id.NONE, table.resolve(EdgeZone.LEFT, Gesture.SWIPE_UP).id)
        assertEquals(Id.NONE, table.resolve(EdgeZone.RIGHT, Gesture.TAP).id)
    }

    @Test
    fun `a full-length edge does not fall back to a segment`() {
        // The fallback only ever goes segment to parent, never the other way.
        val table = GestureTable().with(EdgeZone.LEFT_TOP, Gesture.TAP, Action.Simple(Id.HOME))

        assertEquals(Id.NONE, table.resolve(EdgeZone.LEFT, Gesture.TAP).id)
    }

    @Test
    fun `binding no action clears the entry`() {
        val table = GestureTable()
            .with(EdgeZone.LEFT, Gesture.TAP, Action.Simple(Id.BACK))
            .with(EdgeZone.LEFT, Gesture.TAP, Action.None)

        assertFalse(table.bindings.containsKey(EdgeZone.LEFT.id))
        assertEquals(Id.NONE, table.resolve(EdgeZone.LEFT, Gesture.TAP).id)
    }

    @Test
    fun `clearing the last gesture removes the zone entry`() {
        val table = GestureTable()
            .with(EdgeZone.RIGHT, Gesture.TAP, Action.Simple(Id.HOME))
            .with(EdgeZone.RIGHT, Gesture.TAP, Action.None)

        assertTrue(table.bindings.isEmpty())
    }

    // -------------------------------------------------------- zone switches

    @Test
    fun `zones and their segments are off until their parent is explicitly enabled`() {
        val table = GestureTable()
        assertFalse(table.isZoneEnabled(EdgeZone.LEFT))
        assertFalse(table.isZoneEnabled(EdgeZone.LEFT_TOP))
        assertFalse(table.isZoneEnabled(EdgeZone.RIGHT))

        val enabled = table.withZoneEnabled(EdgeZone.LEFT, true)
        assertTrue(enabled.isZoneEnabled(EdgeZone.LEFT))
        assertTrue(enabled.isZoneEnabled(EdgeZone.LEFT_TOP))
        assertFalse(enabled.isZoneEnabled(EdgeZone.RIGHT))

        val disabled = enabled.withZoneEnabled(EdgeZone.LEFT, false)
        assertFalse(disabled.isZoneEnabled(EdgeZone.LEFT))
        assertFalse(disabled.isZoneEnabled(EdgeZone.LEFT_TOP))
        assertFalse(disabled.isZoneEnabled(EdgeZone.RIGHT))

        assertTrue(disabled.withZoneEnabled(EdgeZone.LEFT, true).isZoneEnabled(EdgeZone.LEFT))
        assertTrue(disabled.withZoneEnabled(EdgeZone.LEFT, true).isZoneEnabled(EdgeZone.LEFT_CENTER))
    }

    @Test
    fun `four switches independently control whole edge and three physical segments`() {
        val first = GestureTable().withZoneEnabled(EdgeZone.LEFT_CENTER, true)
        assertTrue(first.isZoneEnabled(EdgeZone.LEFT_CENTER))
        assertFalse(first.isZoneEnabled(EdgeZone.LEFT_TOP))
        assertFalse(first.isZoneEnabled(EdgeZone.LEFT_BOTTOM))
        assertFalse(first.isZoneEnabled(EdgeZone.LEFT))

        val all = first.withZoneEnabled(EdgeZone.LEFT, true)
        assertTrue(all.isZoneEnabled(EdgeZone.LEFT))
        val middleOff = all.withZoneEnabled(EdgeZone.LEFT_CENTER, false)
        assertFalse(middleOff.isZoneEnabled(EdgeZone.LEFT))
        assertFalse(middleOff.isZoneEnabled(EdgeZone.LEFT_CENTER))
        assertTrue(middleOff.isZoneEnabled(EdgeZone.LEFT_TOP))
        assertTrue(middleOff.isZoneEnabled(EdgeZone.LEFT_BOTTOM))
        assertFalse(middleOff.isZoneEnabled(EdgeZone.RIGHT_TOP))
        assertTrue(middleOff.withZoneEnabled(EdgeZone.LEFT_CENTER, true).isZoneEnabled(EdgeZone.LEFT))
    }

    @Test
    fun `v3 selected edges migrate to three selectable segments and preserve user actions`() {
        val old = EdgeConfig(
            version = 3,
            gestures = GestureTable(
                bindings = mapOf(EdgeZone.LEFT_CENTER.id to mapOf(
                    Gesture.TAP.index to Action.Simple(Id.BACK),
                    Gesture.SWIPE_RIGHT.index to Action.Simple(Id.HOME),
                )),
                enabledEdges = setOf(EdgeZone.LEFT.id),
            ),
            settings = AppSettings(gestureControlEnabled = true),
        )
        val migrated = old.migrateToCurrentVersion()
        assertEquals(4, migrated.version)
        assertTrue(migrated.gestures.isZoneEnabled(EdgeZone.LEFT))
        assertEquals(setOf(0, 1, 2), migrated.gestures.enabledZones)
        assertTrue(migrated.settings.gestureControlEnabled)
        assertEquals(old.gestures.bindings, migrated.gestures.bindings)
    }

    // --------------------------------------------------------- key bindings

    @Test
    fun `key bindings resolve per gesture`() {
        val table = KeyTable()
            .with(HardwareKey.VOLUME_UP, KeyGesture.CLICK, Action.Simple(Id.MEDIA_NEXT))
            .with(HardwareKey.VOLUME_UP, KeyGesture.LONG_PRESS, Action.Simple(Id.MEDIA_PREVIOUS))

        assertEquals(Id.MEDIA_NEXT, table.resolve(HardwareKey.VOLUME_UP, KeyGesture.CLICK).id)
        assertEquals(Id.MEDIA_PREVIOUS, table.resolve(HardwareKey.VOLUME_UP, KeyGesture.LONG_PRESS).id)
        assertEquals(Id.NONE, table.resolve(HardwareKey.VOLUME_UP, KeyGesture.DOUBLE_CLICK).id)
        assertEquals(Id.NONE, table.resolve(HardwareKey.VOLUME_DOWN, KeyGesture.CLICK).id)
    }

    @Test
    fun `keys are disabled until manually enabled`() {
        val table = KeyTable()
        assertFalse(table.isKeyEnabled(HardwareKey.VOLUME_UP))

        val disabled = table.withKeyEnabled(HardwareKey.VOLUME_UP, false)
        assertFalse(disabled.isKeyEnabled(HardwareKey.VOLUME_UP))
        assertTrue(disabled.withKeyEnabled(HardwareKey.VOLUME_UP, true).isKeyEnabled(HardwareKey.VOLUME_UP))
        assertFalse(disabled.withKeyEnabled(HardwareKey.VOLUME_UP, true).isKeyEnabled(HardwareKey.BACK))
    }

    @Test
    fun `fresh installation does not intercept gestures or hardware keys`() {
        val fresh = EdgeConfig()
        assertFalse(fresh.settings.gestureControlEnabled)
        assertFalse(fresh.settings.keyControlEnabled)
        EdgeZone.entries.forEach { assertFalse(fresh.gestures.isZoneEnabled(it)) }
        HardwareKey.entries.forEach { assertFalse(fresh.keys.isKeyEnabled(it)) }
    }

    @Test
    fun `previous on-by-default configuration is upgraded to safe opt-in without losing bindings`() {
        val old = EdgeConfig(
            version = 2,
            gestures = GestureTable(
                bindings = mapOf(EdgeZone.LEFT.id to mapOf(Gesture.SWIPE_UP.index to Action.Simple(Id.HOME))),
                disabledZones = setOf(EdgeZone.RIGHT.id),
            ),
            keys = KeyTable(bindings = mapOf(HardwareKey.VOLUME_UP.id to
                mapOf(KeyGesture.CLICK.index to Action.Simple(Id.MEDIA_NEXT)))),
            settings = AppSettings(gestureControlEnabled = true, keyControlEnabled = true),
        )
        val migrated = old.migrateToCurrentVersion()
        assertEquals(EdgeConfig.SCHEMA_VERSION, migrated.version)
        assertFalse(migrated.settings.gestureControlEnabled)
        assertFalse(migrated.settings.keyControlEnabled)
        assertFalse(migrated.gestures.isZoneEnabled(EdgeZone.LEFT))
        assertFalse(migrated.keys.isKeyEnabled(HardwareKey.VOLUME_UP))
        assertEquals(old.gestures.bindings, migrated.gestures.bindings)
        assertEquals(old.keys.bindings, migrated.keys.bindings)
        assertEquals(old.gestures.disabledZones, migrated.gestures.disabledZones)
    }

    // ----------------------------------------------------------- pie slots

    @Test
    fun `pie slots address both levels`() {
        var edge = PieEdge()
        edge = edge.withSlot(0, Action.Simple(Id.BACK))
        edge = edge.withSlot(PieConfig.LEVEL_1_SLOTS, Action.Simple(Id.HOME))

        assertEquals(Id.BACK, edge.slot(0).id)
        assertEquals(Id.HOME, edge.slot(PieConfig.LEVEL_1_SLOTS).id)
    }

    @Test
    fun `pie level one and level two do not overlap`() {
        val edge = PieEdge()
            .withSlot(PieConfig.LEVEL_1_SLOTS - 1, Action.Simple(Id.BACK))
            .withSlot(PieConfig.LEVEL_1_SLOTS, Action.Simple(Id.HOME))

        assertEquals(Id.BACK, edge.slot(PieConfig.LEVEL_1_SLOTS - 1).id)
        assertEquals(Id.HOME, edge.slot(PieConfig.LEVEL_1_SLOTS).id)
    }

    @Test
    fun `out of range pie slots are ignored rather than throwing`() {
        val edge = PieEdge()
        assertEquals(Id.NONE, edge.slot(-1).id)
        assertEquals(Id.NONE, edge.slot(PieConfig.SLOTS_PER_EDGE).id)
        assertEquals(Id.NONE, edge.slot(999).id)

        // Writing out of range must not corrupt the existing layout.
        val unchanged = edge.withSlot(999, Action.Simple(Id.BACK))
        assertEquals(Id.NONE, unchanged.slot(999).id)
    }

    @Test
    fun `each edge keeps its own pie layout`() {
        var config = PieConfig()
        config = config.withEdge(Edge.LEFT, PieEdge().withSlot(0, Action.Simple(Id.BACK)))

        assertEquals(Id.BACK, config.edge(Edge.LEFT).slot(0).id)
        assertEquals(Id.NONE, config.edge(Edge.RIGHT).slot(0).id)
    }

    // ---------------------------------------------------------- round trip

    @Test
    fun `a configuration survives serialisation unchanged`() {
        val original = EdgeConfig(
            gestures = GestureTable()
                .with(EdgeZone.LEFT_TOP, Gesture.TAP, Action.Simple(Id.HOME))
                .with(EdgeZone.RIGHT, Gesture.SWIPE_UP, Action.Sequence(listOf(
                    Action.Simple(Id.BACK),
                    Action.Delay(millis = 250),
                ))),
            keys = KeyTable()
                .with(HardwareKey.VOLUME_DOWN, KeyGesture.LONG_PRESS, Action.Shell("echo hi", asRoot = true)),
            pie = PieConfig().withEdge(Edge.BOTTOM, PieEdge().withSlot(3, Action.Toast("hello"))),
            stateTriggers = mapOf(
                StateTriggerId.SCREEN_OFF to Action.Toggle(mode = com.edgeflow.core.model.ToggleMode.OFF, id = Id.WIFI),
            ),
            variables = mapOf("mode" to "night"),
        )

        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), original)
        val decoded = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `nested action trees survive serialisation`() {
        val nested = Action.Conditional(
            condition = com.edgeflow.core.model.Condition.And(
                listOf(
                    com.edgeflow.core.model.Condition.ScreenOn,
                    com.edgeflow.core.model.Condition.Not(com.edgeflow.core.model.Condition.KeyguardOn),
                ),
            ),
            thenAction = Action.Repeat(
                times = 3,
                actions = listOf(
                    Action.Sequence(listOf(Action.Simple(Id.BACK), Action.Simple(Id.HOME))),
                    Action.SubGestures(swipeUp = Action.Toast("up")),
                ),
            ),
            elseAction = Action.Simple(Id.NONE),
        )

        val encoded = ConfigCodec.json.encodeToString(Action.serializer(), nested)
        assertEquals(nested, ConfigCodec.json.decodeFromString(Action.serializer(), encoded))
    }

    @Test
    fun `unknown keys in a stored document are ignored`() {
        // A document written by a newer build must still load, so a user who
        // downgrades keeps their configuration instead of losing it.
        val json = """
            {"version":1,"gestures":{"bindings":{},"disabledZones":[]},"somethingNew":42}
        """.trimIndent()

        val decoded = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), json)
        assertNotNull(decoded)
        assertEquals(1, decoded.version)
        assertEquals(EdgeConfig.SCHEMA_VERSION, decoded.migrateToCurrentVersion().version)
    }

    @Test
    fun `version one configuration migrates without losing bindings`() {
        val previous = EdgeConfig(
            version = 1,
            gestures = GestureTable().with(EdgeZone.LEFT, Gesture.TAP, Action.Simple(Id.HOME)),
        )

        val migrated = previous.migrateToCurrentVersion()

        assertEquals(EdgeConfig.SCHEMA_VERSION, migrated.version)
        assertEquals(Id.HOME, migrated.gestures.resolve(EdgeZone.LEFT, Gesture.TAP).id)
        assertTrue(migrated.shortcutPanels.edges.isEmpty())
    }

    @Test
    fun `future configuration versions are rejected by migration`() {
        val future = EdgeConfig(version = EdgeConfig.SCHEMA_VERSION + 1)

        kotlin.test.assertFailsWith<IllegalArgumentException> { future.migrateToCurrentVersion() }
    }

    // ------------------------------------------------------------ catalog

    @Test
    fun `every catalog entry has a unique id`() {
        val ids = ActionCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate action ids in the catalogue")
    }

    @Test
    fun `the catalog resolves titles for every declared action id`() {
        // The picker and every binding row call title(); a missing entry would
        // show a raw number to the user.
        val spotChecks = listOf(
            Id.NONE, Id.BACK, Id.HOME, Id.WIFI, Id.SHELL, Id.CONDITIONAL,
            Id.SUB_GESTURES, Id.SCREENSHOT_FULL, Id.TOAST,
        )
        spotChecks.forEach { id ->
            assertNotNull(ActionCatalog.spec(id), "no catalogue entry for action id $id")
        }
    }

    @Test
    fun `describing every action produces non-empty text`() {
        val samples = listOf(
            Action.Simple(Id.HOME),
            Action.Toggle(id = Id.WIFI),
            Action.LaunchApp(packageName = "com.example"),
            Action.Shell(command = "ls"),
            Action.Delay(millis = 100),
            Action.Sequence(listOf(Action.Simple(Id.BACK))),
            Action.Repeat(times = 2, actions = listOf(Action.Simple(Id.BACK))),
            Action.Conditional(),
            Action.SubGestures(),
            Action.Toast(text = "hi"),
        )
        samples.forEach { action ->
            assertTrue(ActionCatalog.describe(action).isNotBlank(), "empty description for $action")
        }
    }

    @Test
    fun `search matches on title case-insensitively`() {
        val results = ActionCatalog.search("wi-fi")
        assertTrue(results.any { it.id == Id.WIFI }, "search should find Wi-Fi by name")
    }

    @Test
    fun `an empty search returns the whole catalogue`() {
        assertEquals(ActionCatalog.all.size, ActionCatalog.search("").size)
    }
}
