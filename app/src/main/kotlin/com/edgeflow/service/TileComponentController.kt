package com.edgeflow.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Changes actual Android QS tile components, not just the app-side settings
 * screen. The caller must warn the user before reducing an existing count.
 */
object TileComponentController {
    fun applyCount(context: Context, count: Int): Result<Unit> = runCatching {
        require(count in 0..12)
        val pm = context.packageManager
        val changed = mutableListOf<Pair<ComponentName, Int>>()
        try {
            for (index in 1..12) {
                val className = if (index == 1) {
                    "com.edgeflow.service.EdgeFlowTileService"
                } else "com.edgeflow.service.EdgeFlowTile$index"
                val name = ComponentName(context.packageName, className)
                val previous = pm.getComponentEnabledSetting(name)
                val desired = if (index <= count) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                if (previous != desired) {
                    pm.setComponentEnabledSetting(name, desired, PackageManager.DONT_KILL_APP)
                    changed.add(name to previous)
                }
            }
        } catch (failure: Throwable) {
            changed.asReversed().forEach { (component, original) ->
                runCatching { pm.setComponentEnabledSetting(component, original, PackageManager.DONT_KILL_APP) }
            }
            throw failure
        }
    }
}
