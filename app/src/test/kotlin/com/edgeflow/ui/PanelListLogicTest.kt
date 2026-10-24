package com.edgeflow.ui

import com.edgeflow.core.ActionId
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.PanelConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PanelListLogicTest {

    @Test
    fun `new panel ids are nonnegative unique and do not depend on list position`() {
        assertEquals(0, nextPanelId(emptyList()))
        assertEquals(8, nextPanelId(listOf(PanelConfig(id = -2), PanelConfig(id = 2), PanelConfig(id = 7))))
        assertEquals(0, nextPanelId(listOf(PanelConfig(id = Int.MAX_VALUE))))
    }

    @Test
    fun `draft validation trims names and accepts supported grid dimensions`() {
        assertEquals(
            PanelDraftValidation.Valid(name = "Travel", rows = 8, columns = 1),
            validatePanelDraft("  Travel ", "8", "1", emptyList()),
        )
    }

    @Test
    fun `draft validation rejects blank duplicate and oversized names and invalid dimensions`() {
        val panels = listOf(PanelConfig(id = 4, name = "Travel"))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("  ", "3", "3", panels))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("travel", "3", "3", panels))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("x".repeat(41), "3", "3", panels))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("Other", "0", "3", panels))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("Other", "3", "9", panels))
        assertIs<PanelDraftValidation.Invalid>(validatePanelDraft("Other", "many", "3", panels))
        assertEquals(
            PanelDraftValidation.Valid("Travel", 3, 3),
            validatePanelDraft("Travel", "3", "3", panels, editingId = 4),
        )
    }

    @Test
    fun `updating a panel preserves its existing cell actions and list position`() {
        val first = PanelConfig(id = 2, name = "Old", cells = listOf(Action.Simple(ActionId.HOME)))
        val second = PanelConfig(id = 8, name = "Other")
        val updated = upsertPanel(
            listOf(first, second),
            PanelConfig(id = 2, name = "Renamed", rows = 4, columns = 2),
        )

        assertEquals(listOf("Renamed", "Other"), updated.map(PanelConfig::name))
        assertEquals(first.cells, updated.first().cells)
        assertEquals(4, updated.first().rows)
        assertEquals(2, updated.first().columns)
    }

    @Test
    fun `delete removes only the selected panel`() {
        val panels = listOf(PanelConfig(id = 2), PanelConfig(id = 8))
        assertEquals(listOf(PanelConfig(id = 8)), removePanel(panels, 2))
        assertEquals(panels, removePanel(panels, 99))
    }
}
