package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AdjustStream
import com.edgeflow.core.model.EditTextCommand
import com.edgeflow.core.model.MediaCommand
import com.edgeflow.core.model.SlideAdjustTarget
import com.edgeflow.core.model.SuccessiveAdjustTarget
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionSerializationTest {

    @Test
    fun `shortcut panel edge survives configuration serialization`() {
        val action = Action.ShowShortcutPanel(edge = Edge.RIGHT.index)
        val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

        assertEquals(action, ConfigCodec.json.decodeFromString(Action.serializer(), encoded))
    }

    @Test
    fun `floating widget action preserves its host widget ID`() {
        val action = Action.FloatingWidget(widgetId = 42)
        val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

        assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
    }

    @Test
    fun `floating widget includes window settings and retains old JSON defaults`() {
        val action = Action.FloatingWidget(
            widgetId = 42, widthDp = 320, heightDp = 240,
            initialXPercent = 25, initialYPercent = 70,
            timeoutSeconds = 45, rememberLastPosition = false,
        )
        val json = ConfigCodec.json.encodeToString(Action.serializer(), action)
        assertEquals(action, ConfigCodec.json.decodeFromString<Action>(json))

        val old = """{"type":"floating_widget","widgetId":42}"""
        assertEquals(Action.FloatingWidget(widgetId = 42), ConfigCodec.json.decodeFromString<Action>(old))
    }

    @Test
    fun `legacy unconfigured floating widget action remains readable`() {
        val legacy = """{"type":"simple","id":${ActionId.FLOATING_WIDGET}}"""

        assertEquals(Action.Simple(ActionId.FLOATING_WIDGET), ConfigCodec.json.decodeFromString<Action>(legacy))
    }

    @Test
    fun `legacy shortcut panel action remains readable as the left edge`() {
        val legacy = """{"type":"simple","id":${ActionId.SHORTCUTS_PANEL}}"""

        assertEquals(
            Action.Simple(ActionId.SHORTCUTS_PANEL),
            ConfigCodec.json.decodeFromString<Action>(legacy),
        )
    }

    @Test
    fun `media control commands survive action serialization`() {
        MediaCommand.entries.forEach { command ->
            val action = Action.MediaControl(command)
            val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

            assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
        }
    }

    @Test
    fun `legacy media control action remains readable`() {
        val legacy = """{"type":"simple","id":${ActionId.MEDIA_CONTROL}}"""

        assertEquals(Action.Simple(ActionId.MEDIA_CONTROL), ConfigCodec.json.decodeFromString<Action>(legacy))
    }

    @Test
    fun `successive adjust targets and streams survive serialization`() {
        SuccessiveAdjustTarget.entries.forEach { target ->
            val action = Action.SuccessiveAdjust(
                target = target,
                increase = false,
                stream = AdjustStream.ALARM,
            )
            val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

            assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
        }
    }

    @Test
    fun `slide adjust targets and streams survive serialization`() {
        SlideAdjustTarget.entries.forEach { target ->
            val action = Action.SlideAdjust(target, AdjustStream.NOTIFICATION)
            val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

            assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
        }
    }

    @Test
    fun `edit text commands survive serialization`() {
        EditTextCommand.entries.forEach { command ->
            val action = Action.EditText(command)
            val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

            assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
        }
    }

    @Test
    fun `editable action round trips its nested action and custom label`() {
        val action = Action.EditableAction(
            innerAction = Action.LaunchActivity(
                packageName = "com.example",
                className = "com.example.MainActivity",
                data = "example://open",
            ),
            customLabel = "Open example",
        )
        val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

        assertTrue(encoded.contains("\"type\":\"editable_action\""))
        assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
    }

    @Test
    fun `editable action round trips without an optional custom label`() {
        val action = Action.EditableAction(innerAction = Action.Simple(ActionId.BACK))
        val encoded = ConfigCodec.json.encodeToString(Action.serializer(), action)

        assertEquals(action, ConfigCodec.json.decodeFromString<Action>(encoded))
    }

    @Test
    fun `legacy simple editable action remains simple instead of guessing a nested action`() {
        val legacy = """{"type":"simple","id":${ActionId.EDITABLE_ACTION}}"""

        assertEquals(Action.Simple(ActionId.EDITABLE_ACTION), ConfigCodec.json.decodeFromString<Action>(legacy))
    }

    @Test
    fun `legacy simple edit action remains readable without guessing a command`() {
        val legacy = """{"type":"simple","id":${ActionId.EDIT_TEXT}}"""

        assertEquals(Action.Simple(ActionId.EDIT_TEXT), ConfigCodec.json.decodeFromString<Action>(legacy))
    }
}
