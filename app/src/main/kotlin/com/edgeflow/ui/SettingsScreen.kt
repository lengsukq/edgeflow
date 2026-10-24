package com.edgeflow.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.GameModePolicy
import com.edgeflow.core.Ipc
import com.edgeflow.core.GestureReferences
import com.edgeflow.core.GestureTracePolicy
import com.edgeflow.core.model.GesturePlacement
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.data.ConfigRepository
import com.edgeflow.service.OverlayHostService
import com.edgeflow.ui.UnlockRequestActivity
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.i18n.UiTranslations
import com.edgeflow.ui.theme.Spacing
import kotlin.math.roundToInt

/** Present related controls on focused second-level pages without resetting settings. */
private enum class SettingsCategory(val heading: String) {
    OVERVIEW("Settings"),
    GENERAL("Language & appearance"),
    PERMISSIONS("Permissions & services"),
    INPUT("Gestures & keys"),
    GAME("Game Mode"),
    DATA("Backup & advanced"),
}

/**
 * Global tunables.
 *
 * Geometry is expressed in inches rather than pixels because that is the unit
 * the user can actually reason about: "a fifth of an inch from the edge" means
 * the same thing on every phone. The screen shows the resulting pixel value as
 * a subtitle so the setting is not a black box.
 *
 * Sliders update the stored configuration continuously so the hook picks the
 * change up immediately, which lets the user tune the edge width by feel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(configRepository: ConfigRepository) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(SettingsCategory.OVERVIEW) }
    BackHandler(enabled = category != SettingsCategory.OVERVIEW) {
        category = SettingsCategory.OVERVIEW
    }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedRecordingId by remember { mutableStateOf<String?>(null) }
    val settings = config.settings
    val context = LocalContext.current
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (overlayAllowed) OverlayHostService.startFromUserAction(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.heading) },
                navigationIcon = {
                    if (category != SettingsCategory.OVERVIEW) {
                        IconButton(onClick = { category = SettingsCategory.OVERVIEW }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = UiTranslations.forContext(context, "Back"),
                            )
                        }
                    }
                },
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
                bottom = Spacing.xxl,
            ),
        ) {
            if (category == SettingsCategory.OVERVIEW) {
                item {
                    SectionCard(
                        title = "General",
                        subtitle = "Choose a section to manage. Existing settings and bindings are preserved.",
                    ) {
                        SettingRow(
                            title = "Language & appearance",
                            subtitle = "Choose your language and display preferences",
                            onClick = { category = SettingsCategory.GENERAL },
                        )
                        RowDivider()
                        SettingRow(
                            title = "Permissions & services",
                            subtitle = "Overlay access, notification listener, tiles and Android unlock",
                            onClick = { category = SettingsCategory.PERMISSIONS },
                        )
                        RowDivider()
                        SettingRow(
                            title = "Gestures & keys",
                            subtitle = "Edge regions, timing, recorded gestures and input behavior",
                            onClick = { category = SettingsCategory.INPUT },
                        )
                        RowDivider()
                        SettingRow(
                            title = "Game Mode",
                            subtitle = "Game touch passthrough and hardware key interception",
                            onClick = { category = SettingsCategory.GAME },
                        )
                        RowDivider()
                        SettingRow(
                            title = "Backup & advanced",
                            subtitle = "Import, export, recovery, emergency stop and reset",
                            onClick = { category = SettingsCategory.DATA },
                        )
                    }
                    CardSpacer()
                }
            }
            if (category == SettingsCategory.DATA) {
            item {
                SectionCard(
                    title = "Emergency stop",
                    subtitle = "Cancel running and queued automations without disabling normal Android touch or hardware keys.",
                ) {
                    SettingRow(
                        title = "Stop all automations",
                        subtitle = "Requests immediate cancellation. Long-running external shell commands may still need to exit.",
                        onClick = {
                            context.sendBroadcast(
                                Intent(Ipc.STOP_ALL).setPackage(Ipc.SYSTEM_PACKAGE),
                            )
                        },
                    )
                }
                CardSpacer()
            }
            }
            if (category == SettingsCategory.GENERAL) {
            item {
                LanguageSettingsCard()
                CardSpacer()
            }
            }
            if (category == SettingsCategory.DATA) {
            item {
                ConfigurationRecoveryCard(configRepository)
                CardSpacer()
            }
            }

            if (category == SettingsCategory.PERMISSIONS) {
            item {
                SectionCard(
                    title = "Floating overlays",
                    subtitle = "The app-side overlay host must remain enabled for Pie menus and panels to open over other apps.",
                ) {
                    SettingRow(
                        title = if (overlayAllowed) "Start overlay host" else "Grant overlay access",
                        subtitle = if (overlayAllowed) {
                            "Keeps the action windows available while EdgeFlow is in the background."
                        } else {
                            "Android requires special access to draw menus over other apps."
                        },
                        onClick = {
                            if (Settings.canDrawOverlays(context)) {
                                overlayAllowed = true
                                OverlayHostService.startFromUserAction(context)
                            } else {
                                val permissionIntent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                overlayPermissionLauncher.launch(permissionIntent)
                            }
                        },
                    )
                    RowDivider()
                    SettingRow(
                        title = "Stop overlay host",
                        subtitle = "Stops the foreground service and removes any open overlay.",
                        onClick = { OverlayHostService.stop(context) },
                    )
                    RowDivider()
                    SettingRow(
                        title = "Universal Copy & region screenshots",
                        subtitle = "Optional Android Accessibility permission. Only reads text or takes screenshots when you trigger these actions. Region screenshot requires Android 11+.",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
                CardSpacer()
            }

            item {
                QuickSettingsTileCard(configRepository, config)
            }


            item {
                SectionCard(
                    title = "Wake & unlock diagnostics",
                    subtitle = "Screen wake is not the same as unlocking. Android handles secure PIN, pattern, password or biometric authentication.",
                ) {
                    SettingRow(
                        title = "Test Android unlock flow",
                        subtitle = "Open the platform unlock request screen. This does not change your device credentials.",
                        onClick = {
                            context.startActivity(Intent(context, UnlockRequestActivity::class.java))
                        },
                    )
                }
                CardSpacer()
            }
            }

            if (category == SettingsCategory.INPUT) {
            item {
                val used = GestureReferences.usedIds(config)
                SectionCard(
                    title = "Recorded gestures",
                    subtitle = "Use the Gesture record action to capture a stroke. Bind one with Inject Gesture. Recordings currently used by any action are protected from deletion.",
                ) {
                    SettingRow(
                        title = "Record new gesture",
                        subtitle = "Draw one or two fingers across the overlay, with up to 12 consecutive strokes.",
                        onClick = {
                            if (Settings.canDrawOverlays(context)) {
                                context.startForegroundService(
                                    Intent(context, OverlayHostService::class.java)
                                        .setAction(Ipc.SHOW_OVERLAY)
                                        .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_GESTURE_RECORD),
                                )
                            } else {
                                overlayPermissionLauncher.launch(Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ))
                            }
                        },
                    )
                    if (config.recordedGestures.isEmpty()) {
                        Text("No recorded gestures")
                    }
                    config.recordedGestures.forEach { record ->
                        RowDivider()
                        SettingRow(
                            title = record.name,
                            subtitle = "${record.points.size} points · " +
                                "${record.points.lastOrNull()?.elapsedMs ?: 0} ms · " +
                                if (record.id in used) "In use · protected" else "Tap to delete",
                            onClick = { selectedRecordingId = record.id },
                        )
                    }
                }
                CardSpacer()
            }
            }

            if (category == SettingsCategory.PERMISSIONS) {
            item {
                FloatingWidgetManagementCard(config)
                CardSpacer()
            }

            item {
                NotificationAccessCard()
            }
            }

            if (category == SettingsCategory.GAME) {
            item {
                SectionCard(
                    title = "Game Mode · prevent accidental input",
                    subtitle = "Original Edge-style input guard. Edge choices pass through to the game; selected keys are intercepted. Leave Home and Back unselected unless needed.",
                ) {
                    SwitchRow(
                        title = "Enable game mode",
                        subtitle = "Ignore selected edge gestures and block selected hardware keys",
                        checked = settings.gameMode,
                        onCheckedChange = { on ->
                            configRepository.update { it.copy(settings = it.settings.copy(gameMode = on)) }
                        },
                    )
                    GameModePolicy.edges.forEachIndexed { bit, name ->
                        RowDivider()
                        SwitchRow(
                            title = "Ignore $name edge",
                            subtitle = "Let the foreground game handle touches on this edge",
                            checked = ((settings.gameIgnoreEdgesMask ushr bit) and 1) != 0,
                            onCheckedChange = { checked ->
                                configRepository.update {
                                    it.copy(settings = it.settings.copy(gameIgnoreEdgesMask =
                                        GameModePolicy.withBit(it.settings.gameIgnoreEdgesMask, bit, checked)))
                                }
                            },
                        )
                    }
                    GameModePolicy.keys.forEachIndexed { bit, name ->
                        RowDivider()
                        SwitchRow(
                            title = "Intercept $name",
                            subtitle = "Suppress this key only while Game Mode is on",
                            checked = ((settings.gameInterceptKeysMask ushr bit) and 1) != 0,
                            onCheckedChange = { checked ->
                                configRepository.update {
                                    it.copy(settings = it.settings.copy(gameInterceptKeysMask =
                                        GameModePolicy.withBit(it.settings.gameInterceptKeysMask, bit, checked)))
                                }
                            },
                        )
                    }
                }
                CardSpacer()
            }
            }

            // Freezer management and other Xposed Edge legacy tools are hidden
            // from the personal automation Beta. Keep persisted fields and
            // their original action IDs for non-destructive JSON migration.

            if (category == SettingsCategory.INPUT) {
            item {
                SectionCard(
                    title = "Gesture geometry",
                    subtitle = "Measured from the physical screen edge.",
                ) {
                    FloatSliderRow(
                        title = "Edge width",
                        subtitle = "Thickness of the left and right strips",
                        value = settings.edgeWidthInches,
                        range = 0.04f..0.4f,
                        steps = 35,
                        format = { formatInches(it) },
                        icon = Icons.Outlined.Straighten,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(edgeWidthInches = value)) }
                        },
                    )
                    RowDivider()
                    FloatSliderRow(
                        title = "Edge height",
                        subtitle = "Thickness of the top and bottom strips",
                        value = settings.edgeHeightInches,
                        range = 0.04f..0.4f,
                        steps = 35,
                        format = { formatInches(it) },
                        icon = Icons.Outlined.Straighten,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(edgeHeightInches = value)) }
                        },
                    )
                    RowDivider()
                    FloatSliderRow(
                        title = "Swipe threshold",
                        subtitle = "How far the finger must travel to count as a swipe",
                        value = settings.swipeThresholdInches,
                        range = 0.1f..0.8f,
                        steps = 34,
                        format = { formatInches(it) },
                        icon = Icons.Outlined.Gesture,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(swipeThresholdInches = value)) }
                        },
                    )
                }
                CardSpacer()
            }

            item {
                SectionCard(title = "Timing") {
                    LongSliderRow(
                        title = "Long press timeout",
                        value = settings.longPressTimeoutMillis,
                        range = 200L..1_200L,
                        step = 50L,
                        icon = Icons.Outlined.Timer,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(longPressTimeoutMillis = value)) }
                        },
                    )
                    RowDivider()
                    LongSliderRow(
                        title = "Double tap timeout",
                        subtitle = "A single tap waits this long before firing",
                        value = settings.doubleTapTimeoutMillis,
                        range = 150L..800L,
                        step = 25L,
                        icon = Icons.Outlined.Timer,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(doubleTapTimeoutMillis = value)) }
                        },
                    )
                    RowDivider()
                    LongSliderRow(
                        title = "Key double click timeout",
                        value = settings.keyDoubleClickTimeoutMillis,
                        range = 150L..800L,
                        step = 25L,
                        icon = Icons.Outlined.Keyboard,
                        onValueChange = { value ->
                            configRepository.update { it.copy(settings = it.settings.copy(keyDoubleClickTimeoutMillis = value)) }
                        },
                    )
                }
                CardSpacer()
            }

            item {
                SectionCard(title = "Behaviour") {
                    SwitchRow(
                        title = "Gesture control",
                        subtitle = "Master switch for all edge gestures",
                        checked = settings.gestureControlEnabled,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(gestureControlEnabled = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Key control",
                        subtitle = "Master switch for all key remapping",
                        checked = settings.keyControlEnabled,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(keyControlEnabled = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Trigger on lock screen",
                        subtitle = "Allow gestures and keys to fire while the device is locked",
                        checked = settings.triggerOnKeyguard,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(triggerOnKeyguard = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Trigger over the keyboard",
                        subtitle = "Allow gestures when a soft keyboard is showing",
                        checked = settings.triggerOnSoftKeyboard,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(triggerOnSoftKeyboard = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Allow broadcast triggers",
                        subtitle = "Let other apps fire actions with an intent. Off is safer.",
                        checked = settings.allowBroadcastTrigger,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(allowBroadcastTrigger = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Show gesture pointer",
                        subtitle = "Draw a dot that follows the finger during a gesture",
                        leadingIcon = Icons.Outlined.Gesture,
                        checked = settings.showGesturePointer,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(showGesturePointer = enabled)) }
                        },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Haptic feedback",
                        subtitle = "Vibrate briefly when a gesture fires",
                        leadingIcon = Icons.Outlined.Vibration,
                        checked = settings.vibrateOnTrigger,
                        onCheckedChange = { enabled ->
                            configRepository.update { it.copy(settings = it.settings.copy(vibrateOnTrigger = enabled)) }
                        },
                    )
                }
                CardSpacer()
            }
            }

            // The unified ConfigurationRecoveryCard above replaces the
            // duplicate legacy backup widget without deleting its data.

            if (category == SettingsCategory.DATA) {
            item {
                SectionCard(title = "Reset") {
                    SettingRow(
                        title = "Reset all settings to defaults",
                        subtitle = "Clears every gesture, key and trigger binding",
                        onClick = { showResetDialog = true },
                    )
                }
            }
            }
        }
    }

    selectedRecordingId?.let { id ->
        config.recordedGestures.firstOrNull { it.id == id }?.let { selected ->
            GestureRecordingDetailDialog(
                record = selected,
                onDismiss = { selectedRecordingId = null },
                canDelete = id !in GestureReferences.usedIds(config),
                onRename = { value ->
                    configRepository.update { state ->
                        state.copy(recordedGestures = state.recordedGestures.map {
                            if (it.id == id) it.copy(name = value) else it
                        })
                    }
                },
                onRemoveStroke = { index ->
                    configRepository.update { state ->
                        state.copy(recordedGestures = state.recordedGestures.map { current ->
                            if (current.id != id) return@map current
                            GestureTracePolicy.withoutStroke(current, index)
                        })
                    }
                },
                onMoveStroke = { index, dx, dy ->
                    configRepository.update { state ->
                        state.copy(recordedGestures = state.recordedGestures.map { current ->
                            if (current.id == id) GestureTracePolicy.moveStroke(current, index, dx, dy)
                            else current
                        })
                    }
                },
                onDelete = {
                    configRepository.update { state ->
                        if (id in GestureReferences.usedIds(state)) state
                        else state.copy(recordedGestures =
                            state.recordedGestures.filterNot { it.id == id })
                    }
                    selectedRecordingId = null
                },
                onRecordAnother = {
                    if (Settings.canDrawOverlays(context)) {
                        context.startForegroundService(
                            Intent(context, OverlayHostService::class.java)
                                .setAction(Ipc.SHOW_OVERLAY)
                                .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_GESTURE_RECORD),
                        )
                    }
                },
                onTest = { speed, placement, anchorX, anchorY ->
                    android.widget.Toast.makeText(context,
                        UiTranslations.forContext(context,
                            "Gesture test in 5 seconds. Switch to your target app."),
                        android.widget.Toast.LENGTH_LONG).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        context.sendBroadcast(
                            Intent(Ipc.PERFORM_ACTION).setPackage(Ipc.SYSTEM_PACKAGE)
                                .putExtra(Ipc.EXTRA_GESTURE_TEST_ID, id)
                                .putExtra(Ipc.EXTRA_GESTURE_TEST_SPEED, speed)
                                .putExtra(Ipc.EXTRA_GESTURE_TEST_PLACEMENT, placement.name)
                                .putExtra(Ipc.EXTRA_GESTURE_ANCHOR_X, anchorX)
                                .putExtra(Ipc.EXTRA_GESTURE_ANCHOR_Y, anchorY),
                        )
                    }, 5000L)
                },
            )
        } ?: run { selectedRecordingId = null }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset everything?") },
            text = {
                Text(
                    "This clears every gesture, key and trigger binding and restores the default " +
                        "timing and geometry. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        configRepository.replace(EdgeConfig())
                        showResetDialog = false
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** A slider row for a floating-point setting. */
@Composable
private fun FloatSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = format(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

/** A slider row for an integer setting expressed in milliseconds. */
@Composable
private fun LongSliderRow(
    title: String,
    value: Long,
    range: LongRange,
    step: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val span = (range.last - range.first).toFloat()
    val steps = ((span / step).toInt() - 1).coerceAtLeast(0)
    FloatSliderRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        steps = steps,
        format = { "${it.roundToInt()} ms" },
        onValueChange = { onValueChange(it.roundToInt().toLong()) },
        modifier = modifier,
    )
}

/** Formats an inch value to two decimals with the unit appended. */
private fun formatInches(value: Float): String =
    "${(value * 100).roundToInt() / 100f} in"
