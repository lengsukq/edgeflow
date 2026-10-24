package com.edgeflow.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edgeflow.core.Ipc
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.theme.Spacing

@Composable
fun FreezerCard(configRepository: ConfigRepository, config: EdgeConfig) {
    val context = LocalContext.current
    var showApps by remember { mutableStateOf(false) }
    val frozenPackages = config.freezer.frozen
    val candidates = remember(frozenPackages) { installedLaunchableApps(context, frozenPackages) }

    SectionCard(
        title = "App freezer",
        subtitle = "Disable selected apps until you unfreeze them from the freezer drawer or this list.",
    ) {
        SettingRow(
            title = "Manage frozen apps",
            subtitle = "${frozenPackages.size} app${if (frozenPackages.size == 1) "" else "s"} selected",
            onClick = { showApps = true },
        )
        if (frozenPackages.isNotEmpty()) {
            SettingRow(
                title = "Refreeze selected apps",
                subtitle = "Re-applies the disabled state to every selected app.",
                onClick = { frozenPackages.forEach { sendFreezerCommand(context, Ipc.FREEZE_APP, it) } },
            )
        }
    }
    CardSpacer()

    if (showApps) {
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Apps to freeze") },
            text = {
                if (candidates.isEmpty()) {
                    Text("No launchable apps are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        candidates.forEach { app ->
                            val frozen = app.packageName in frozenPackages
                            SwitchRow(
                                title = app.label,
                                subtitle = app.packageName,
                                checked = frozen,
                                onCheckedChange = { shouldFreeze ->
                                    if (shouldFreeze) {
                                        configRepository.update { current ->
                                            val next = current.freezer.frozen + app.packageName
                                            current.copy(freezer = current.freezer.copy(frozen = next))
                                        }
                                    }
                                    sendFreezerCommand(
                                        context,
                                        if (shouldFreeze) Ipc.FREEZE_APP else Ipc.UNFREEZE_APP,
                                        app.packageName,
                                    )
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("Done") } },
        )
    }
}

private data class FreezerCandidate(val packageName: String, val label: String)

private fun installedLaunchableApps(context: Context, frozenPackages: Set<String>): List<FreezerCandidate> {
    val packageManager = context.packageManager
    return runCatching {
        packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter { it.packageName != context.packageName && it.packageName != Ipc.SYSTEM_PACKAGE }
            .filter { it.packageName in frozenPackages || packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { app ->
                FreezerCandidate(
                    packageName = app.packageName,
                    label = runCatching { packageManager.getApplicationLabel(app).toString() }
                        .getOrDefault(app.packageName),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())
}

private fun sendFreezerCommand(context: Context, action: String, packageName: String) {
    runCatching {
        context.sendBroadcast(
            Intent(action)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_PACKAGE, packageName),
        )
    }
}
