package com.edgeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgeflow.core.ConditionId
import com.edgeflow.core.model.Condition
import com.edgeflow.core.model.ConditionOperator

@Composable
internal fun ConditionEditor(
    condition: Condition?,
    onConditionChange: (Condition?) -> Unit,
    depth: Int = 0,
) {
    var selectedId by remember { mutableIntStateOf(condition?.id ?: -1) }
    LaunchedEffect(condition?.id) {
        condition?.let { selectedId = it.id }
    }
    var showConditionPicker by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showConditionPicker = true }) {
            Text("Condition: ${conditionTitle(selectedId)}")
        }
        if (depth > MAX_CONDITION_DEPTH && selectedId in COMPOSITE_CONDITION_IDS) {
            Text("Condition nesting is limited to $MAX_CONDITION_DEPTH levels.", color = MaterialTheme.colorScheme.error)
        } else {
            ConditionFields(
                conditionId = selectedId,
                condition = condition,
                onConditionChange = onConditionChange,
                depth = depth,
            )
        }
    }

    val options = remember(search) {
        CONDITION_OPTIONS.filter { option ->
            search.isBlank() || option.title.contains(search.trim(), ignoreCase = true)
        }
    }
    if (showConditionPicker) {
        AlertDialog(
            onDismissRequest = { showConditionPicker = false },
            title = { Text("Choose condition") },
            text = {
                Column {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search conditions") },
                        singleLine = true,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                        items(options, key = ConditionOption::id) { option ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedId = option.id
                                    onConditionChange(defaultCondition(option.id))
                                    showConditionPicker = false
                                    search = ""
                                },
                            ) {
                                Text(option.title, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showConditionPicker = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConditionFields(
    conditionId: Int,
    condition: Condition?,
    onConditionChange: (Condition?) -> Unit,
    depth: Int,
) {
    var minimumBattery by remember(conditionId) {
        mutableStateOf((condition as? Condition.BatteryLevel)?.min?.toString() ?: "0")
    }
    var maximumBattery by remember(conditionId) {
        mutableStateOf((condition as? Condition.BatteryLevel)?.max?.toString() ?: "100")
    }
    var startTime by remember(conditionId) {
        mutableStateOf((condition as? Condition.TimePeriod)?.startMinutes?.let(::formatConditionTime) ?: "00:00")
    }
    var endTime by remember(conditionId) {
        mutableStateOf((condition as? Condition.TimePeriod)?.endMinutes?.let(::formatConditionTime) ?: "00:00")
    }
    var packageName by remember(conditionId) {
        mutableStateOf((condition as? Condition.AppFocused)?.packageName.orEmpty())
    }
    var ssid by remember(conditionId) {
        mutableStateOf((condition as? Condition.WifiSsid)?.ssid.orEmpty())
    }
    var variableName by remember(conditionId) {
        mutableStateOf((condition as? Condition.Variable)?.name.orEmpty())
    }
    var variableValue by remember(conditionId) {
        mutableStateOf((condition as? Condition.Variable)?.value.orEmpty())
    }
    var variableOperator by remember(conditionId) {
        mutableStateOf((condition as? Condition.Variable)?.operator ?: ConditionOperator.EQUAL)
    }
    var weekMask by remember(conditionId) {
        mutableIntStateOf((condition as? Condition.DaysOfWeek)?.mask ?: ALL_WEEK_MASK)
    }
    var monthDayMask by remember(conditionId) {
        mutableIntStateOf((condition as? Condition.DaysOfMonth)?.mask ?: ALL_MONTH_DAYS_MASK)
    }
    var monthMask by remember(conditionId) {
        mutableIntStateOf((condition as? Condition.Months)?.mask ?: ALL_MONTHS_MASK)
    }

    when (conditionId) {
        ConditionId.BATTERY_LEVEL -> {
            ConditionTextField(minimumBattery, { value ->
                minimumBattery = value
                onConditionChange(buildConditionalCondition(conditionId, minimumBattery, maximumBattery))
            }, "Minimum battery (%)", numeric = true)
            ConditionTextField(maximumBattery, { value ->
                maximumBattery = value
                onConditionChange(buildConditionalCondition(conditionId, minimumBattery, maximumBattery))
            }, "Maximum battery (%)", numeric = true)
            ConditionHelp("Use an inclusive range from 0 to 100.")
        }
        ConditionId.TIME_PERIOD -> {
            ConditionTextField(startTime, { value ->
                startTime = value
                updateTimeCondition(startTime, endTime, onConditionChange)
            }, "Start time (HH:mm)")
            ConditionTextField(endTime, { value ->
                endTime = value
                updateTimeCondition(startTime, endTime, onConditionChange)
            }, "End time (HH:mm)")
            ConditionHelp("The range may cross midnight; both endpoints are included.")
        }
        ConditionId.DAYS_OF_WEEK -> {
            ConditionChoiceRows(
                labels = WEEKDAY_LABELS,
                isSelected = { index -> weekMask and (1 shl index) != 0 },
                onToggle = { index ->
                    weekMask = weekMask xor (1 shl index)
                    onConditionChange(Condition.DaysOfWeek(weekMask))
                },
            )
        }
        ConditionId.DAYS_OF_MONTH -> {
            ConditionChoiceRows(
                labels = (1..31).map(Int::toString),
                columns = 7,
                isSelected = { index -> monthDayMask and (1 shl index) != 0 },
                onToggle = { index ->
                    monthDayMask = monthDayMask xor (1 shl index)
                    onConditionChange(Condition.DaysOfMonth(monthDayMask))
                },
            )
        }
        ConditionId.MONTHS -> {
            ConditionChoiceRows(
                labels = MONTH_LABELS,
                columns = 4,
                isSelected = { index -> monthMask and (1 shl index) != 0 },
                onToggle = { index ->
                    monthMask = monthMask xor (1 shl index)
                    onConditionChange(Condition.Months(monthMask))
                },
            )
        }
        ConditionId.APP_FOCUSED -> ConditionTextField(packageName, { value ->
            packageName = value
            onConditionChange(value.trim().takeIf(String::isNotEmpty)?.let { Condition.AppFocused(it) })
        }, "Package name")
        ConditionId.WIFI_SSID, ConditionId.WIFI_SSID_ALT -> ConditionTextField(ssid, { value ->
            ssid = value
            onConditionChange(value.trim().takeIf(String::isNotEmpty)?.let { Condition.WifiSsid(it, conditionId) })
        }, "Wi-Fi SSID")
        ConditionId.ORIENTATION -> {
            val landscape = (condition as? Condition.Orientation)?.landscape ?: false
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !landscape,
                    onClick = { onConditionChange(Condition.Orientation(landscape = false)) },
                    label = { Text("Portrait") },
                )
                FilterChip(
                    selected = landscape,
                    onClick = { onConditionChange(Condition.Orientation(landscape = true)) },
                    label = { Text("Landscape") },
                )
            }
        }
        ConditionId.VARIABLE -> {
            ConditionTextField(variableName, { value ->
                variableName = value
                updateVariableCondition(variableName, variableValue, variableOperator, onConditionChange)
            }, "Variable name")
            ConditionTextField(variableValue, { value ->
                variableValue = value
                updateVariableCondition(variableName, variableValue, variableOperator, onConditionChange)
            }, "Compare with")
            CONDITION_COMPARISON_OPERATORS.chunked(3).forEach { rowOperators ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOperators.forEach { operator ->
                        FilterChip(
                            selected = variableOperator == operator,
                            onClick = {
                                variableOperator = operator
                                updateVariableCondition(variableName, variableValue, variableOperator, onConditionChange)
                            },
                            label = { Text(operator.name.lowercase().replace('_', ' ')) },
                        )
                    }
                }
            }
        }
        ConditionId.AND, ConditionId.OR -> {
            val items = when (condition) {
                is Condition.And -> condition.items
                is Condition.Or -> condition.items
                else -> emptyList()
            }
            val allRequired = conditionId == ConditionId.AND
            Text(if (allRequired) "Every clause must match." else "At least one clause must match.")
            items.forEachIndexed { index, item ->
                key(index) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${if (allRequired) "Clause" else "Alternative"} ${index + 1}")
                        ConditionEditor(
                            condition = item.takeUnless { it is Condition.Unknown },
                            onConditionChange = { updated ->
                                onConditionChange(updateComposite(conditionId, items, index, updated ?: Condition.Unknown()))
                            },
                            depth = depth + 1,
                        )
                        TextButton(onClick = {
                            onConditionChange(updateComposite(conditionId, items, index, null))
                        }) { Text("Remove") }
                    }
                }
            }
            if (items.size < MAX_COMPOSITE_ITEMS) {
                TextButton(onClick = {
                    onConditionChange(updateComposite(conditionId, items, items.size, Condition.Flag(ConditionId.SCREEN_ON)))
                }) { Text(if (allRequired) "Add clause" else "Add alternative") }
            }
        }
        ConditionId.NOT -> {
            val notCondition = condition as? Condition.Not
            val item = notCondition?.item?.takeUnless { it is Condition.Unknown }
            Text("The inner condition must not match.")
            ConditionEditor(
                condition = item,
                onConditionChange = { updated ->
                    onConditionChange(Condition.Not(updated ?: Condition.Unknown()))
                },
                depth = depth + 1,
            )
        }
        else -> Unit
    }
}

