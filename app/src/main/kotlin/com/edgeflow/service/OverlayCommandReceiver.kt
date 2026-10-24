package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Process
import com.edgeflow.core.Ipc

/**
 * The receiver must remain exported for system_server broadcasts. Android
 * enforces the privileged DUMP sender permission in the manifest, including
 * on Android 9-13 where BroadcastReceiver.sentFromUid does not exist.
 * Sender UID checking here is an additional defense, not the sole gate.
 */
class OverlayCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Ipc.SHOW_OVERLAY || !isTrustedSender(context)) {
            android.util.Log.w(TAG, "rejected overlay command from uid ${senderUid()}")
            return
        }

        val serviceIntent = Intent(context, OverlayHostService::class.java)
            .setAction(Ipc.SHOW_OVERLAY)
            .putExtras(intent)
        try {
            context.startService(serviceIntent)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "overlay host is not running; start it from EdgeFlow settings first", t)
        }
    }

    private fun isTrustedSender(context: Context): Boolean {
        val uid = senderUid()
        return uid == Process.SYSTEM_UID || uid == context.applicationInfo.uid
    }

    private fun senderUid(): Int {
        val sentFromUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromUid
        } else {
            -1
        }
        return sentFromUid.takeIf { it >= 0 } ?: Binder.getCallingUid()
    }

    private companion object {
        const val TAG = "EdgeFlowOverlay"
    }
}
