package com.edgeflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.edgeflow.EdgeFlowApp
import android.provider.Settings

/**
 * Re-asserts the configuration after a boot or an app update.
 *
 * LSPosed reads the module's preferences when it injects, and the module app is
 * the only writer. After an update the stored document may have been produced by
 * an older schema, so this receiver normalises it once rather than leaving the
 * hook to cope with a stale shape on every read.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val app = context.applicationContext as? EdgeFlowApp ?: return
                val repository = app.configRepository
                // Loading the repository already performs migrations. Never
                // write the in-memory fallback defaults here: if active JSON
                // was corrupt, replace() would permanently destroy it on boot
                // or app upgrade, along with the user's recovery chance.
                if (!repository.requiresRecovery) {
                    repository.syncToFramework()
                    if (repository.current.settings.floatingAutomationBarEnabled &&
                        Settings.canDrawOverlays(context)
                    ) {
                        // Background FGS launches can be restricted by the ROM.
                        // Never crash the boot receiver; the user's next manual
                        // start of the host provides a safe recovery path.
                        runCatching {
                            context.startForegroundService(
                                Intent(context, OverlayHostService::class.java)
                                    .setAction(OverlayHostService.ACTION_START),
                            )
                        }.onFailure { android.util.Log.w("EdgeFlow", "Floating bar restore deferred until app foreground", it) }
                    }
                }
            }
        }
    }
}
