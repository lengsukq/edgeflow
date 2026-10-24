package com.edgeflow.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.edgeflow.EdgeFlowApp
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.Ipc

open class EdgeFlowTileService : TileService() {
    protected open val tileIndex: Int = 1

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (tileIndex > (edgeFlowApp()?.configRepository?.current?.settings?.effectiveTileCount() ?: 12)) return
        val actionId = configuredActionId() ?: return
        val savedAction = edgeFlowApp()?.configRepository?.current?.savedMultiActions
            ?.firstOrNull { it.id == actionId }
            ?.action
            ?.takeIf { it.id != ActionId.NONE }
            ?: return

        sendBroadcast(
            Intent(Ipc.PERFORM_ACTION)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_SLOT, actionId),
        )
        qsTile?.apply {
            label = "EdgeFlow $tileIndex"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = ActionCatalog.describe(savedAction)
            }
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun updateTileState() {
        val actionId = configuredActionId()
        val action = edgeFlowApp()?.configRepository?.current?.savedMultiActions
            ?.firstOrNull { it.id == actionId }
            ?.action
            ?.takeIf { it.id != ActionId.NONE }
        qsTile?.apply {
            label = "EdgeFlow $tileIndex"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = action?.let { ActionCatalog.describe(it) }
                    ?: getString(com.edgeflow.R.string.quick_settings_tile_unconfigured)
            }
            state = if (action == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun configuredActionId(): Int? = edgeFlowApp()?.configRepository?.current
        ?.settings?.actionForTile(tileIndex)

    private fun edgeFlowApp(): EdgeFlowApp? = application as? EdgeFlowApp
}
