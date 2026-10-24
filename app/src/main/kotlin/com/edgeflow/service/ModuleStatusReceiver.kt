package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.edgeflow.core.Ipc

/**
 * Framework-to-app-only confirmation that the module entered system_server.
 * This receiver has android.permission.DUMP at the manifest boundary, so an
 * ordinary third-party app cannot forge an active system-hook report.
 */
class ModuleStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Ipc.MODULE_STATUS_REPLY) return
        // Android can report INVALID_UID for some privileged/synthetic
        // broadcasts even when ActivityManager records system_server as UID
        // 1000. The manifest's android.permission.DUMP sender requirement
        // already rejects unprivileged apps. Do not rely on sentFromUid as a
        // second hard gate or valid system_server acknowledgements are lost.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getString(PENDING_NONCE, null) ?: run {
            android.util.Log.w("EdgeFlowStatus", "Received system reply with no pending challenge")
            return
        }
        val actual = intent.getStringExtra(Ipc.EXTRA_STATUS_NONCE) ?: run {
            android.util.Log.w("EdgeFlowStatus", "Received system reply with no challenge nonce")
            return
        }
        if (actual != expected) {
            android.util.Log.w("EdgeFlowStatus", "Rejected system reply: challenge mismatch")
            return
        }
        android.util.Log.i("EdgeFlowStatus", "Verified live system_server module response")
        prefs.edit()
            .remove(PENDING_NONCE)
            .remove(PENDING_STARTED_ELAPSED)
            .putLong(LAST_CONFIRMED_ELAPSED, SystemClock.elapsedRealtime())
            .putString(LAST_FRAMEWORK, intent.getStringExtra(Ipc.EXTRA_STATUS_FRAMEWORK)
                ?.take(120) ?: "Xposed (system_server)")
            .putInt(LAST_API, intent.getIntExtra(Ipc.EXTRA_STATUS_API, 0))
            .putString(LAST_STAGE, intent.getStringExtra(Ipc.EXTRA_STATUS_STAGE)?.take(100))
            .putBoolean(INPUT_HOOK, intent.getBooleanExtra(Ipc.EXTRA_INPUT_HOOK, false))
            .putBoolean(INPUT_BRIDGE, intent.getBooleanExtra(Ipc.EXTRA_INPUT_BRIDGE, false))
            .putBoolean(INPUT_GATE, intent.getBooleanExtra(Ipc.EXTRA_INPUT_GATE, false))
            .putInt(INPUT_VERSION, intent.getIntExtra(Ipc.EXTRA_INPUT_VERSION, 0))
            .putInt(INPUT_ZONES, intent.getIntExtra(Ipc.EXTRA_INPUT_ZONES, 0))
            .putInt(INPUT_BINDINGS, intent.getIntExtra(Ipc.EXTRA_INPUT_BINDINGS, 0))
            .putLong(INPUT_TOUCHES, intent.getLongExtra(Ipc.EXTRA_INPUT_TOUCHES, 0L))
            .putLong(INPUT_FIRED, intent.getLongExtra(Ipc.EXTRA_INPUT_FIRED, 0L))
            .putLong(INPUT_INJECTION_ATTEMPTS, intent.getLongExtra(Ipc.EXTRA_INPUT_INJECTION_ATTEMPTS, 0L))
            .putLong(INPUT_INJECTION_ACCEPTED, intent.getLongExtra(Ipc.EXTRA_INPUT_INJECTION_ACCEPTED, 0L))
            .putLong(INPUT_INJECTION_REJECTED, intent.getLongExtra(Ipc.EXTRA_INPUT_INJECTION_REJECTED, 0L))
            .putInt(INPUT_INJECTION_LAST_RESULT, intent.getIntExtra(Ipc.EXTRA_INPUT_INJECTION_LAST_RESULT, -1))
            .putLong(INPUT_EDGE_DOWNS, intent.getLongExtra(Ipc.EXTRA_INPUT_EDGE_DOWNS, 0L))
            .putLong(INPUT_ACTIVE_DOWNS, intent.getLongExtra(Ipc.EXTRA_INPUT_ACTIVE_DOWNS, 0L))
            .putLong(INPUT_KEYGUARD_BLOCKED, intent.getLongExtra(Ipc.EXTRA_INPUT_KEYGUARD_BLOCKED, 0L))
            .putString(AUTOMATION_STATE, intent.getStringExtra(Ipc.EXTRA_AUTOMATION_STATE)?.take(40))
            .putString(AUTOMATION_ORIGIN, intent.getStringExtra(Ipc.EXTRA_AUTOMATION_ORIGIN)?.take(72))
            .putInt(AUTOMATION_STEPS, intent.getIntExtra(Ipc.EXTRA_AUTOMATION_STEPS, 0))
            .putInt(AUTOMATION_FAILURES, intent.getIntExtra(Ipc.EXTRA_AUTOMATION_FAILURES, 0))
            .putString(AUTOMATION_RECENT, intent.getStringExtra(Ipc.EXTRA_AUTOMATION_RECENT)?.take(920))
            .apply()
    }

    companion object {
        const val PREFS = "module_runtime_status"
        const val PENDING_NONCE = "pending_nonce"
        const val PENDING_STARTED_ELAPSED = "pending_started_elapsed"
        const val LAST_CONFIRMED_ELAPSED = "last_confirmed_elapsed"
        const val LAST_FRAMEWORK = "last_framework"
        const val LAST_API = "last_api"
        const val LAST_STAGE = "system_server_stage"
        const val CONFIRMATION_MAX_AGE_MS = 60_000L
        const val INPUT_HOOK = "input_hook"
        const val INPUT_BRIDGE = "input_bridge"
        const val INPUT_GATE = "input_gate"
        const val INPUT_VERSION = "input_config_version"
        const val INPUT_ZONES = "input_enabled_zones"
        const val INPUT_BINDINGS = "input_bound_actions"
        const val INPUT_TOUCHES = "input_touch_events"
        const val INPUT_FIRED = "input_fired_gestures"
        const val INPUT_INJECTION_ATTEMPTS = "input_injection_attempts"
        const val INPUT_INJECTION_ACCEPTED = "input_injection_accepted"
        const val INPUT_INJECTION_REJECTED = "input_injection_rejected"
        const val INPUT_INJECTION_LAST_RESULT = "input_injection_last_result"
        const val INPUT_EDGE_DOWNS = "input_edge_downs"
        const val INPUT_ACTIVE_DOWNS = "input_active_downs"
        const val INPUT_KEYGUARD_BLOCKED = "input_keyguard_blocked"
        const val AUTOMATION_STATE = "automation_state"
        const val AUTOMATION_ORIGIN = "automation_origin"
        const val AUTOMATION_STEPS = "automation_steps"
        const val AUTOMATION_FAILURES = "automation_failures"
        const val AUTOMATION_RECENT = "automation_recent"
    }
}
