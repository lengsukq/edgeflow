package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipboardHistoryPolicyTest {
    @Test fun `empty capture does not alter history`() {
        assertEquals(listOf("old"), ClipboardHistoryPolicy.append(listOf("old"), " "))
    }

    @Test fun `reusing text moves it to front without duplicates`() {
        assertEquals(listOf("two", "one"), ClipboardHistoryPolicy.append(listOf("one", "two"), "two"))
    }

    @Test fun `history limits entries and text length`() {
        val history = (1..40).fold(emptyList<String>()) { acc, item ->
            ClipboardHistoryPolicy.append(acc, "item$item")
        }
        assertEquals(30, history.size)
        assertEquals("item40", history.first())
        val long = ClipboardHistoryPolicy.append(emptyList(), "a".repeat(5000)).first()
        assertEquals(4_000, long.length)
        assertTrue(ClipboardHistoryPolicy.remove(history, "item40").none { it == "item40" })
    }
}
