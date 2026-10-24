package com.edgeflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.i18n.Text

/** SAF keeps complete backups outside the app sandbox without storage permissions. */
@Composable
fun ConfigurationRecoveryCard(repository: ConfigRepository) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = repository.exportJson()
                context.contentResolver.openOutputStream(uri, "wt")
                    ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                    ?: error("Cannot open backup destination")
            }.onSuccess { feedback = "Backup exported. Keep it in a safe location." }
                .onFailure { feedback = "Export failed: ${it.message}" }
        }
    }
    val import = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Cannot read backup document")
                check(json.length <= 4_000_000) { "Backup exceeds the 4 MB safety limit" }
                repository.importJson(json)
            }.onSuccess { feedback = "Configuration restored. Recheck permissions and bindings." }
                .onFailure { feedback = "Import rejected: ${it.message}" }
        }
    }

    SectionCard(
        title = "Configuration backup & recovery",
        subtitle = "Export a full backup before major updates. Existing bindings and action IDs are preserved during migration.",
    ) {
        SettingRow(
            title = "Export configuration",
            subtitle = if (repository.requiresRecovery) "Exports unreadable original JSON for recovery"
                else "Save an editable JSON document using Android's file picker",
            onClick = { export.launch("EdgeFlow-backup.json") },
        )
        RowDivider()
        SettingRow(
            title = "Import configuration",
            subtitle = "Validate the file and back up the current document before replacing it",
            onClick = { import.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
        if (repository.hasPreviousBackup()) {
            RowDivider()
            SettingRow(
                title = "Restore previous edit",
                subtitle = "Undo the last saved configuration change (not task execution)",
                onClick = { pendingRestore = "previous" },
            )
        }
        if (repository.hasMigrationBackup()) {
            RowDivider()
            SettingRow(
                title = "Restore original pre-upgrade settings",
                subtitle = "Original JSON is preserved separately from ordinary edit history",
                onClick = { pendingRestore = "migration" },
            )
        }
        if (repository.requiresRecovery) {
            RowDivider()
            Text(
                "The current configuration could not be loaded. Export it before restoring a backup.",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    pendingRestore?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore saved configuration?") },
            text = { Text("The current settings and bindings will be replaced. Export a backup first if you need to keep them.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    runCatching {
                        if (target == "migration") repository.restoreMigrationBackup()
                        else repository.restorePreviousBackup()
                    }.onSuccess { feedback = "Configuration restored; verify all triggers and permissions." }
                        .onFailure { feedback = "Restore failed: ${it.message}" }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
    feedback?.let { message ->
        AlertDialog(
            onDismissRequest = { feedback = null },
            title = { Text("Configuration") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { feedback = null }) { Text("Close") } },
        )
    }
}
