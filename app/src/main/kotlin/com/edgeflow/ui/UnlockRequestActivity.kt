package com.edgeflow.ui

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.edgeflow.core.UnlockDecision
import com.edgeflow.core.UnlockPolicy
import com.edgeflow.core.UnlockSnapshot
import com.edgeflow.core.Ipc
import com.edgeflow.ui.i18n.UiTranslations

/**
 * A visible, user-controlled gateway for Android's normal keyguard dismissal.
 *
 * Never injects a PIN, bypasses secure authentication, or reports that waking
 * the display was equivalent to unlocking it. The system owns all prompts.
 */
class UnlockRequestActivity : Activity() {
    private fun tr(value: String): String = UiTranslations.forContext(this, value)
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.edgeflow.ui.i18n.AppLanguage.wrap(newBase))
    }
    private lateinit var message: TextView
    private lateinit var requestButton: Button
    private lateinit var continueButton: Button
    private lateinit var manager: KeyguardManager
    private lateinit var power: PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private var inFlight = false
    private var finished = false
    private var generation = 0
    private var wakeChecks = 0

    private val timeout = Runnable {
        if (!finished && inFlight) {
            inFlight = false
            message.text = tr("Android did not confirm unlocking. You can try again or cancel.")
            requestButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(KeyguardManager::class.java)
        power = getSystemService(PowerManager::class.java)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 52, 36, 36)
        }
        layout.addView(TextView(this).apply {
            text = tr("EdgeFlow · Unlock request")
            textSize = 22f
        })
        message = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        layout.addView(message)
        requestButton = Button(this).apply {
            text = tr("Use Android unlock")
            setOnClickListener { requestDismissal() }
        }
        layout.addView(requestButton)
        continueButton = Button(this).apply {
            text = tr("Continue paused automation")
            isEnabled = false
            setOnClickListener {
                if (!UnlockPolicy.confirmedUnlocked(snapshot())) {
                    refresh()
                    return@setOnClickListener
                }
                runCatching {
                    sendBroadcast(Intent(Ipc.RESUME_AUTOMATION).setPackage(Ipc.SYSTEM_PACKAGE))
                }.onSuccess {
                    message.text = tr("Continuation requested. Check EdgeFlow's recent automation status to verify the result.")
                    continueButton.isEnabled = false
                    finished = true
                    handler.postDelayed({ if (!isFinishing) finish() }, 700L)
                }.onFailure {
                    message.text = tr("Unable to request continuation: ${it.javaClass.simpleName}")
                }
            }
        }
        layout.addView(continueButton)
        layout.addView(Button(this).apply {
            text = tr("Cancel")
            setOnClickListener { finish() }
        })
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (!finished && !inFlight) refresh()
    }

    private fun snapshot() = UnlockSnapshot(
        interactive = power.isInteractive,
        keyguardLocked = manager.isKeyguardLocked,
        deviceLocked = manager.isDeviceLocked,
        secure = manager.isKeyguardSecure,
    )

    private fun refresh() {
        when (UnlockPolicy.classify(snapshot())) {
            UnlockDecision.UNLOCKED -> {
                message.text = tr("Android confirmed: device is unlocked. Tap Continue to resume only the unfinished steps of a paused task (if any).")
                requestButton.isEnabled = false
                continueButton.isEnabled = true
            }
            UnlockDecision.WAKE_AND_RECHECK -> {
                continueButton.isEnabled = false
                message.text = tr("Screen is waking. Unlock remains unconfirmed.")
                requestButton.isEnabled = true
                if (++wakeChecks <= 8) {
                    handler.postDelayed({ if (!isFinishing && !finished) refresh() }, 300L)
                } else {
                    message.text = tr("Android has not reported an interactive screen. Check the display and try again.")
                }
            }
            UnlockDecision.REQUEST_DISMISSAL -> {
                continueButton.isEnabled = false
                message.text = tr("Non-secure keyguard detected. Android can dismiss it from this screen.")
                requestButton.isEnabled = true
                requestDismissal()
            }
            UnlockDecision.REQUIRE_AUTHENTICATION -> {
                continueButton.isEnabled = false
                message.text = tr("The device is securely locked. Complete the normal Android PIN, password, pattern or biometric authentication. EdgeFlow will not bypass it.")
                requestButton.isEnabled = true
            }
        }
    }

    private fun requestDismissal() {
        if (inFlight || finished) return
        // A fresh snapshot is essential: a wake gesture could have changed
        // the keyguard state without Android signaling completion yet.
        if (UnlockPolicy.confirmedUnlocked(snapshot())) {
            refresh()
            return
        }
        inFlight = true
        requestButton.isEnabled = false
        val requestGeneration = ++generation
        message.text = tr("Waiting for Android to confirm unlock or request authentication…")
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 20_000L)
        runCatching {
            manager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = completed(requestGeneration, null)
                override fun onDismissCancelled() = completed(requestGeneration, tr("Unlock cancelled by user or Android."))
                override fun onDismissError() = completed(requestGeneration, tr("Android rejected the unlock request."))
            })
        }.onFailure { completed(requestGeneration, tr("Cannot request Android unlock: ${it.javaClass.simpleName}")) }
    }

    private fun completed(requestGeneration: Int, error: String?) {
        runOnUiThread {
            if (isFinishing || finished || generation != requestGeneration) return@runOnUiThread
            handler.removeCallbacks(timeout)
            inFlight = false
            if (UnlockPolicy.confirmedUnlocked(snapshot())) {
                refresh()
            } else {
                message.text = error ?: tr("Dismissal callback received, but device is still locked.")
                requestButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
