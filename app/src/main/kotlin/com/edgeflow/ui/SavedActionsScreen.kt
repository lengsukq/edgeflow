package com.edgeflow.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.Ipc
import com.edgeflow.ui.i18n.AppLanguage
import com.edgeflow.ui.i18n.UiTranslations
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.NamedAction
import com.edgeflow.data.ConfigRepository
import com.edgeflow.service.AutomationHistoryStore
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.EmptyState
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.ValueRow
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing

/**
 * Saved multi-actions.
 *
 * These are named action sequences that other bindings can invoke by reference.
 * They exist so a long sequence (for example "start navigation") can be edited
 * in one place and reused from a gesture, a key and a state trigger without
 * being duplicated three times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedActionsScreen(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var recentHistory by remember { mutableStateOf(AutomationHistoryStore.read(context)) }
    var showHistory by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NamedAction?>(null) }
    var renameText by remember { mutableStateOf("") }
    var runTarget by remember { mutableStateOf<NamedAction?>(null) }
    var runFeedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Saved actions") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val nextId = (config.savedMultiActions.maxOfOrNull { it.id } ?: 0) + 1
                    configRepository.update { current ->
                        current.copy(
                            savedMultiActions = current.savedMultiActions +
                                NamedAction(id = nextId, name = "Action $nextId"),
                        )
                    }
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New action") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = 96.dp,
            ),
        ) {
            item {
                SectionCard(
                    title = "Automation history",
                    subtitle = "Stored on this device · newest 80 runs · no text input or shell commands",
                ) {
                    com.edgeflow.ui.components.SettingRow(
                        title = "Refresh history",
                        subtitle = "Latest: " + (recentHistory.firstOrNull()?.state ?: "No runs yet"),
                        onClick = { recentHistory = AutomationHistoryStore.read(context) },
                    )
                    if (recentHistory.isNotEmpty()) {
                        com.edgeflow.ui.components.SettingRow(
                            title = "View recent runs",
                            subtitle = "${recentHistory.size} entries · dispatched is not confirmed success",
                            onClick = { showHistory = true },
                        )
                    }
                }
                CardSpacer()
            }
            val visibleActions = config.savedMultiActions.withIndex()
                .filterNot { it.value.deleted }
            if (visibleActions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No saved actions",
                        message = "Create one to reuse a sequence of steps from several triggers.",
                        icon = Icons.Outlined.Add,
                    )
                }
            }

            items(visibleActions.size) { visibleIndex ->
                val (index, saved) = visibleActions[visibleIndex]
                SectionCard(
                    title = saved.name.ifBlank { "Action ${saved.id}" },
                    subtitle = ActionCatalog.describe(saved.action),
                ) {
                    ValueRow(
                        title = "Steps",
                        value = if (saved.action.id == ActionId.NONE) {
                            "Not set"
                        } else {
                            ActionCatalog.describe(saved.action)
                        },
                        onClick = {
                            onPickAction(BindingFamily.SAVED_ACTION.id, saved.id, index)
                        },
                    )
                    ValueRow(
                        title = "Rename",
                        value = saved.name,
                        onClick = {
                            renameTarget = saved
                            renameText = saved.name
                        },
                    )
                    if (saved.action.id != ActionId.NONE) {
                        ValueRow(
                            title = "Test run",
                            value = "Run once · see latest execution diagnostics on Home",
                            onClick = { runTarget = saved },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = {
                                configRepository.update { current ->
                                    current.deleteSavedAction(saved.id)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete ${saved.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                CardSpacer()
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename action") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameText.trim()
                        configRepository.update { current ->
                            current.copy(
                                savedMultiActions = current.savedMultiActions.map {
                                    if (it.id == target.id) it.copy(name = newName) else it
                                },
                            )
                        }
                        renameTarget = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    runTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { runTarget = null },
            title = { Text("Run this automation?") },
            text = {
                Text("This can interact with other apps or change system settings. " +
                    "Review the steps and unlock your device before proceeding. " +
                    "Check Home → Last automation for dispatch and failure information.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val latest = configRepository.current.savedMultiActions
                        .firstOrNull { it.id == target.id && !it.deleted && it.action.id != ActionId.NONE }
                    if (latest == null) {
                        runFeedback = "This task was deleted or is no longer configured."
                    } else {
                        runCatching {
                            context.sendBroadcast(
                                Intent(Ipc.PERFORM_ACTION)
                                    .setPackage(Ipc.SYSTEM_PACKAGE)
                                    .putExtra(Ipc.EXTRA_SLOT, latest.id),
                            )
                        }.onSuccess {
                            runFeedback = "Execution requested. This is not confirmation that the task completed."
                        }.onFailure {
                            runFeedback = "Could not request execution: ${it.javaClass.simpleName}"
                        }
                    }
                    runTarget = null
                }) { Text("Run once") }
            },
            dismissButton = { TextButton(onClick = { runTarget = null }) { Text("Cancel") } },
        )
    }
    runFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { runFeedback = null },
            title = { Text("Automation request") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { runFeedback = null }) { Text("Close") } },
        )
    }
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Recent automation runs") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                    items(recentHistory.size) { index ->
                        val record = recentHistory[index]
                        Text(
                            "${java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.SHORT,
                                java.text.DateFormat.SHORT,
                                if (AppLanguage.effectiveLanguage(context) == AppLanguage.CHINESE)
                                    java.util.Locale.SIMPLIFIED_CHINESE else java.util.Locale.ENGLISH,
                            ).format(java.util.Date(record.endedAtMillis))} · " +
                                UiTranslations.forContext(context, record.state),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${record.origin} · ${record.durationMillis}ms · ${record.dispatchedSteps} dispatched · ${record.failedSteps} failed",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (record.recentSteps.isNotBlank()) {
                            Text(record.recentSteps, style = MaterialTheme.typography.bodySmall)
                        }
                        CardSpacer()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false; confirmClearHistory = true }) {
                    Text("Clear history")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistory = false }) { Text("Close") }
            },
        )
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Delete execution history?") },
            text = { Text("This removes saved run metadata only. Automations and their bindings are preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    AutomationHistoryStore.clear(context)
                    recentHistory = emptyList()
                    confirmClearHistory = false
                }) { Text("Delete history") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
            },
        )
    }
}
