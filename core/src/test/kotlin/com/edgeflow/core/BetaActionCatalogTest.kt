package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BetaActionCatalogTest {
    @Test fun `deprecated actions are hidden only from creation and keep original IDs`() {
        assertFalse(ActionCatalog.betaSearch("").any { it.id == ActionId.IMMERSIVE_NAV_BAR })
        assertFalse(ActionCatalog.betaSearch("freezer").any { it.id == ActionId.FREEZER_DRAWER })
        assertTrue(ActionCatalog.betaSearch("delay").any { it.id == ActionId.DELAY })
        assertNotNull(ActionCatalog.spec(ActionId.IMMERSIVE_NAV_BAR))
        assertEquals(ActionId.IMMERSIVE_NAV_BAR, ActionCatalog.spec(ActionId.IMMERSIVE_NAV_BAR)?.id)
    }
}
