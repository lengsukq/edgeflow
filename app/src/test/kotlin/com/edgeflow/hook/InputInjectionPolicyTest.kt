package com.edgeflow.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputInjectionPolicyTest {
    @Test
    fun `injected events carry both filtered and delivery bits`() {
        val original = 0x00000002
        assertEquals(0x44000002, InputInjectionPolicy.markFiltered(original))
        assertEquals(original, InputInjectionPolicy.markFiltered(original) and 0x0000ffff)
    }

    @Test
    fun `filtered marker is idempotent`() {
        val once = InputInjectionPolicy.markFiltered(0)
        assertEquals(InputInjectionPolicy.FILTERED or InputInjectionPolicy.PASS_TO_USER, once)
        assertEquals(once, InputInjectionPolicy.markFiltered(once))
        assertTrue(once and InputInjectionPolicy.FILTERED != 0)
        assertTrue(once and InputInjectionPolicy.PASS_TO_USER != 0)
    }
}
