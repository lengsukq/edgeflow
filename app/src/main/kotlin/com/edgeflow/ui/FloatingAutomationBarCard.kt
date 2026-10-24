package com.edgeflow.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.edgeflow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.edgeflow.core.ActionId
import com.edgeflow.core.FloatingAutomationBarPolicy
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.data.ConfigRepository
import com.edgeflow.service.OverlayHostService
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.components.SettingRow

@Composable
fun FloatingAutomationBarCard(repository: ConfigRepository, config: EdgeConfig) {
    val context = LocalContext.current
    val settings = config.settings
    val chosen = settings.floatingAutomationBarActionIds
    var sizeDraft by remember(settings.floatingAutomationBarSizePercent) {
        mutableFloatStateOf(settings.floatingAutomationBarSizePercent.toFloat())
    }
    var opacityDraft by remember(settings.floatingAutomationBarOpacityPercent) {
        mutableFloatStateOf(settings.floatingAutomationBarOpacityPercent.toFloat())
    }
    var error by remember { mutableStateOf<String?>(null) }
    val available = config.savedMultiActions.filter { !it.deleted && it.action.id != ActionId.NONE }

    fun refreshOverlay() {
        if (!settings.floatingAutomationBarEnabled && !repository.current.settings.floatingAutomationBarEnabled) return
        runCatching { OverlayHostService.startFromUserAction(context) }
            .onFailure { error = "Could not start the overlay host: ${it.javaClass.simpleName}" }
    }

    SectionCard(
        title = "Floating automation shortcuts",
        subtitle = "Optional draggable edge handle, independent of Pie and Panel overlays. Tap to expand up to five saved tasks.",
    ) {
        SwitchRow(
            title = "Enable floating shortcut bar",
            subtitle = if (settings.floatingAutomationBarEnabled) "Active · drag to move, tap to expand"
                else "Off by default. Requires Android overlay permission.",
            checked = settings.floatingAutomationBarEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !Settings.canDrawOverlays(context)) {
                    error = "Grant Display over other apps, then return and enable again."
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }.onFailure { error = "Could not open overlay permission settings" }
                } else {
                    repository.update { current ->
                        current.copy(settings = current.settings.copy(floatingAutomationBarEnabled = enabled))
                    }
                    refreshOverlay()
                    error = null
                }
            },
        )
        RowDivider()
        SwitchRow(
            title = "Hide on lock screen",
            subtitle = "Do not cover Android authentication or the keyguard",
            checked = settings.floatingAutomationBarHideOnLockscreen,
            onCheckedChange = { enabled ->
                repository.update { it.copy(settings = it.settings.copy(floatingAutomationBarHideOnLockscreen = enabled)) }
            },
        )
        RowDivider()
        SwitchRow(
            title = "Hide in Game Mode",
            subtitle = "Avoid blocking game touches while Game Mode is enabled",
            checked = settings.floatingAutomationBarHideInGameMode,
            onCheckedChange = { enabled ->
                repository.update { it.copy(settings = it.settings.copy(floatingAutomationBarHideInGameMode = enabled)) }
            },
        )
        RowDivider()
        Column {
            Text("Handle size: ${(sizeDraft / 10).toInt() * 10}%")
            Slider(
                value = sizeDraft,
                onValueChange = { sizeDraft = it },
                onValueChangeFinished = {
                    repository.update {
                        it.copy(settings = it.settings.copy(
                            floatingAutomationBarSizePercent = (sizeDraft / 10).toInt().coerceIn(7, 15) * 10,
                        ))
                    }
                },
                valueRange = 70f..150f,
                steps = 7,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        Column {
            Text("Opacity: ${(opacityDraft / 10).toInt() * 10}%")
            Slider(
                value = opacityDraft,
                onValueChange = { opacityDraft = it },
                onValueChangeFinished = {
                    repository.update {
                        it.copy(settings = it.settings.copy(
                            floatingAutomationBarOpacityPercent = (opacityDraft / 10).toInt().coerceIn(4, 10) * 10,
                        ))
                    }
                },
                valueRange = 40f..100f,
                steps = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        SettingRow(
            title = "Reset handle position",
            subtitle = "Move the handle to the right edge at the default height",
            onClick = {
                context.getSharedPreferences("automation_bar_position", android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply()
                runCatching {
                    context.startService(
                        Intent(context, OverlayHostService::class.java)
                            .setAction(OverlayHostService.ACTION_RESET_BAR),
                    )
                }.onFailure { error = "Could not reset handle: ${it.message}" }
            },
        )
        if (available.isEmpty()) {
            Text("Create a saved automation first, then select it below.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        available.forEach { task ->
            RowDivider()
            SwitchRow(
                title = task.name.ifBlank { "Action ${task.id}" },
                subtitle = if (task.id in chosen) "Pinned to floating bar"
                    else if (chosen.size >= FloatingAutomationBarPolicy.MAX_SHORTCUTS) "Maximum 5 shortcuts"
                    else "Tap to add",
                checked = task.id in chosen,
                onCheckedChange = { checked ->
                    if (checked && task.id !in chosen &&
                        chosen.size >= FloatingAutomationBarPolicy.MAX_SHORTCUTS
                    ) {
                        error = "Choose at most five shortcuts. Remove one first."
                        return@SwitchRow
                    }
                    repository.update { current ->
                        current.copy(settings = current.settings.copy(
                            floatingAutomationBarActionIds = FloatingAutomationBarPolicy.toggle(
                                current.settings.floatingAutomationBarActionIds, task.id, checked,
                            ),
                        ))
                    }
                    refreshOverlay()
                },
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
