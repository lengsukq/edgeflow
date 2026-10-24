package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.NamedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FloatingAutomationBarPolicyTest {
    @Test fun `deleted or unconfigured saved actions never appear in the quick bar`() {
        val config = EdgeConfig(
            settings = AppSettings(floatingAutomationBarActionIds = listOf(1, 2, 3, 1)),
            savedMultiActions = listOf(
                NamedAction(1, "Home", Action.Simple(ActionId.HOME)),
                NamedAction(2, "Deleted", Action.Simple(ActionId.BACK), deleted = true),
                NamedAction(3, "Unconfigured"),
            ),
        )
        assertEquals(listOf(1), FloatingAutomationBarPolicy.selected(config).map { it.id })
    }

    @Test fun `bar is opt in and limited to five stable IDs`() {
        assertFalse(AppSettings().floatingAutomationBarEnabled)
        val selected = (1..10).fold(emptyList<Int>()) { ids, id ->
            FloatingAutomationBarPolicy.toggle(ids, id, true)
        }
        assertEquals(listOf(1, 2, 3, 4, 5), selected)
        assertEquals(listOf(1, 3, 4, 5), FloatingAutomationBarPolicy.toggle(selected, 2, false))
    }
}
