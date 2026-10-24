package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.edgeflow.EdgeFlowApp

/** Receives system-server speech requests and hands them to the app-owned TTS controller. */
class SpeechCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val senderUid = senderUid()
        if (senderUid != null && !SpeechRequest.isTrustedSender(senderUid)) {
            Log.w(TAG, "rejected speech request from uid $senderUid")
            return
        }

        val text = try {
            SpeechRequest.parse(intent.action, intent.getStringExtra(SpeechRequest.EXTRA_TEXT))
        } catch (t: Throwable) {
            Log.w(TAG, "rejected malformed speech request", t)
            null
        } ?: return

        val app = context.applicationContext as? EdgeFlowApp ?: run {
            Log.w(TAG, "cannot handle speech request without EdgeFlow application")
            return
        }
        app.speak(text)
    }

    /** Older Android releases rely on the receiver's DUMP permission gate. */
    private fun senderUid(): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        sentFromUid.takeIf { it >= 0 }
    } else {
        null
    }

    private companion object {
        const val TAG = "EdgeFlowSpeech"
    }
}
