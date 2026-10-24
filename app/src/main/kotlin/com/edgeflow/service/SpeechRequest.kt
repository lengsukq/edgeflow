package com.edgeflow.service

import com.edgeflow.core.Ipc

/** Pure validation shared by the speech receiver and its focused unit tests. */
internal object SpeechRequest {
    const val EXTRA_TEXT = "speak"
    const val MAX_TEXT_LENGTH = 4_000
    private const val SYSTEM_UID = 1_000

    fun parse(action: String?, text: String?): String? {
        if (action != Ipc.PERFORM_ACTION) return null
        return text?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }
    }

    fun isTrustedSender(senderUid: Int): Boolean = senderUid == SYSTEM_UID
}
