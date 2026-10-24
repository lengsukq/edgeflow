package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.edgeflow.core.AutomationHistory
import com.edgeflow.core.AutomationHistoryRecord
import com.edgeflow.core.Ipc
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * system_server sends only numeric step IDs and coarse failure descriptions.
 * The manifest requires signature-level DUMP permission from the sender.
 */
class AutomationHistoryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Ipc.AUTOMATION_HISTORY) return
        val endTime = intent.getLongExtra(Ipc.EXTRA_HISTORY_END_TIME, 0)
        if (endTime <= 0) return
        val record = AutomationHistory.sanitize(
            AutomationHistoryRecord(
                id = UUID.randomUUID().toString(),
                endedAtMillis = endTime,
                durationMillis = intent.getLongExtra(Ipc.EXTRA_HISTORY_DURATION, 0),
                origin = intent.getStringExtra(Ipc.EXTRA_AUTOMATION_ORIGIN).orEmpty(),
                state = intent.getStringExtra(Ipc.EXTRA_AUTOMATION_STATE).orEmpty(),
                dispatchedSteps = intent.getIntExtra(Ipc.EXTRA_AUTOMATION_STEPS, 0),
                failedSteps = intent.getIntExtra(Ipc.EXTRA_AUTOMATION_FAILURES, 0),
                recentSteps = intent.getStringExtra(Ipc.EXTRA_AUTOMATION_RECENT).orEmpty(),
            ),
        )
        AutomationHistoryStore.add(context, record)
    }
}

object AutomationHistoryStore {
    private const val PREFS = "automation_history"
    private const val KEY = "runs"
    private val serializer = ListSerializer(AutomationHistoryRecord.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun read(context: Context): List<AutomationHistoryRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw).take(AutomationHistory.MAX_RECORDS) }
            .getOrDefault(emptyList())
    }

    @Synchronized
    fun add(context: Context, record: AutomationHistoryRecord) {
        val updated = AutomationHistory.retainWith(read(context), record)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(serializer, updated)).commit()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
    }
}
