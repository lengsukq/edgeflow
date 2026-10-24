package com.edgeflow.hook

import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.S)
internal object Api31TelephonyCallState {

    fun register(
        manager: TelephonyManager,
        executor: Executor,
        onStateChanged: (Int) -> Unit,
    ): () -> Unit {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onStateChanged(state)
        }
        manager.registerTelephonyCallback(executor, callback)
        return { manager.unregisterTelephonyCallback(callback) }
    }
}
