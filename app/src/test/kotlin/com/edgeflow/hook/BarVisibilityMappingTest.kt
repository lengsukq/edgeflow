package com.edgeflow.hook

import com.edgeflow.core.StateTriggerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarVisibilityMappingTest {

    @Test
    fun `legacy controller class is selected for API 28 and 29 only`() {
        assertEquals(
            "com.android.server.policy.BarController",
            BarVisibilityMapping.legacyControllerClassName(28),
        )
        assertEquals(
            "com.android.server.wm.BarController",
            BarVisibilityMapping.legacyControllerClassName(29),
        )
        assertNull(BarVisibilityMapping.legacyControllerClassName(27))
        assertNull(BarVisibilityMapping.legacyControllerClassName(30))
    }

    @Test
    fun `legacy mapping classifies controller tags and maps showing booleans to shown triggers`() {
        val statusShown = BarVisibilityMapping.legacyChange(
            apiLevel = 28,
            controllerClassName = "com.android.server.policy.BarController",
            controllerTag = "StatusBar",
            changed = true,
            showing = true,
        )
        assertEquals(BarVisibilityMapping.Change(BarVisibilityMapping.Bar.STATUS, true), statusShown)
        assertEquals(StateTriggerId.STATUS_BAR_SHOWN, BarVisibilityMapping.triggerId(statusShown!!))

        val navigationShown = BarVisibilityMapping.legacyChange(
            apiLevel = 29,
            controllerClassName = "com.android.server.wm.BarController",
            controllerTag = "Display 0 NavigationBar",
            changed = true,
            showing = true,
        )
        assertEquals(BarVisibilityMapping.Change(BarVisibilityMapping.Bar.NAVIGATION, true), navigationShown)
        assertEquals(StateTriggerId.NAV_BAR_SHOWN, BarVisibilityMapping.triggerId(navigationShown!!))
    }

    @Test
    fun `legacy false showing booleans map to hidden triggers and remain dedup friendly`() {
        val statusHidden = BarVisibilityMapping.legacyChange(
            apiLevel = 28,
            controllerClassName = "com.android.server.policy.BarController",
            controllerTag = "StatusBar",
            changed = true,
            showing = false,
        )
        val navigationHidden = BarVisibilityMapping.legacyChange(
            apiLevel = 29,
            controllerClassName = "com.android.server.wm.BarController",
            controllerTag = "NavigationBar",
            changed = true,
            showing = false,
        )

        assertEquals(BarVisibilityMapping.Change(BarVisibilityMapping.Bar.STATUS, false), statusHidden)
        assertEquals(StateTriggerId.STATUS_BAR_HIDDEN, BarVisibilityMapping.triggerId(statusHidden!!))
        assertEquals(BarVisibilityMapping.Change(BarVisibilityMapping.Bar.NAVIGATION, false), navigationHidden)
        assertEquals(StateTriggerId.NAV_BAR_HIDDEN, BarVisibilityMapping.triggerId(navigationHidden!!))
    }

    @Test
    fun `legacy mapping ignores unchanged calls unknown tags and API class mismatches`() {
        assertNull(legacyChange(changed = false))
        assertNull(legacyChange(changed = null))
        assertNull(legacyChange(tag = "SystemBar"))
        assertNull(legacyChange(apiLevel = 29, className = "com.android.server.policy.BarController"))
        assertNull(legacyChange(apiLevel = 30))
    }

    @Test
    fun `inset types map status and navigation bars and ignore unrelated sources`() {
        assertEquals(BarVisibilityMapping.Bar.STATUS, BarVisibilityMapping.barForInsetType(1))
        assertEquals(BarVisibilityMapping.Bar.NAVIGATION, BarVisibilityMapping.barForInsetType(2))
        assertNull(BarVisibilityMapping.barForInsetType(3))
    }

    @Test
    fun `effective source visibility prefers the source and falls back to server visibility`() {
        assertEquals(true, BarVisibilityMapping.effectiveSourceVisibility(true, false))
        assertEquals(false, BarVisibilityMapping.effectiveSourceVisibility(false, true))
        assertEquals(true, BarVisibilityMapping.effectiveSourceVisibility(null, true))
        assertEquals(false, BarVisibilityMapping.effectiveSourceVisibility(null, false))
    }

    private fun legacyChange(
        apiLevel: Int = 28,
        className: String = "com.android.server.policy.BarController",
        tag: String? = "StatusBar",
        changed: Boolean? = true,
    ) = BarVisibilityMapping.legacyChange(
        apiLevel = apiLevel,
        controllerClassName = className,
        controllerTag = tag,
        changed = changed,
        showing = true,
    )
}
