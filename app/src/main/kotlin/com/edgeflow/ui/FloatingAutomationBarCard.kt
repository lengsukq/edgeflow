package com.edgeflow.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.edgeflow.core.RadialMenuGeometry
import kotlin.math.cos
import kotlin.math.sin
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
    val radial = settings.floatingAutomationBarDisplayMode == "radial"
    val ringSlots = FloatingAutomationBarPolicy.ringSlots(config)
    var pickingRingSlot by remember { mutableStateOf<Int?>(null) }
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
        subtitle = "Optional draggable bubble with a list or radial shortcut menu. Off by default.",
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
            title = "Radial ring layout",
            subtitle = "Tap the bubble to open. Slide across a sector and release to run its task.",
            checked = radial,
            onCheckedChange = { useRadial ->
                repository.update { current -> current.copy(settings = current.settings.copy(
                    floatingAutomationBarDisplayMode = if (useRadial) "radial" else "list",
                )) }
                refreshOverlay()
            },
        )
        if (radial) {
            RowDivider()
            SwitchRow(
                title = "Three rings",
                subtitle = "Off: two rings (12 shortcuts). On: three rings (18 shortcuts).",
                checked = settings.floatingAutomationBarRingCount == 3,
                onCheckedChange = { useThree ->
                    repository.update { current -> current.copy(settings = current.settings.copy(
                        floatingAutomationBarRingCount = if (useThree) 3 else 2,
                    )) }
                },
            )
            RowDivider()
            SwitchRow(
                title = "Haptic sector feedback",
                subtitle = "Gently vibrate on changing to an assigned sector.",
                checked = settings.floatingAutomationBarRingHaptics,
                onCheckedChange = { enabled ->
                    repository.update { current -> current.copy(settings = current.settings.copy(
                        floatingAutomationBarRingHaptics = enabled,
                    )) }
                },
            )
        }
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
        if (radial) {
            RadialMenuPreview(ringSlots, settings.floatingAutomationBarRingCount.coerceIn(2, 3))
            Text("Preview: inner ring first, outer ring second. Long press and slide for a continuous gesture.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (ring in 0 until settings.floatingAutomationBarRingCount.coerceIn(2, 3)) {
                RowDivider()
                Text("Ring ${ring + 1}", color = MaterialTheme.colorScheme.primary)
                for (sector in 0 until FloatingAutomationBarPolicy.SECTORS_PER_RING) {
                    val index = ring * FloatingAutomationBarPolicy.SECTORS_PER_RING + sector
                    SettingRow(
                        title = "Sector ${sector + 1}",
                        subtitle = ringSlots.getOrNull(index)?.name ?: "Unassigned",
                        onClick = { pickingRingSlot = index },
                    )
                }
            }
        } else available.forEach { task ->
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
    val slotToEdit = pickingRingSlot
    if (slotToEdit != null) {
        AlertDialog(
            onDismissRequest = { pickingRingSlot = null },
            title = { Text("Choose shortcut") },
            text = {
                Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    SettingRow(
                        title = "Clear assignment",
                        subtitle = "Leave this sector inactive",
                        onClick = {
                            repository.update { current -> current.copy(settings = current.settings.copy(
                                floatingAutomationBarRingActionIds = FloatingAutomationBarPolicy.assignRingSlot(
                                    current.settings.floatingAutomationBarRingActionIds, slotToEdit, null,
                                ),
                            )) }
                            pickingRingSlot = null
                        },
                    )
                    available.forEach { task ->
                        SettingRow(
                            title = task.name.ifBlank { "Action ${task.id}" },
                            subtitle = if (ringSlots.getOrNull(slotToEdit)?.id == task.id) "Selected" else null,
                            onClick = {
                                repository.update { current -> current.copy(settings = current.settings.copy(
                                    floatingAutomationBarRingActionIds = FloatingAutomationBarPolicy.assignRingSlot(
                                        current.settings.floatingAutomationBarRingActionIds, slotToEdit, task.id,
                                    ),
                                )) }
                                pickingRingSlot = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingRingSlot = null }) { Text("Done") } },
        )
    }
}

/** Shows the actual sector order and saved task names without requiring overlay permission. */
@Composable
private fun RadialMenuPreview(slots: List<com.edgeflow.core.model.NamedAction?>, rings: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().height(286.dp)
            .background(Color(0xFF17243C), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(265.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension * 0.47f
            drawCircle(Color(0xFF253554), radius, Offset(cx, cy))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL)
                textSize = 10.dp.toPx()
            }
            for (ring in 0 until rings) {
                val band = RadialMenuGeometry.band(ring, rings)
                val mid = radius * (band.start + band.endInclusive) / 2f
                val stroke = radius * (band.endInclusive - band.start)
                for (sector in 0 until 6) {
                    val index = ring * 6 + sector
                    drawArc(
                        color = if (slots.getOrNull(index) != null) Color(0xFF6488DD) else Color(0xFF3B4E70),
                        startAngle = (RadialMenuGeometry.START_ANGLE + sector * 60 + 2).toFloat(),
                        sweepAngle = 56f,
                        useCenter = false,
                        topLeft = Offset(cx - mid, cy - mid),
                        size = Size(mid * 2, mid * 2),
                        style = Stroke(width = stroke),
                    )
                    val angle = RadialMenuGeometry.centerAngle(index)
                    val name = slots.getOrNull(index)?.name?.take(4) ?: "·"
                    drawContext.canvas.nativeCanvas.drawText(name,
                        cx + cos(angle).toFloat() * mid,
                        cy + sin(angle).toFloat() * mid + paint.textSize * 0.35f,
                        paint,
                    )
                }
            }
            drawCircle(Color(0xFF6F85E9), radius * 0.22f, Offset(cx, cy))
            paint.textSize = 14.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText("EF", cx, cy + paint.textSize * 0.35f, paint)
        }
    }
}
