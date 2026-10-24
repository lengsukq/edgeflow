package com.edgeflow.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.StateTriggerId
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.ScheduleEntry
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.components.ValueRow
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing

/**
 * System state triggers.
 *
 * The trigger list is long and heterogeneous — screen events, connectivity,
 * power, calls — so it is filtered by group rather than shown flat. The groups
 * follow how a user reasons about them ("things about power", "things about the
 * screen"), not the internal trigger ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateTriggersScreen(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int, target: String?) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    var selectedGroup by remember { mutableIntStateOf(0) }
    val groups = remember { TriggerGroup.entries.toList() }
    val activeGroup = groups[selectedGroup]

    Scaffold(
        topBar = { TopAppBar(title = { Text("System triggers") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                groups.forEachIndexed { index, group ->
                    SegmentedButton(
                        selected = selectedGroup == index,
                        onClick = { selectedGroup = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = groups.size),
                        label = { Text(group.label) },
                    )
                }
            }

            when (activeGroup) {
                TriggerGroup.APP_STATE -> AppStateTriggerContent(configRepository, onPickAction)
                TriggerGroup.SCHEDULE -> ScheduleTriggerContent(configRepository, onPickAction)
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.xxl,
                    ),
                ) {
                    item {
                        SectionCard(
                            title = activeGroup.label,
                            subtitle = activeGroup.description,
                        ) {
                            activeGroup.triggers.forEachIndexed { index, trigger ->
                                if (index > 0) RowDivider()
                                val action = config.stateTriggers[trigger.id]
                                ValueRow(
                                    title = trigger.label,
                                    value = if (action == null || action.id == ActionId.NONE) {
                                        "Not set"
                                    } else {
                                        ActionCatalog.describe(action)
                                    },
                                    leadingIcon = trigger.icon,
                                    onClick = {
                                        onPickAction(BindingFamily.STATE_TRIGGER.id, trigger.id, 0, null)
                                    },
                                )
                            }
                        }
                        CardSpacer()
                    }

                    item {
                        SectionCard(
                            title = "Note",
                            subtitle = "These triggers fire from the system server, so they work even " +
                                "when no app is in the foreground. Triggers that need telephony or " +
                                "audio state are only available when the corresponding service is running.",
                        ) {
                            Spacer(Modifier.height(Spacing.sm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStateTriggerContent(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int, target: String?) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    val labels = remember {
        listOf("App started", "App stopped", "Gained focus", "Lost focus")
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xxl,
        ),
    ) {
        item {
            SectionCard(
                title = "Per-app triggers",
                subtitle = "Choose apps and bind actions to their lifecycle and focus transitions.",
            ) {
                SettingRow(
                    title = "Add app",
                    subtitle = "Select an installed launchable app",
                    onClick = { showAppPicker = true },
                )
            }
            CardSpacer()
        }

        items(config.appStateTriggers.toList().sortedBy { appEntryLabel(context, it.first).lowercase() }, key = { it.first }) { (packageName, actions) ->
            SectionCard(
                title = appEntryLabel(context, packageName),
                subtitle = packageName,
            ) {
                labels.forEachIndexed { eventIndex, label ->
                    if (eventIndex > 0) RowDivider()
                    val action = actions.getOrNull(eventIndex)
                    ValueRow(
                        title = label,
                        value = if (action == null || action.id == ActionId.NONE) {
                            "Not set"
                        } else {
                            ActionCatalog.describe(action)
                        },
                        onClick = {
                            onPickAction(BindingFamily.APP_STATE.id, eventIndex, 0, packageName)
                        },
                    )
                }
                RowDivider()
                SettingRow(
                    title = "Remove app trigger",
                    subtitle = "Keeps the app installed; only removes EdgeFlow bindings.",
                    onClick = {
                        configRepository.update { it.copy(appStateTriggers = it.appStateTriggers - packageName) }
                    },
                )
            }
            CardSpacer()
        }
    }

    if (showAppPicker) {
        AppTriggerPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { packageName ->
                configRepository.update { config ->
                    if (packageName in config.appStateTriggers) config
                    else config.copy(appStateTriggers = config.appStateTriggers + (packageName to List(4) { null }))
                }
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun AppTriggerPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember(context) {
        runCatching {
            context.packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .map { app ->
                    app.packageName to context.packageManager.getApplicationLabel(app).toString()
                }
                .sortedBy { it.second.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }
    val filteredApps = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) apps else apps.filter { (packageName, label) ->
            label.lowercase().contains(needle) || packageName.lowercase().contains(needle)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(filteredApps.take(150), key = { it.first }) { (packageName, label) ->
                        SettingRow(
                            title = label,
                            subtitle = packageName,
                            onClick = { onSelect(packageName) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ScheduleTriggerContent(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int, target: String?) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.xxl,
        ),
    ) {
        item {
            SectionCard(
                title = "Scheduled actions",
                subtitle = "Schedules use the device's local time. The system trigger service must be active.",
            ) {
                SettingRow(
                    title = "Add schedule",
                    subtitle = "Create a daily schedule, then choose its days and action.",
                    onClick = {
                        configRepository.update { current ->
                            val id = (current.schedules.maxOfOrNull(ScheduleEntry::id) ?: 0) + 1
                            current.copy(
                                schedules = current.schedules + ScheduleEntry(
                                    id = id,
                                    enabled = true,
                                    hour = 9,
                                    minute = 0,
                                    daysOfWeekMask = 0x7F,
                                    label = "Schedule $id",
                                ),
                            )
                        }
                    },
                )
            }
            CardSpacer()
        }

        items(config.schedules, key = ScheduleEntry::id) { schedule ->
            SectionCard(
                title = schedule.label.ifBlank { "Schedule ${schedule.id}" },
                subtitle = "${schedule.hour.toString().padStart(2, '0')}:${schedule.minute.toString().padStart(2, '0')}",
            ) {
                SwitchRow(
                    title = "Enabled",
                    checked = schedule.enabled,
                    onCheckedChange = { enabled ->
                        updateSchedule(configRepository, schedule.id) { it.copy(enabled = enabled) }
                    },
                )
                RowDivider()
                ValueRow(
                    title = "Time",
                    value = "${schedule.hour.toString().padStart(2, '0')}:${schedule.minute.toString().padStart(2, '0')}",
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                updateSchedule(configRepository, schedule.id) { it.copy(hour = hour, minute = minute) }
                            },
                            schedule.hour,
                            schedule.minute,
                            true,
                        ).show()
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    weekdays.forEachIndexed { dayIndex, label ->
                        FilterChip(
                            selected = schedule.daysOfWeekMask and (1 shl dayIndex) != 0,
                            onClick = {
                                updateSchedule(configRepository, schedule.id) { current ->
                                    val bit = 1 shl dayIndex
                                    val mask = if (current.daysOfWeekMask and bit != 0) {
                                        current.daysOfWeekMask and bit.inv()
                                    } else {
                                        current.daysOfWeekMask or bit
                                    }
                                    current.copy(daysOfWeekMask = mask)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                RowDivider()
                ValueRow(
                    title = "Action",
                    value = if (schedule.action.id == ActionId.NONE) "Not set" else ActionCatalog.describe(schedule.action),
                    onClick = {
                        onPickAction(BindingFamily.SCHEDULE.id, schedule.id, 0, null)
                    },
                )
                RowDivider()
                SettingRow(
                    title = "Delete schedule",
                    subtitle = "Removes this schedule and its action binding.",
                    onClick = {
                        configRepository.update { current ->
                            current.copy(schedules = current.schedules.filterNot { it.id == schedule.id })
                        }
                    },
                )
            }
            CardSpacer()
        }
    }
}

private fun updateSchedule(
    configRepository: ConfigRepository,
    scheduleId: Int,
    transform: (ScheduleEntry) -> ScheduleEntry,
) {
    configRepository.update { config ->
        config.copy(
            schedules = config.schedules.map { schedule ->
                if (schedule.id == scheduleId) transform(schedule) else schedule
            },
        )
    }
}

private fun appEntryLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val application = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(application).toString()
    }.getOrDefault(packageName)

/** One entry in the trigger list. */
private data class TriggerEntry(
    val id: Int,
    val label: String,
    val icon: ImageVector,
)

