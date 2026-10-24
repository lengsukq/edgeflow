package com.edgeflow.service

import com.edgeflow.core.Ipc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechRequestTest {

    @Test
    fun `only perform-action requests with nonblank text are parsed`() {
        assertNull(SpeechRequest.parse(null, "hello"))
        assertNull(SpeechRequest.parse("com.edgeflow.OTHER", "hello"))
        assertNull(SpeechRequest.parse(Ipc.PERFORM_ACTION, null))
        assertNull(SpeechRequest.parse(Ipc.PERFORM_ACTION, ""))
        assertNull(SpeechRequest.parse(Ipc.PERFORM_ACTION, " \n\t "))
    }

    @Test
    fun `valid speech text is preserved and bounded`() {
        val text = "  Read this aloud.  "
        assertEquals(text, SpeechRequest.parse(Ipc.PERFORM_ACTION, text))
        assertEquals(
            "x".repeat(SpeechRequest.MAX_TEXT_LENGTH),
            SpeechRequest.parse(Ipc.PERFORM_ACTION, "x".repeat(SpeechRequest.MAX_TEXT_LENGTH)),
        )
        assertNull(SpeechRequest.parse(Ipc.PERFORM_ACTION, "x".repeat(SpeechRequest.MAX_TEXT_LENGTH + 1)))
    }

    @Test
    fun `only system server is a trusted sender`() {
        assertTrue(SpeechRequest.isTrustedSender(1_000))
        assertFalse(SpeechRequest.isTrustedSender(10_123))
        assertFalse(SpeechRequest.isTrustedSender(-1))
    }
}
