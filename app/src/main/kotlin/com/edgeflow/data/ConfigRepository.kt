package com.edgeflow.data

import android.content.Context
import android.content.SharedPreferences
import com.edgeflow.core.ConfigCodec
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.ScheduleEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import java.util.concurrent.Executors

/**
 * Persists the user configuration.
 *
 * ## Why a single JSON document
 *
 * The configuration is a deeply nested tree — gestures contain actions which
 * contain conditions which contain actions. Mapping that onto individual
 * preference keys would mean either flattening it (lossy) or inventing a
 * key-per-node scheme (unmaintainable). Storing one serialised document keeps
 * the schema in exactly one place, [EdgeConfig], and makes versioning
 * straightforward.
 *
 * ## How the hook sees it
 *
 * The file is the module app's own `SharedPreferences` group [PREFERENCES_NAME].
 * LSPosed exposes that group to the injected process through its remote
 * preferences bridge, which is why the hook can read the same document without
 * any IPC of our own.
 *
 * Writes are synchronous ([SharedPreferences.Editor.commit]) rather than
 * asynchronous: the user has just tapped a control and expects the change to be
 * live in system_server before they leave the screen.
 */
class ConfigRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val syncDiagnostics: SharedPreferences =
        context.applicationContext.getSharedPreferences("framework_sync_status", Context.MODE_PRIVATE)
    /** Serial queue prevents older local snapshots racing newer UI edits. */
    private val frameworkSyncExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "EdgeFlow-framework-config-sync").apply { isDaemon = true }
    }

    /** An unreadable document must never be overwritten by a routine toggle. */
    @Volatile private var unreadableConfiguration = false
    private val _config = MutableStateFlow(load())

    /** Observable configuration, for the UI to collect. */
    val config: StateFlow<EdgeConfig> = _config.asStateFlow()

    /** The most recent configuration value, for non-composable callers. */
    val current: EdgeConfig get() = _config.value

    /** True when the active JSON was unreadable. Mutations are blocked to avoid data loss. */
    val requiresRecovery: Boolean get() = unreadableConfiguration

    /** Keep one prior complete document for user-controlled rollback. */
    fun hasPreviousBackup(): Boolean = preferences.contains(KEY_PREVIOUS_CONFIG)

    /** The original bytes of the first pre-v4 document, never overwritten by UI toggles. */
    fun hasMigrationBackup(): Boolean = preferences.contains(KEY_PRE_MIGRATION_CONFIG)

    @Synchronized
    fun restoreMigrationBackup() {
        val raw = preferences.getString(KEY_PRE_MIGRATION_CONFIG, null)
            ?: error("There is no saved pre-upgrade configuration")
        val restored = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), raw)
            .migrateToCurrentVersion()
        persist(restored)
        unreadableConfiguration = false
        _config.value = restored
        syncToFramework()
    }

    /** Restores only after validating the backup; the failed active file is not silently applied. */
    @Synchronized
    fun restorePreviousBackup() {
        val json = preferences.getString(KEY_PREVIOUS_CONFIG, null)
            ?: error("There is no previous configuration backup")
        val restored = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), json)
            .migrateToCurrentVersion()
        // Do not replace the known-good snapshot with a corrupt active document.
        persist(restored, snapshotPrevious = !unreadableConfiguration)
        unreadableConfiguration = false
        _config.value = restored
        syncToFramework()
    }

    /**
     * Applies [transform] to the current configuration and persists the result.
     *
     * The transform runs against the newest value, so two quick edits cannot
     * clobber each other.
     */
    @Synchronized
    fun update(transform: (EdgeConfig) -> EdgeConfig) {
        check(!unreadableConfiguration) {
            "Saved configuration could not be loaded. Export or restore a backup before editing."
        }
        val next = transform(_config.value)
        persist(next)
        _config.value = next
    }

    /** Replaces the configuration wholesale, used by import and reset. */
    @Synchronized
    fun replace(next: EdgeConfig) {
        val migrated = next.migrateToCurrentVersion()
        persist(migrated)
        unreadableConfiguration = false
        _config.value = migrated
        syncToFramework()
    }

    /** Export the untouched damaged original rather than silently exporting fallback defaults. */
    fun exportJson(): String = if (requiresRecovery) {
        preferences.getString(KEY_CONFIG, null) ?: error("Missing damaged configuration")
    } else {
        ConfigCodec.json.encodeToString(EdgeConfig.serializer(), current)
    }

    /** Imports a backup without accepting a schema newer than this build understands. */
    fun importJson(json: String) {
        val imported = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), json).migrateToCurrentVersion()
        replace(imported)
    }

    private fun persist(config: EdgeConfig, snapshotPrevious: Boolean = !unreadableConfiguration) {
        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), config)
        val previous = preferences.getString(KEY_CONFIG, null)
        val editor = preferences.edit()
        // Keep exactly the prior JSON, including unknown fields or obsolete
        // actions, until the user explicitly restores or replaces it.
        if (snapshotPrevious && previous != null && previous != encoded) {
            editor.putString(KEY_PREVIOUS_CONFIG, previous)
        }
        check(editor.putString(KEY_CONFIG, encoded).commit()) {
            "Could not persist EdgeFlow configuration; in-memory state remains unchanged"
        }
        syncToFramework()
    }

    /**
     * App-local SharedPreferences and modern libxposed RemotePreferences are
     * DIFFERENT stores. The hooked system_server reads ONLY the latter, which
     * is owned by LSPosed. Mirror the committed local document via the
     * official app-side XposedService, never by reading the app XML as root.
     *
     * A listener in EdgeFlowApp calls this after service (re)connection.
     * UI writes also enqueue it, so changes propagate without reboot.
     */
    fun syncToFramework() {
        if (requiresRecovery) {
            syncDiagnostics.edit().putString("status", "recovery-required").apply()
            return
        }
        frameworkSyncExecutor.execute {
            if (requiresRecovery) return@execute
            val service = FrameworkServiceState.boundService() ?: run {
                syncDiagnostics.edit().putString("status", "waiting-for-xposed-service").apply()
                return@execute
            }
            val latest = preferences.getString(KEY_CONFIG, null) ?: return@execute
            syncDiagnostics.edit().putString("status", "sync-started")
                .putInt("source_bytes", latest.length).apply()
            try {
                val remote = service.getRemotePreferences(PREFERENCES_NAME)
                if (remote.getString(KEY_CONFIG, null) == latest) {
                    syncDiagnostics.edit().putString("status", "already-synced").apply()
                    return@execute
                }
                check(remote.edit().putString(KEY_CONFIG, latest).commit()) {
                    "LSPosed remote preferences commit failed"
                }
                syncDiagnostics.edit()
                    .putString("status", "synced")
                    .putLong("last_success_elapsed", android.os.SystemClock.elapsedRealtime())
                    .apply()
                android.util.Log.i("EdgeFlowConfig", "Synced configuration to LSPosed remote preferences")
            } catch (t: Throwable) {
                // Do not lose the user's existing local settings if LSPosed is
                // disconnected or unavailable. Retry automatically on bind.
                syncDiagnostics.edit()
                    .putString("status", "error")
                    .putString("error", "${t.javaClass.simpleName}: ${t.message}".take(300))
                    .apply()
                android.util.Log.w("EdgeFlowConfig", "Cannot sync configuration to LSPosed", t)
            }
        }
    }

    private fun load(): EdgeConfig {
        val raw = preferences.getString(KEY_CONFIG, null)
            ?: return EdgeConfig(settings = com.edgeflow.core.model.AppSettings(quickSettingsTileCount = 2))
        return try {
            val decoded = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), raw)
            decoded.migrateToCurrentVersion().also { migrated ->
                if (migrated.version != decoded.version) {
                    // Preserve the exact original migration input before
                    // rewriting. The ordinary one-step backup is overwritten
                    // by later toggles, so it is NOT sufficient for rollback.
                    if (!preferences.contains(KEY_PRE_MIGRATION_CONFIG)) {
                        check(preferences.edit()
                            .putString(KEY_PRE_MIGRATION_CONFIG, raw).commit()) {
                            "Unable to preserve original configuration before migration"
                        }
                    }
                    persist(migrated)
                }
            }
        } catch (_: SerializationException) {
            // A document from a newer build, or a corrupted file. Starting from
            // defaults is better than crashing on launch; the previous document
            // is left in place so it can be recovered manually.
            unreadableConfiguration = true
            EdgeConfig()
        } catch (_: IllegalArgumentException) {
            unreadableConfiguration = true
            EdgeConfig()
        }
    }

    /** Writes a default document so the hook has something to read on first run. */
    fun ensureInitialised() {
        if (!preferences.contains(KEY_CONFIG)) {
            persist(current)
        }
    }

    companion object {
        /**
         * Preference group name. Must match the group the hook requests from the
         * framework; changing it silently disconnects the two sides.
         */
        const val PREFERENCES_NAME = "edgeflow"

        /** Single key holding the serialised [EdgeConfig]. */
        const val KEY_CONFIG = "config"
        /** Prior, unchanged JSON snapshot, updated atomically with the active config. */
        const val KEY_PREVIOUS_CONFIG = "previous_config"
        const val KEY_PRE_MIGRATION_CONFIG = "pre_migration_config"
    }
}

