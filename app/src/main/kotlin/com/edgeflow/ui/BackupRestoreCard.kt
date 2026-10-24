package com.edgeflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import java.io.OutputStreamWriter

@Composable
fun BackupRestoreCard(configRepository: ConfigRepository) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            message = runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the selected destination.")
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write(configRepository.exportJson())
                }
                "Configuration backup exported."
            }.getOrElse { "Export failed: ${it.message ?: "unknown error"}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open the selected backup.")
                val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                configRepository.importJson(json)
                "Configuration backup restored."
            }.getOrElse { "Import failed: ${it.message ?: "invalid backup"}" }
        }
    }

    SectionCard(
        title = "Backup and restore",
        subtitle = "Export your configuration as a JSON file or restore a previous backup.",
    ) {
        SettingRow(
            title = "Export configuration",
            subtitle = "Save a portable JSON backup.",
            onClick = { exportLauncher.launch("edgeflow-backup.json") },
        )
        SettingRow(
            title = "Restore configuration",
            subtitle = "Replaces the current configuration after validation.",
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
        if (configRepository.hasPreviousBackup()) {
            SettingRow(
                title = "Restore previous local configuration",
                subtitle = "Restore the last successful settings snapshot (before your most recent edit/import).",
                onClick = {
                    message = runCatching {
                        configRepository.restorePreviousBackup()
                        "Previous configuration restored. Current bindings have been replaced."
                    }.getOrElse { "Could not restore previous configuration: ${it.message}" }
                },
            )
        }
        if (configRepository.requiresRecovery) {
            Text(
                "Stored configuration is unreadable; changes are disabled until you restore a valid backup. Your original data has been retained.",
            )
        }
    }

    message?.let { result ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Configuration backup") },
            text = { Text(result) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
