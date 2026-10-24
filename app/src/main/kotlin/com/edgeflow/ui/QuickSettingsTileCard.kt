package com.edgeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.data.ConfigRepository
import com.edgeflow.service.TileComponentController
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.RowDivider

@Composable
fun QuickSettingsTileCard(configRepository: ConfigRepository, config: EdgeConfig) {
    val context = LocalContext.current
    val currentCount = config.settings.effectiveTileCount()
    var showPicker by remember { mutableStateOf(false) }
    var showCountPicker by remember { mutableStateOf(false) }
    var pendingReduction by remember { mutableStateOf<Int?>(null) }
    var tileError by remember { mutableStateOf<String?>(null) }
    var selectedSlot by remember { mutableIntStateOf(1) }

    fun applyCount(next: Int) {
        TileComponentController.applyCount(context, next)
            .onSuccess {
                runCatching {
                    configRepository.update { current ->
                        current.copy(settings = current.settings.withTileCount(next))
                    }
                }.onFailure { error ->
                    TileComponentController.applyCount(context, currentCount)
                    tileError = "Could not save tile count: ${error.message}"
                }
            }
            .onFailure { tileError = "Could not change Android tiles: ${it.message}" }
    }

    SectionCard(
        title = "Quick Settings tiles",
        subtitle = "Choose 0–12 actual Android tiles. Reducing the count hides components but preserves their saved action bindings.",
    ) {
        SettingRow(
            title = "Active tiles: $currentCount",
            subtitle = "Choose how many independent tiles Android exposes (0–12)",
            onClick = { showCountPicker = true },
        )
        if (currentCount > 0) RowDivider()
        for (slot in 1..currentCount) {
            val actionId = config.settings.actionForTile(slot)
            val selected = config.savedMultiActions.firstOrNull {
                it.id == actionId
            }?.takeIf { it.action.id != ActionId.NONE }
            SettingRow(
                title = "Tile $slot · " + (selected?.name?.takeIf(String::isNotBlank) ?: "Not configured"),
                subtitle = selected?.let { ActionCatalog.describe(it.action) } ?: "Choose a saved action",
                onClick = {
                    selectedSlot = slot
                    showPicker = true
                },
            )
            if (slot != currentCount) RowDivider()
        }
    }
    CardSpacer()

    if (showCountPicker) {
        AlertDialog(
            onDismissRequest = { showCountPicker = false },
            title = { Text("Number of Quick Settings tiles") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
                    items(13) { count ->
                        SettingRow(
                            title = "$count tiles",
                            subtitle = if (count == currentCount) "Current setting" else
                                if (count < currentCount) "Requires confirmation" else "Enable more tiles",
                            onClick = {
                                showCountPicker = false
                                when {
                                    count == currentCount -> Unit
                                    count < currentCount -> pendingReduction = count
                                    else -> applyCount(count)
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCountPicker = false }) { Text("Close") } },
        )
    }

    pendingReduction?.let { next ->
        AlertDialog(
            onDismissRequest = { pendingReduction = null },
            title = { Text("Disable some system tiles?") },
            text = { Text("Android may remove disabled EdgeFlow tiles from Quick Settings. You may need to add them again later. Saved actions will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingReduction = null
                    applyCount(next)
                }) { Text("Disable extra tiles") }
            },
            dismissButton = { TextButton(onClick = { pendingReduction = null }) { Text("Cancel") } },
        )
    }

    tileError?.let { message ->
        AlertDialog(
            onDismissRequest = { tileError = null },
            title = { Text("Tile update failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { tileError = null }) { Text("Close") } },
        )
    }

    if (showPicker) {
        val actions = config.savedMultiActions.filter { it.action.id != ActionId.NONE }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Choose action for tile $selectedSlot") },
            text = {
                if (actions.isEmpty()) {
                    Text(
                        "Create a saved action before configuring this tile.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(actions, key = { it.id }) { item ->
                            SettingRow(
                                title = item.name.takeIf(String::isNotBlank) ?: "Saved action ${item.id}",
                                subtitle = ActionCatalog.describe(item.action),
                                onClick = {
                                    configRepository.update { current ->
                                        current.copy(
                                            settings = current.settings.withTileAction(selectedSlot, item.id),
                                        )
                                    }
                                    showPicker = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        configRepository.update { current ->
                            current.copy(settings = current.settings.withTileAction(selectedSlot, null))
                        }
                        showPicker = false
                    },
                ) {
                    Text("Clear selection")
                }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}
