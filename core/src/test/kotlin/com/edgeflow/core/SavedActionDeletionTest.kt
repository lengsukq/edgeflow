package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.NamedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedActionDeletionTest {
    private fun sample() = EdgeConfig(
        savedMultiActions = listOf(
            NamedAction(id = 1, name = "first", action = Action.Simple(ActionId.HOME)),
            NamedAction(id = 2, name = "second", action = Action.Simple(ActionId.BACK)),
            NamedAction(id = 3, name = "third", action = Action.Simple(ActionId.MENU)),
        ),
    )

    @Test fun `deleting one task does not shift existing positional references`() {
        val after = sample().deleteSavedAction(2)
        assertEquals(3, after.savedMultiActions.size)
        assertEquals(1, after.savedMultiActions[0].id)
        assertEquals(ActionId.NONE, after.savedMultiActions[1].action.id)
        assertTrue(after.savedMultiActions[1].deleted)
        assertEquals(3, after.savedMultiActions[2].id)
        assertEquals(ActionId.MENU, after.savedMultiActions[2].action.id)
    }

    @Test fun `deleted slot cannot accidentally execute another saved action`() {
        val after = sample().deleteSavedAction(1)
        val appended = after.copy(savedMultiActions = after.savedMultiActions +
            NamedAction(id = 4, action = Action.Simple(ActionId.HOME)))
        assertTrue(appended.savedMultiActions[0].deleted)
        assertEquals(ActionId.NONE, appended.savedMultiActions[0].action.id)
        assertEquals(4, appended.savedMultiActions[3].id)
        assertFalse(appended.savedMultiActions[3].deleted)
    }

    @Test fun `tombstones persist across json export and import`() {
        val before = sample().deleteSavedAction(2)
        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), before)
        val after = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), encoded)
        assertEquals(before, after)
    }

    @Test fun `old configurations without tombstone field still decode`() {
        val legacy = sample()
        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), legacy)
        val decoded = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), encoded)
        assertTrue(decoded.savedMultiActions.all { !it.deleted })
    }
}
