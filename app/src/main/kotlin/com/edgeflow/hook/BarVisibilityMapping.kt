package com.edgeflow.hook

import com.edgeflow.core.StateTriggerId

/** Pure mapping for the legacy BarController and modern inset visibility hooks. */
internal object BarVisibilityMapping {
    private const val API_28 = 28
    private const val API_29 = 29

    enum class Bar { STATUS, NAVIGATION }

    data class Change(val bar: Bar, val visible: Boolean)

    fun legacyControllerClassName(apiLevel: Int): String? = when (apiLevel) {
        API_28 -> "com.android.server.policy.BarController"
        API_29 -> "com.android.server.wm.BarController"
        else -> null
    }

    /** Returns a visibility event only for a successful legacy state change. */
    fun legacyChange(
        apiLevel: Int,
        controllerClassName: String,
        controllerTag: String?,
        changed: Boolean?,
        showing: Boolean,
    ): Change? {
        if (changed != true || legacyControllerClassName(apiLevel) != controllerClassName) return null
        val bar = barForControllerTag(controllerTag) ?: return null
        return Change(bar, showing)
    }

    fun barForControllerTag(tag: String?): Bar? = when {
        tag?.endsWith("StatusBar") == true -> Bar.STATUS
        tag?.endsWith("NavigationBar") == true -> Bar.NAVIGATION
        else -> null
    }

    fun barForInsetType(type: Int): Bar? = when (type) {
        STATUS_BARS_TYPE -> Bar.STATUS
        NAVIGATION_BARS_TYPE -> Bar.NAVIGATION
        else -> null
    }

    private const val STATUS_BARS_TYPE = 1
    private const val NAVIGATION_BARS_TYPE = 2

    /** Prefer effective source state, falling back for framework versions without isVisible(). */
    fun effectiveSourceVisibility(sourceVisible: Boolean?, serverVisible: Boolean): Boolean =
        sourceVisible ?: serverVisible

    fun triggerId(change: Change): Int = when (change.bar) {
        Bar.STATUS -> if (change.visible) StateTriggerId.STATUS_BAR_SHOWN else StateTriggerId.STATUS_BAR_HIDDEN
        Bar.NAVIGATION -> if (change.visible) StateTriggerId.NAV_BAR_SHOWN else StateTriggerId.NAV_BAR_HIDDEN
    }
}
