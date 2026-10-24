package com.edgeflow.data

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The supported modern-libxposed path for obtaining framework information in
 * the module's own app process. No package names or reflected XposedBridge
 * classes are needed (and hidden frameworks remain eligible).
 */
object FrameworkServiceState : XposedServiceHelper.OnServiceListener {
    data class Snapshot(
        val frameworkName: String,
        val frameworkVersion: String,
        val apiVersion: Int,
        val scope: Set<String>,
    )

    @Volatile private var current: Snapshot? = null
    @Volatile private var activeService: XposedService? = null
    private val serviceBoundListeners = CopyOnWriteArrayList<() -> Unit>()

    fun latest(): Snapshot? = current
    fun boundService(): XposedService? = activeService

    /** Notify the app's configuration mirror whenever LSPosed reconnects. */
    fun addServiceBoundListener(listener: () -> Unit) {
        serviceBoundListeners += listener
        if (activeService != null) listener()
    }

    override fun onServiceBind(service: XposedService) {
        activeService = service
        current = runCatching {
            Snapshot(
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                apiVersion = service.apiVersion,
                scope = service.scope.toSet(),
            )
        }.getOrNull()
        serviceBoundListeners.forEach { listener ->
            runCatching { listener() }
                .onFailure { android.util.Log.w("EdgeFlowConfig", "Framework sync listener failed", it) }
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (activeService !== service) return
        activeService = null
        current = null
    }
}