/**
 * Convenience accessors for the parts of the configuration the UI edits most.
 *
 * These exist so screens do not repeat the same `update { it.copy(...) }`
 * boilerplate, and so the "which trigger family am I editing" question is
 * answered in one place.
 */
fun ConfigRepository.setGestureAction(zone: com.edgeflow.core.EdgeZone, gesture: com.edgeflow.core.Gesture, action: Action) =
    update { it.copy(gestures = it.gestures.with(zone, gesture, action)) }

fun ConfigRepository.setZoneEnabled(zone: com.edgeflow.core.EdgeZone, enabled: Boolean) =
    update { current ->
        val gestures = current.gestures.withZoneEnabled(zone, enabled)
        current.copy(
            gestures = gestures,
            settings = current.settings.copy(
                gestureControlEnabled = gestures.enabledZones.isNotEmpty(),
            ),
        )
    }

fun ConfigRepository.setKeyAction(key: com.edgeflow.core.HardwareKey, gesture: com.edgeflow.core.KeyGesture, action: Action) =
    update { it.copy(keys = it.keys.with(key, gesture, action)) }

fun ConfigRepository.setKeyEnabled(key: com.edgeflow.core.HardwareKey, enabled: Boolean) =
    update { current ->
        val keys = current.keys.withKeyEnabled(key, enabled)
        current.copy(
            keys = keys,
            settings = current.settings.copy(keyControlEnabled = keys.enabledKeys.isNotEmpty()),
        )
    }