/**
 * Groups of related triggers.
 *
 * Ordered by how often they are used: screen and power first, because those are
 * the ones people actually bind.
 */
private enum class TriggerGroup(
    val label: String,
    val description: String,
    val triggers: List<TriggerEntry>,
) {
    SCREEN(
        label = "Screen",
        description = "Events about the display and the lock screen.",
        triggers = listOf(
            TriggerEntry(StateTriggerId.SCREEN_ON, "Screen turned on", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.SCREEN_OFF, "Screen turned off", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.USER_PRESENT, "Keyguard unlocked", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.SCREEN_ROTATED, "Screen rotated", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.ROTATED_PORTRAIT, "Rotated to portrait", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.ROTATED_LANDSCAPE, "Rotated to landscape", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.FULL_SCREEN, "Entered full screen", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.EXIT_FULL_SCREEN, "Left full screen", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.STATUS_BAR_SHOWN, "Status bar shown", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.STATUS_BAR_HIDDEN, "Status bar hidden", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.NAV_BAR_SHOWN, "Navigation bar shown", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.NAV_BAR_HIDDEN, "Navigation bar hidden", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.SOFT_KEYBOARD_SHOWN, "Keyboard shown", Icons.Outlined.Bolt),
            TriggerEntry(StateTriggerId.SOFT_KEYBOARD_HIDDEN, "Keyboard hidden", Icons.Outlined.Bolt),
        ),
    ),

    POWER(
        label = "Power",
        description = "Battery and charging events.",
        triggers = listOf(
            TriggerEntry(StateTriggerId.BOOT_COMPLETED, "Boot completed", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.BATTERY_FULL, "Battery full", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.BATTERY_LOW, "Battery low", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.BATTERY_OKAY, "Battery okay", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.POWER_CONNECTED, "Power connected", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.POWER_DISCONNECTED, "Power disconnected", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.USB_PLUGGED, "USB plugged", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.USB_UNPLUGGED, "USB unplugged", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.HEADSET_PLUGGED, "Headset plugged", Icons.Outlined.Power),
            TriggerEntry(StateTriggerId.HEADSET_UNPLUGGED, "Headset unplugged", Icons.Outlined.Power),
        ),
    ),

    SESSION(
        label = "Session",
        description = "System user and session events.",
        triggers = listOf(
            TriggerEntry(StateTriggerId.USER_SWITCHED, "User switched", Icons.Outlined.Info),
        ),
    ),

    NETWORK(
        label = "Network",
        description = "Connectivity and radio state.",
        triggers = listOf(
            TriggerEntry(StateTriggerId.WIFI_CONNECTED, "Wi-Fi connected", Icons.Outlined.Wifi),
            TriggerEntry(StateTriggerId.WIFI_DISCONNECTED, "Wi-Fi disconnected", Icons.Outlined.Wifi),
            TriggerEntry(StateTriggerId.NET_CONNECTED, "Network connected", Icons.Outlined.Wifi),
            TriggerEntry(StateTriggerId.NET_DISCONNECTED, "Network disconnected", Icons.Outlined.Wifi),
            TriggerEntry(StateTriggerId.AIRPLANE_MODE_ON, "Airplane mode on", Icons.Outlined.Wifi),
            TriggerEntry(StateTriggerId.AIRPLANE_MODE_OFF, "Airplane mode off", Icons.Outlined.Wifi),
        ),
    ),

    CALLS(
        label = "Calls",
        description = "Telephony events. Only available when the telephony service is running.",
        triggers = listOf(
            TriggerEntry(StateTriggerId.SMS_RECEIVED, "SMS received", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.INCOMING_CALL_RINGING, "Incoming call ringing", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.INCOMING_CALL_CONNECTED, "Incoming call answered", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.INCOMING_CALL_ENDED, "Incoming call ended", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.OUTGOING_CALL_DIALED, "Outgoing call dialled", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.OUTGOING_CALL_CONNECTED, "Outgoing call connected", Icons.Outlined.Info),
            TriggerEntry(StateTriggerId.OUTGOING_CALL_ENDED, "Outgoing call ended", Icons.Outlined.Info),
        ),
    ),
    APP_STATE("Apps", "Run actions when an app starts, stops or changes focus.", emptyList()),
    SCHEDULE("Schedule", "Run actions at selected local times and days.", emptyList()),
}
