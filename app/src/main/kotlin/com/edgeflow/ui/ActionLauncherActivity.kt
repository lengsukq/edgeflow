package com.edgeflow.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.edgeflow.EdgeFlowApp
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.Ipc
import com.edgeflow.core.model.Action

/**
 * Fires a saved action and exits.
 *
 * This exists so a saved multi-action can be triggered from outside the app —
 * from a launcher shortcut, a Tasker profile, or `adb shell am start`. The
 * activity is transparent and finishes immediately, so it reads as an action
 * rather than as opening the app.
 *
 * Only saved actions are reachable this way. Accepting an arbitrary serialised
 * action over an intent would let any app that can send one configure the module.
 */
class ActionLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as? EdgeFlowApp
        if (app != null && app.configRepository.current.settings.allowBroadcastTrigger) {
            val slot = intent.getIntExtra(Ipc.EXTRA_SLOT, -1)
            val saved = app.configRepository.current.savedMultiActions.firstOrNull { it.id == slot }
            if (saved != null && saved.action.id != com.edgeflow.core.ActionId.NONE) {
                // The action itself runs in the system server; hand it over by
                // broadcast so there is exactly one executor.
                val request = Intent(Ipc.PERFORM_ACTION)
                    .setPackage(Ipc.SYSTEM_PACKAGE)
                    .putExtra(Ipc.EXTRA_SLOT, slot)
                sendBroadcast(request)
            }
        }

        finish()
    }
}
