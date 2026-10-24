package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AdjustStream
import com.edgeflow.core.model.EditTextCommand
import com.edgeflow.core.model.MediaCommand
import com.edgeflow.core.model.SlideAdjustTarget
import com.edgeflow.core.model.SuccessiveAdjustTarget
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActionCatalogTest {

    @Test
    fun `catalog has one presentation entry per persistent action id`() {
        val ids = ActionCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `legacy configurable action variants are selectable`() {
        listOf(
            ActionId.ACTIVITY_FINISH,
            ActionId.FINISH_ACTIVITY,
            ActionId.ACTIVITY_113,
            ActionId.ACTIVITY_114,
            ActionId.INJECT_KEY_EVENT_ALT,
            ActionId.INJECT_TEXT_ALT,
            ActionId.SAVED_MULTI_ACTION_ALT,
            ActionId.SHELL_SU,
        ).forEach { actionId ->
            assertNotNull(ActionCatalog.spec(actionId), "missing picker entry for action $actionId")
        }
    }

    @Test
    fun `floating widget requires a provider selection and describes its host ID`() {
        assertTrue(ActionCatalog.spec(ActionId.FLOATING_WIDGET)?.requiresConfig == true)
        assertEquals("Floating widget #42", ActionCatalog.describe(Action.FloatingWidget(42)))
    }

    @Test
    fun `media control requires setup and describes its selected command`() {
        assertTrue(ActionCatalog.spec(ActionId.MEDIA_CONTROL)?.requiresConfig == true)
        MediaCommand.entries.forEach { command ->
            assertEquals(
                "Media control: ${command.label}",
                ActionCatalog.describe(Action.MediaControl(command)),
            )
        }
    }

    @Test
    fun `edit text requires setup and describes the selected command`() {
        assertTrue(ActionCatalog.spec(ActionId.EDIT_TEXT)?.requiresConfig == true)
        EditTextCommand.entries.forEach { command ->
            assertEquals(
                "Edit text: ${command.label}",
                ActionCatalog.describe(Action.EditText(command)),
            )
        }
    }

    @Test
    fun `editable action requires setup and falls back to its inner action description`() {
        assertTrue(ActionCatalog.spec(ActionId.EDITABLE_ACTION)?.requiresConfig == true)

        val innerAction = Action.LaunchApp("com.example")
        assertEquals(
            ActionCatalog.describe(innerAction),
            ActionCatalog.describe(Action.EditableAction(innerAction)),
        )
        assertEquals(
            "Open example",
            ActionCatalog.describe(Action.EditableAction(Action.Simple(ActionId.BACK), "Open example")),
        )
        assertEquals(
            "Back",
            ActionCatalog.describe(Action.EditableAction(Action.Simple(ActionId.BACK), "  ")),
        )
        assertEquals(
            "Select and edit",
            ActionCatalog.describe(Action.Simple(ActionId.EDITABLE_ACTION)),
        )
    }

    @Test
    fun `successive adjust requires setup and describes target direction`() {
        assertTrue(ActionCatalog.spec(ActionId.SUCCESSIVE_ADJUST)?.requiresConfig == true)
        assertEquals(
            "Caret left",
            ActionCatalog.describe(
                Action.SuccessiveAdjust(SuccessiveAdjustTarget.CARET, increase = true),
            ),
        )
        assertEquals(
            "alarm volume down",
            ActionCatalog.describe(
                Action.SuccessiveAdjust(SuccessiveAdjustTarget.VOLUME, increase = false, stream = AdjustStream.ALARM),
            ),
        )
    }

    @Test
    fun `slide adjust requires setup and names its selected target`() {
        assertTrue(ActionCatalog.spec(ActionId.SLIDE_ADJUST)?.requiresConfig == true)
        assertEquals(
            "Slide adjust: notification volume",
            ActionCatalog.describe(Action.SlideAdjust(SlideAdjustTarget.VOLUME, AdjustStream.NOTIFICATION)),
        )
    }
}
