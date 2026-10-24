package com.edgeflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.edgeflow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.edgeflow.core.GesturePlaybackPlan
import com.edgeflow.core.GestureTracePolicy
import com.edgeflow.core.model.GesturePlacement
import com.edgeflow.core.model.RecordedGesture
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Review, safely edit, preview and explicitly test a recorded multi-stroke trace. */
@Composable
fun GestureRecordingDetailDialog(
    record: RecordedGesture,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemoveStroke: (Int) -> Unit,
    onMoveStroke: (Int, Int, Int) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    onRecordAnother: () -> Unit,
    onTest: (Int, GesturePlacement, Int, Int) -> Unit,
) {
    var name by remember(record.id) { mutableStateOf(record.name) }
    var speed by remember(record.id) { mutableIntStateOf(100) }
    var placement by remember(record.id) { mutableStateOf(GesturePlacement.ORIGINAL) }
    var anchorX by remember(record.id) { mutableIntStateOf(50) }
    var anchorY by remember(record.id) { mutableIntStateOf(50) }
    var preview by remember(record.id) { mutableStateOf(false) }
    var playhead by remember(record.id) { mutableFloatStateOf(0f) }
    var confirmTest by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val strokes = GestureTracePolicy.strokes(record)
    val frames = remember(record, speed, placement, anchorX, anchorY) {
        runCatching {
            GesturePlaybackPlan.create(
                record, speed, placement,
                anchorXPercent = anchorX, anchorYPercent = anchorY,
            )
        }.getOrDefault(emptyList())
    }
    val pathFrames = remember(frames) {
        val completed = mutableListOf<Path>()
        val active = linkedMapOf<Int, Path>()
        frames.forEach { frame ->
            if (frame.phase == com.edgeflow.core.GestureEventPhase.DOWN ||
                frame.phase == com.edgeflow.core.GestureEventPhase.POINTER_DOWN
            ) {
                val point = frame.pointers[frame.changedIndex]
                active[point.id] = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
                    .also(completed::add)
            }
            frame.pointers.forEach { point ->
                active[point.id]?.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            if (frame.phase == com.edgeflow.core.GestureEventPhase.POINTER_UP ||
                frame.phase == com.edgeflow.core.GestureEventPhase.UP
            ) active.remove(frame.pointers[frame.changedIndex].id)
        }
        completed
    }
    val duration = frames.lastOrNull()?.elapsedMs?.coerceAtLeast(1) ?: 1
    LaunchedEffect(preview, speed, placement, record) {
        if (!preview) return@LaunchedEffect
        val started = android.os.SystemClock.uptimeMillis()
        do {
            val elapsed = android.os.SystemClock.uptimeMillis() - started
            playhead = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            delay(16)
        } while (elapsed < duration && preview)
        preview = false
    }
    val color = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Gesture preview", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Gesture name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${strokes.size} strokes · ${GestureTracePolicy.duration(record)} ms · " +
                        if (record.sourceRotation == -1) "Legacy recording" else "Rotation-aware",
                    style = MaterialTheme.typography.bodySmall,
                )
                Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                    val scaleX = size.width / 10000f
                    val scaleY = size.height / 10000f
                    pathFrames.forEachIndexed { index, original ->
                        withTransform({
                            scale(scaleX, scaleY, Offset.Zero)
                        }) {
                            drawPath(original, if (index % 2 == 0) color else accent,
                                style = Stroke(2.5.dp.toPx() / maxOf(scaleX, scaleY)))
                        }
                    }
                    if (preview) {
                        val time = (duration * playhead).roundToInt()
                        frames.lastOrNull { it.elapsedMs <= time }?.pointers?.forEach {
                            drawCircle(accent, radius = 7.dp.toPx(),
                                center = Offset(it.x * scaleX, it.y * scaleY))
                        }
                    }
                }
                Slider(
                    value = playhead,
                    onValueChange = { playhead = it; preview = false },
                    valueRange = 0f..1f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { playhead = 0f; preview = true }, enabled = frames.isNotEmpty()) {
                        Text("Preview motion")
                    }
                    Text("${(duration * playhead).toInt()} / $duration ms",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Playback speed: ${speed}%")
                Slider(value = speed.toFloat(), onValueChange = {
                    speed = (it / 25).roundToInt().coerceIn(2, 8) * 25
                    preview = false
                }, valueRange = 50f..200f, steps = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = placement == GesturePlacement.ORIGINAL,
                        onClick = { placement = GesturePlacement.ORIGINAL; preview = false },
                        label = { Text("Original position") },
                    )
                    FilterChip(
                        selected = placement == GesturePlacement.CENTER,
                        onClick = { placement = GesturePlacement.CENTER; preview = false },
                        label = { Text("Center on screen") },
                    )
                }
                FilterChip(
                    selected = placement == GesturePlacement.CUSTOM,
                    onClick = { placement = GesturePlacement.CUSTOM; preview = false },
                    label = { Text("Custom anchor") },
                )
                if (placement == GesturePlacement.CUSTOM) {
                    Text("Anchor X: ${anchorX}%")
                    Slider(value = anchorX.toFloat(), onValueChange = {
                        anchorX = it.roundToInt(); preview = false
                    }, valueRange = 0f..100f)
                    Text("Anchor Y: ${anchorY}%")
                    Slider(value = anchorY.toFloat(), onValueChange = {
                        anchorY = it.roundToInt(); preview = false
                    }, valueRange = 0f..100f)
                }
                Text("Stroke editor", style = MaterialTheme.typography.titleSmall)
                strokes.forEachIndexed { index, stroke ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stroke ${index + 1} · ${stroke.points.size} points",
                            modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(
                            enabled = strokes.size > 1,
                            onClick = { onRemoveStroke(index); preview = false },
                        ) { Text("Remove") }
                    }
                    Text("Move selected stroke by 2%", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onMoveStroke(index, -200, 0) }) { Text("←") }
                        TextButton(onClick = { onMoveStroke(index, 200, 0) }) { Text("→") }
                        TextButton(onClick = { onMoveStroke(index, 0, -200) }) { Text("↑") }
                        TextButton(onClick = { onMoveStroke(index, 0, 200) }) { Text("↓") }
                    }
                }
                Text("Device test injects real touches after a 5-second countdown. Switch to the target app. Use Emergency stop if needed.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { onRename(name.trim()); onDismiss() },
                    ) { Text("Save name") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmDelete = true }, enabled = canDelete) {
                        Text("Delete gesture")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { confirmTest = true }, enabled = frames.isNotEmpty()) {
                        Text("Test on device")
                    }
                }
                TextButton(onClick = { onRecordAnother(); onDismiss() }) {
                    Text("Record another gesture")
                }
            }
        }
    }
    if (confirmTest) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmTest = false },
            title = { Text("Confirm device playback") },
            text = { Text("EdgeFlow will inject the saved gesture in 5 seconds. Switch to your intended app now. Dispatch acceptance does not guarantee the app responds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmTest = false; onTest(speed, placement, anchorX, anchorY); onDismiss()
                }) {
                    Text("Start countdown")
                }
            },
            dismissButton = { TextButton(onClick = { confirmTest = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this gesture?") },
            text = { Text("This recording will be permanently deleted. Automations that reference it are protected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) {
                Text("Delete gesture")
            } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