fun ConfigRepository.setStateTrigger(triggerId: Int, action: Action) =
    update { it.copy(stateTriggers = it.stateTriggers + (triggerId to action)) }

/** The four lifecycle/focus actions stored in each app state's action list. */
enum class AppStateEvent(val index: Int, val label: String) {
    STARTED(0, "Started"),
    STOPPED(1, "Stopped"),
    FOCUS_GAINED(2, "Focus gained"),
    FOCUS_LOST(3, "Focus lost"),
}

/** Ensures a package has a four-slot app-state entry without changing the JSON shape. */
fun ConfigRepository.ensureAppStatePackage(packageName: String) {
    val normalizedPackageName = packageName.trim()
    if (normalizedPackageName.isEmpty()) return

    update { config ->
        if (normalizedPackageName in config.appStateTriggers) {
            config
        } else {
            config.copy(
                appStateTriggers = config.appStateTriggers +
                    (normalizedPackageName to List(AppStateEvent.entries.size) { null }),
            )
        }
    }
}

/** Writes one lifecycle/focus action at its established list index. */
fun ConfigRepository.setAppStateAction(packageName: String, event: AppStateEvent, action: Action) {
    val normalizedPackageName = packageName.trim()
    if (normalizedPackageName.isEmpty()) return

    update { config ->
        val actions = config.appStateTriggers[normalizedPackageName].orEmpty().toMutableList()
        while (actions.size <= event.index) actions.add(null)
        actions[event.index] = action.takeUnless { it.id == com.edgeflow.core.ActionId.NONE }
        config.copy(appStateTriggers = config.appStateTriggers + (normalizedPackageName to actions))
    }
}

fun ConfigRepository.removeAppStatePackage(packageName: String) =
    update { it.copy(appStateTriggers = it.appStateTriggers - packageName) }

fun ConfigRepository.addSchedule(schedule: ScheduleEntry) =
    update { it.copy(schedules = it.schedules + schedule) }

fun ConfigRepository.updateSchedule(schedule: ScheduleEntry) =
    update { config ->
        config.copy(schedules = config.schedules.map { current -> if (current.id == schedule.id) schedule else current })
    }

fun ConfigRepository.setScheduleEnabled(scheduleId: Int, enabled: Boolean) =
    update { config ->
        config.copy(schedules = config.schedules.map { schedule ->
            if (schedule.id == scheduleId) schedule.copy(enabled = enabled) else schedule
        })
    }

fun ConfigRepository.setScheduleAction(scheduleId: Int, action: Action) =
    update { config ->
        config.copy(schedules = config.schedules.map { schedule ->
            if (schedule.id == scheduleId) schedule.copy(action = action) else schedule
        })
    }

fun ConfigRepository.removeSchedule(scheduleId: Int) =
    update { it.copy(schedules = it.schedules.filterNot { schedule -> schedule.id == scheduleId }) }

fun ConfigRepository.setPieSlot(edge: com.edgeflow.core.Edge, slot: Int, action: Action) =
    update { config ->
        val currentEdge = config.pie.edge(edge)
        config.copy(pie = config.pie.withEdge(edge, currentEdge.withSlot(slot, action)))
    }

fun ConfigRepository.setSideBar(side: com.edgeflow.core.Edge, actions: List<Action>) =
    update { it.copy(sideBars = it.sideBars.with(side, actions)) }
