package com.edgeflow.core

/** Deliberately user-driven. Android 10+ forbids unfocused background clipboard reads. */
object ClipboardHistoryPolicy {
    const val MAX_ENTRIES = 30
    const val MAX_TEXT_LENGTH = 4_000

    fun append(existing: List<String>, candidate: String): List<String> {
        val value = candidate.take(MAX_TEXT_LENGTH)
        if (value.isBlank()) return existing.take(MAX_ENTRIES)
        return (listOf(value) + existing.filterNot { it == value }).take(MAX_ENTRIES)
    }

    fun remove(existing: List<String>, item: String): List<String> =
        existing.filterNot { it == item }
}
