package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.edgeflow.EdgeFlowApp
import com.edgeflow.core.Ipc

/**
 * Only a sender holding android.permission.DUMP can reach this exported
 * receiver (system_server). Persisting the value updates the Settings screen
 * and the floating-bar visibility via the existing configuration StateFlow.
 */
class GameModeSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Ipc.GAME_MODE_CHANGED ||
            !intent.hasExtra(Ipc.EXTRA_GAME_MODE_ENABLED)
        ) return
        val enabled = intent.getBooleanExtra(Ipc.EXTRA_GAME_MODE_ENABLED, false)
        val repo = (context.applicationContext as? EdgeFlowApp)?.configRepository ?: return
        runCatching {
            if (repo.current.settings.gameMode != enabled) {
                repo.update { latest ->
                    latest.copy(settings = latest.settings.copy(gameMode = enabled))
                }
            }
        }.onFailure {
            android.util.Log.w("EdgeFlowGameMode", "Could not store Game Mode state", it)
        }
    }
}