@Composable
private fun ConditionChoiceRows(
    labels: List<String>,
    columns: Int = 4,
    isSelected: (Int) -> Boolean,
    onToggle: (Int) -> Unit,
) {
    labels.indices.toList().chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { index ->
                FilterChip(
                    selected = isSelected(index),
                    onClick = { onToggle(index) },
                    label = { Text(labels[index]) },
                )
            }
        }
    }
}

@Composable
private fun ConditionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,
        ),
    )
}

@Composable
private fun ConditionHelp(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun updateTimeCondition(
    start: String,
    end: String,
    onConditionChange: (Condition?) -> Unit,
) {
    val startMinutes = parseConditionTimeMinutes(start)
    val endMinutes = parseConditionTimeMinutes(end)
    onConditionChange(
        if (startMinutes != null && endMinutes != null) {
            Condition.TimePeriod(startMinutes, endMinutes)
        } else {
            null
        },
    )
}

private fun updateVariableCondition(
    name: String,
    value: String,
    operator: ConditionOperator,
    onConditionChange: (Condition?) -> Unit,
) {
    onConditionChange(name.trim().takeIf(String::isNotEmpty)?.let { Condition.Variable(it, value, operator) })
}

private fun updateComposite(
    conditionId: Int,
    items: List<Condition>,
    index: Int,
    replacement: Condition?,
): Condition {
    val updated = items.toMutableList()
    if (replacement == null) updated.removeAt(index) else if (index in updated.indices) {
        updated[index] = replacement
    } else {
        updated.add(replacement)
    }
    return if (conditionId == ConditionId.AND) Condition.And(updated) else Condition.Or(updated)
}

private fun defaultCondition(id: Int): Condition? = when {
    id in FLAG_CONDITION_IDS -> Condition.Flag(id)
    id == ConditionId.BATTERY_LEVEL -> Condition.BatteryLevel()
    id == ConditionId.TIME_PERIOD -> Condition.TimePeriod()
    id == ConditionId.DAYS_OF_WEEK -> Condition.DaysOfWeek()
    id == ConditionId.DAYS_OF_MONTH -> Condition.DaysOfMonth()
    id == ConditionId.MONTHS -> Condition.Months()
    id == ConditionId.APP_FOCUSED -> Condition.AppFocused("")
    id == ConditionId.WIFI_SSID || id == ConditionId.WIFI_SSID_ALT -> Condition.WifiSsid("", id)
    id == ConditionId.ORIENTATION -> Condition.Orientation()
    id == ConditionId.VARIABLE -> Condition.Variable("")
    id == ConditionId.AND -> Condition.And()
    id == ConditionId.OR -> Condition.Or()
    id == ConditionId.NOT -> Condition.Not()
    else -> null
}

internal fun buildConditionalCondition(
    conditionId: Int,
    minimumBatteryText: String,
    maximumBatteryText: String,
): Condition? {
    if (conditionId in FLAG_CONDITION_IDS) return Condition.Flag(conditionId)
    if (conditionId != ConditionId.BATTERY_LEVEL) return null

    val minimum = minimumBatteryText.toIntOrNull()?.takeIf { it in 0..100 } ?: return null
    val maximum = maximumBatteryText.toIntOrNull()?.takeIf { it in minimum..100 } ?: return null
    return Condition.BatteryLevel(min = minimum, max = maximum)
}

internal fun isValidCondition(condition: Condition): Boolean = when (condition) {
    is Condition.Unknown -> false
    is Condition.Flag -> condition.id in FLAG_CONDITION_IDS
    is Condition.And -> condition.items.isNotEmpty() && condition.items.all(::isValidCondition)
    is Condition.Or -> condition.items.isNotEmpty() && condition.items.all(::isValidCondition)
    is Condition.Not -> isValidCondition(condition.item)
    is Condition.BatteryLevel -> condition.min in 0..100 && condition.max in condition.min..100
    is Condition.TimePeriod -> condition.startMinutes in 0..1439 && condition.endMinutes in 0..1439
    is Condition.DaysOfWeek -> condition.mask in 1..ALL_WEEK_MASK
    is Condition.DaysOfMonth -> condition.mask in 1..ALL_MONTH_DAYS_MASK
    is Condition.Months -> condition.mask in 1..ALL_MONTHS_MASK
    is Condition.AppFocused -> condition.packageName.isNotBlank()
    is Condition.WifiSsid -> condition.ssid.isNotBlank() &&
        condition.id in setOf(ConditionId.WIFI_SSID, ConditionId.WIFI_SSID_ALT)
    is Condition.Orientation -> true
    is Condition.Variable -> condition.name.isNotBlank() && condition.operator in CONDITION_COMPARISON_OPERATORS
}

internal fun parseConditionTimeMinutes(value: String): Int? {
    val match = CONDITION_TIME_PATTERN.matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun formatConditionTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun conditionTitle(id: Int): String = CONDITION_OPTIONS.firstOrNull { it.id == id }?.title ?: "Choose"

private data class ConditionOption(val id: Int, val title: String)

private val FLAG_CONDITION_OPTIONS = listOf(
    ConditionOption(ConditionId.SCREEN_ON, "Screen on"),
    ConditionOption(ConditionId.KEYGUARD_ON, "Keyguard"),
    ConditionOption(ConditionId.AUTO_BRIGHTNESS, "Auto brightness"),
    ConditionOption(ConditionId.AUTO_ROTATION, "Auto rotation"),
    ConditionOption(ConditionId.WIFI_ON, "Wi-Fi on"),
    ConditionOption(ConditionId.MOBILE_DATA_ON, "Mobile data"),
    ConditionOption(ConditionId.LOCATION_ON, "Location"),
    ConditionOption(ConditionId.BLUETOOTH_ON, "Bluetooth"),
    ConditionOption(ConditionId.NFC_ON, "NFC"),
    ConditionOption(ConditionId.SYNC_ON, "Auto-sync"),
    ConditionOption(ConditionId.WIFI_AP_ON, "Hotspot"),
    ConditionOption(ConditionId.AIRPLANE_MODE_ON, "Airplane mode"),
    ConditionOption(ConditionId.GESTURE_CONTROL_ON, "Gestures enabled"),
    ConditionOption(ConditionId.KEY_CONTROL_ON, "Keys enabled"),
    ConditionOption(ConditionId.POWER_CONNECTED, "Power connected"),
    ConditionOption(ConditionId.USB_PLUGGED, "USB plugged"),
    ConditionOption(ConditionId.HEADSET_PLUGGED, "Headset plugged"),
    ConditionOption(ConditionId.NET_CONNECTED, "Network connected"),
    ConditionOption(ConditionId.WIFI_CONNECTED, "Wi-Fi connected"),
    ConditionOption(ConditionId.MUSIC_ACTIVE, "Music active"),
    ConditionOption(ConditionId.RINGING, "Ringing"),
    ConditionOption(ConditionId.OFF_HOOK, "In call"),
    ConditionOption(ConditionId.SOFT_KEYBOARD_SHOWING, "Keyboard showing"),
    ConditionOption(ConditionId.STATUS_BAR_SHOWING, "Status bar showing"),
    ConditionOption(ConditionId.NAV_BAR_SHOWING, "Navigation bar showing"),
)

private val CONDITION_OPTIONS = FLAG_CONDITION_OPTIONS + listOf(
    ConditionOption(ConditionId.BATTERY_LEVEL, "Battery level"),
    ConditionOption(ConditionId.TIME_PERIOD, "Time period"),
    ConditionOption(ConditionId.DAYS_OF_WEEK, "Days of week"),
    ConditionOption(ConditionId.DAYS_OF_MONTH, "Days of month"),
    ConditionOption(ConditionId.MONTHS, "Months"),
    ConditionOption(ConditionId.WIFI_SSID, "Wi-Fi SSID"),
    ConditionOption(ConditionId.WIFI_SSID_ALT, "Wi-Fi SSID (alternate)"),
    ConditionOption(ConditionId.APP_FOCUSED, "App is focused"),
    ConditionOption(ConditionId.ORIENTATION, "Orientation"),
    ConditionOption(ConditionId.VARIABLE, "Variable comparison"),
    ConditionOption(ConditionId.AND, "All conditions (AND)"),
    ConditionOption(ConditionId.OR, "Any condition (OR)"),
    ConditionOption(ConditionId.NOT, "Not condition"),
)

private val FLAG_CONDITION_IDS = FLAG_CONDITION_OPTIONS.mapTo(mutableSetOf()) { it.id }
private val COMPOSITE_CONDITION_IDS = setOf(ConditionId.AND, ConditionId.OR, ConditionId.NOT)
private val CONDITION_COMPARISON_OPERATORS = listOf(
    ConditionOperator.EQUAL,
    ConditionOperator.NOT_EQUAL,
    ConditionOperator.GREATER_THAN,
    ConditionOperator.GREATER_THAN_OR_EQUAL,
    ConditionOperator.LESS_THAN,
    ConditionOperator.LESS_THAN_OR_EQUAL,
)
private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTH_LABELS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val CONDITION_TIME_PATTERN = Regex("([01]\\d|2[0-3]):([0-5]\\d)")
private const val ALL_WEEK_MASK = 0x7F
private const val ALL_MONTH_DAYS_MASK = 0x7FFFFFFF
private const val ALL_MONTHS_MASK = 0x0FFF
private const val MAX_CONDITION_DEPTH = 8
private const val MAX_COMPOSITE_ITEMS = 8
