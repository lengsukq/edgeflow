package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.edgeflow.EdgeFlowApp
import com.edgeflow.core.Ipc
import com.edgeflow.core.model.afterUnfreeze

/** Persists an unfreeze only after system_server confirms PackageManager succeeded. */
class FreezerUnfreezeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromUid.takeIf { it >= 0 }
        } else {
            null
        }
        if (intent.action != Ipc.UNFREEZE_APP_SUCCEEDED ||
            (senderUid != null && senderUid != Process.SYSTEM_UID)
        ) {
            android.util.Log.w(TAG, "rejected unfreeze result from uid ${senderUid ?: "permission-gated"}")
            return
        }

        val packageName = intent.getStringExtra(Ipc.EXTRA_PACKAGE)?.takeIf(String::isNotBlank) ?: return
        val app = context.applicationContext as? EdgeFlowApp ?: return
        try {
            app.configRepository.update { config ->
                config.copy(
                    freezer = config.freezer.afterUnfreeze(packageName, succeeded = true),
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "could not remove successfully unfrozen $packageName from freezer list", t)
        }
    }

    private companion object {
        const val TAG = "EdgeFlowFreezer"
    }
}
