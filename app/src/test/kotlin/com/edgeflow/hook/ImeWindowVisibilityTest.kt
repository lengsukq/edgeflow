package com.edgeflow.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImeWindowVisibilityTest {

    @Test
    fun `reads the first integer visibility argument after a binder token`() {
        assertEquals(3, ImeWindowVisibility.valueFrom(listOf("window-token", 3, 0)))
        assertEquals(2, ImeWindowVisibility.valueFrom(listOf(2, 0, "user-data")))
        assertNull(ImeWindowVisibility.valueFrom(listOf("window-token", "user-data")))
    }

    @Test
    fun `active flag alone is not visible`() {
        assertFalse(ImeWindowVisibility.isVisible(0))
        assertFalse(ImeWindowVisibility.isVisible(1))
        assertFalse(ImeWindowVisibility.isVisible(4))
    }

    @Test
    fun `visible bit is recognized with or without the active bit`() {
        assertTrue(ImeWindowVisibility.isVisible(2))
        assertTrue(ImeWindowVisibility.isVisible(3))
    }
}
