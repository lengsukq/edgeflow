package com.edgeflow.hook

import android.content.SharedPreferences
import com.edgeflow.core.ConfigCodec
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads the user configuration from the module app and keeps it cached.
 *
 * The modern Xposed API exposes the module app's preferences as a
 * cross-process [SharedPreferences] facade, so the whole configuration travels
 * as one JSON document under [KEY_CONFIG]. That keeps the persisted format in
 * one place — [ConfigCodec] — and avoids inventing a wire protocol.
 *
 * Reads happen on the input path, which must stay cheap, so the parsed model is
 * held in an [AtomicReference] and only re-parsed when the stored document
 * actually changes. A parse failure keeps the previous good configuration:
 * a malformed edit must never disable the user's gestures.
 */
internal class ConfigRepository(private val module: XposedModule) {

    private val cached = AtomicReference(EdgeConfig())
    private val rawCache = AtomicReference<String?>(null)

    // The module is created before Android unlocks credential-encrypted app
    // data. Do NOT permanently remember a null RemotePreferences from boot:
    // retry on an app status probe after first unlock.
    @Volatile private var preferences: SharedPreferences? = null
    @Volatile private var preferenceFailureLogged = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KEY_CONFIG) reload()
    }
    private val configListeners = CopyOnWriteArrayList<(EdgeConfig) -> Unit>()

    @Synchronized
    private fun getPreferences(): SharedPreferences? {
        preferences?.let { return it }
        return try {
            module.getRemotePreferences(GROUP).also { prefs ->
                prefs.registerOnSharedPreferenceChangeListener(listener)
                preferences = prefs
                preferenceFailureLogged = false
                XLog.i("remote gesture configuration bridge connected")
            }
        } catch (t: Throwable) {
            if (!preferenceFailureLogged) {
                XLog.e("remote preferences unavailable before unlock; will retry", t)
                preferenceFailureLogged = true
            }
            null
        }
    }

    /** Current configuration. Never null. */
    val config: EdgeConfig get() = cached.get()

    /** Observes config replacements without making the input path poll shared preferences. */
    fun addConfigListener(listener: (EdgeConfig) -> Unit): () -> Unit {
        configListeners += listener
        return { configListeners -= listener }
    }

    /** Re-reads the stored document, keeping the last good value on failure. */
    @Synchronized
    fun reload() {
        val prefs = getPreferences() ?: return
        val raw = try {
            prefs.getString(KEY_CONFIG, null)
        } catch (t: Throwable) {
            XLog.w("remote gesture configuration read failed; keeping cached settings", t)
            return
        }
        if (raw == null) {
            // A temporary no-data response during user unlock must not erase
            // a previously loaded valid configuration.
            return
        }
        if (raw == rawCache.get()) return

        try {
            val updated = ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), raw).migrateToCurrentVersion()
            cached.set(updated)
            rawCache.set(raw)
            notifyConfigListeners(updated)
            XLog.d("configuration reloaded (${raw.length} chars)")
        } catch (t: Throwable) {
            XLog.e("configuration is unreadable; keeping the previous one", t)
        }
    }

    private fun notifyConfigListeners(config: EdgeConfig) {
        configListeners.forEach { callback ->
            runCatching { callback(config) }
                .onFailure { XLog.w("configuration listener failed: ${it.message}") }
        }
    }

    fun dispose() {
        preferences?.unregisterOnSharedPreferenceChangeListener(listener)
        preferences = null
        configListeners.clear()
    }

    companion object {
        /** Preference group name; must match the app side. */
        const val GROUP = "edgeflow"

        /** Single key holding the serialised [EdgeConfig]. */
        const val KEY_CONFIG = "config"
    }
}
