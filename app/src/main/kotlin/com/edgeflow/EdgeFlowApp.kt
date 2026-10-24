package com.edgeflow

import android.app.Application
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.ModuleStatusProbe
import com.edgeflow.data.FrameworkServiceState
import io.github.libxposed.service.XposedServiceHelper
import com.edgeflow.service.SpeechController
import com.edgeflow.service.TileComponentController

/**
 * Application entry point.
 *
 * Holds the two objects that are genuinely process-scoped — the configuration
 * repository and the status probe — so screens receive them through the
 * ViewModel factory rather than reaching for a global. Everything else is
 * screen-scoped and lives in a ViewModel.
 */
class EdgeFlowApp : Application() {

    /** Configuration store shared by every screen. */
    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }

    /** Detects whether the module is live; cheap, so it is not cached. */
    val statusProbe: ModuleStatusProbe by lazy { ModuleStatusProbe(this) }

    /** Lazily owns app-process speech synthesis, never a system_server TTS instance. */
    private var speechController: SpeechController? = null

    @Synchronized
    internal fun speak(text: String) {
        val controller = speechController ?: SpeechController(this).also { speechController = it }
        controller.speak(text)
    }

    override fun onCreate() {
        super.onCreate()
        // Write a default document on first launch so the hook always has a
        // readable configuration, even before the user changes anything.
        configRepository.ensureInitialised()
        // Do not silently alter components for migrated legacy installs.
        if (!configRepository.requiresRecovery) {
            configRepository.current.settings.quickSettingsTileCount?.let { count ->
                TileComponentController.applyCount(this, count).onFailure {
                    android.util.Log.w("EdgeFlow", "Could not reconcile configured tiles", it)
                }
            }
        }
        FrameworkServiceState.addServiceBoundListener {
            configRepository.syncToFramework()
        }
        // Framework service is delivered independently of package visibility.
        XposedServiceHelper.registerListener(FrameworkServiceState)
    }

    override fun onTerminate() {
        speechController?.shutdown()
        super.onTerminate()
    }
}
