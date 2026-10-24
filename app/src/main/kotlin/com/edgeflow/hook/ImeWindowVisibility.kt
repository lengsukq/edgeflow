package com.edgeflow.hook

/** Decodes the visibility bit from the version-varying IME hook arguments. */
internal object ImeWindowVisibility {
    private const val IME_VISIBLE = 0x2

    fun valueFrom(arguments: List<Any?>): Int? =
        arguments.firstOrNull { it is Int } as? Int

    fun isVisible(visibility: Int): Boolean = visibility and IME_VISIBLE != 0
}
