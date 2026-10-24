package com.edgeflow.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionCategory
import com.edgeflow.core.ActionId
import com.edgeflow.core.ActionSpec
import com.edgeflow.core.Edge
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.Ipc
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.KeyGesture
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.GesturePlacement
import com.edgeflow.core.model.Condition
import com.edgeflow.core.model.AdjustStream
import com.edgeflow.core.model.EditTextCommand
import com.edgeflow.core.model.MediaCommand
import com.edgeflow.core.model.RingerModeValue
import com.edgeflow.core.model.SlideAdjustTarget
import com.edgeflow.core.model.SuccessiveAdjustTarget
import com.edgeflow.core.model.ToggleMode
import com.edgeflow.core.model.VariableOperator
import com.edgeflow.data.ConfigRepository
import com.edgeflow.EdgeFlowApp
import com.edgeflow.service.OverlayHostService
import com.edgeflow.data.setGestureAction
import com.edgeflow.data.setKeyAction
import com.edgeflow.data.setPieSlot
import com.edgeflow.data.setSideBar
import com.edgeflow.data.setStateTrigger
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing

/**
 * The action picker.
 *
 * ## Why a search bar plus a grid
 *
 * There are around a hundred actions. A plain list means a lot of scrolling for
 * the common cases (back, home, launch an app), while a grid of icons alone is
 * unusable for discovery because most actions have no memorable icon. The screen
 * therefore offers search for people who know what they want, and a
 * category-grouped grid for people who do not.
 *
 * The picker writes straight through to the configuration when an action is
 * chosen and then pops, so there is no separate save step to forget.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerScreen(
    configRepository: ConfigRepository,
    family: BindingFamily,
    primary: Int,
    secondary: Int,
    target: String? = null,
    onDone: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var searchActive by remember { mutableStateOf(false) }
    var pendingActionId by rememberSaveable { mutableIntStateOf(-1) }

    fun chooseAction(actionId: Int) {
        if (ActionCatalog.spec(actionId)?.requiresConfig == true) {
            pendingActionId = actionId
        } else {
            val action = if (actionId == ActionId.DEFAULT && family != BindingFamily.EDGE_GESTURE) {
                Action.None
            } else {
                actionForId(actionId)
            }
            applySelection(configRepository, family, primary, secondary, action, target)
            onDone()
        }
    }

    val results = remember(query, family, secondary) {
        ActionCatalog.betaSearch(query).filterNot {
            (it.id == ActionId.DEFAULT && family != BindingFamily.EDGE_GESTURE) ||
                (it.id == ActionId.SUB_GESTURES &&
                    (family != BindingFamily.EDGE_GESTURE ||
                        secondary !in Gesture.LONG_PRESS.index..Gesture.SWIPE_DOWN.index))
        }
    }
    val grouped = remember(results) {
        results.groupBy(ActionSpec::category).toSortedMap(compareBy(ActionCategory::ordinal))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pickerTitle(family, primary, secondary, target)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { searchActive = false },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("Search actions") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = com.edgeflow.ui.i18n.UiTranslations.forContext(context, "Clear search"))
                                }
                            }
                        },
                    )
                },
                expanded = searchActive,
                onExpandedChange = { searchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                LazyColumn {
                    items(results.take(SEARCH_RESULT_LIMIT)) { spec ->
                        ActionRow(
                            spec = spec,
                            onClick = {
                                chooseAction(spec.id)
                            },
                        )
                    }
                }
            }

            if (query.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.xxl,
                    ),
                ) {
                    item {
                        ActionRow(
                            spec = ActionCatalog.spec(ActionId.NONE)!!,
                            onClick = {
                                chooseAction(ActionId.NONE)
                            },
                        )
                    }

                    grouped.forEach { (category, specs) ->
                        item(key = "header-${category.name}") {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = Spacing.xs,
                                    top = Spacing.lg,
                                    bottom = Spacing.sm,
                                ),
                            )
                        }
                        item(key = "grid-${category.name}") {
                            ActionGrid(
                                specs = specs,
                                onPick = { spec ->
                                    chooseAction(spec.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingActionId >= 0) {
        ActionConfigurationDialog(
            actionId = pendingActionId,
            config = configRepository.current,
            onDismiss = { pendingActionId = -1 },
            onConfirm = { action ->
                applySelection(configRepository, family, primary, secondary, action, target)
                pendingActionId = -1
                onDone()
            },
        )
    }
}

/**
 * A category's actions as a two-column grid.
 *
 * Two columns rather than three: the labels are the primary information, and a
 * third column would truncate most of them to ellipses.
 */
@Composable
private fun ActionGrid(
    specs: List<ActionSpec>,
    onPick: (ActionSpec) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeightFor(specs.size)),
        userScrollEnabled = false,
    ) {
        items(specs, key = { it.id }) { spec ->
            ActionTile(spec = spec, onClick = { onPick(spec) })
        }
    }
}

/**
 * Height a grid needs for [count] items at two columns.
 *
 * A nested lazy grid cannot measure itself, so its height is computed. The tile
 * height is fixed by [ActionTile], which keeps this arithmetic honest.
 */
private fun gridHeightFor(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 1) / 2
    val tileHeight = 72.dp
    val spacing = Spacing.sm
    return (tileHeight * rows) + (spacing * (rows - 1).coerceAtLeast(0))
}

/** A tappable action tile. */
@Composable
private fun ActionTile(
    spec: ActionSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
            .height(56.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = spec.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (spec.premium) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Premium",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** A full-width action row, used by the search results and the "no action" entry. */
@Composable
private fun ActionRow(
    spec: ActionSpec,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = spec.title.take(1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = spec.category.label + if (spec.requiresConfig) " · needs setup" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (spec.premium) {
            Text(
                text = "Premium",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * Writes the chosen action into the right part of the configuration.
 *
 * A single `when` over the family keeps the write paths together, so it is
 * obvious that every picker entry point is wired up.
 */
private fun applySelection(
    configRepository: ConfigRepository,
    family: BindingFamily,
    primary: Int,
    secondary: Int,
    action: Action,
    target: String?,
) {
    when (family) {
        BindingFamily.EDGE_GESTURE -> {
            val zone = EdgeZone.of(primary) ?: return
            val gesture = Gesture.of(secondary) ?: return
            configRepository.setGestureAction(zone, gesture, action)
        }

        BindingFamily.HARDWARE_KEY -> {
            val key = HardwareKey.of(primary) ?: return
            val gesture = KeyGesture.of(secondary) ?: return
            configRepository.setKeyAction(key, gesture, action)
        }

        BindingFamily.PIE_SLOT -> {
            val edge = Edge.of(primary) ?: return
            configRepository.setPieSlot(edge, secondary, action)
        }

        BindingFamily.SIDE_BAR -> {
            val edge = Edge.of(primary) ?: return
            val existing = configRepository.current.sideBars.items(edge).toMutableList()
            while (existing.size <= secondary) existing.add(Action.None)
            existing[secondary] = action
            configRepository.setSideBar(edge, existing.filter { it.id != ActionId.NONE })
        }

        BindingFamily.STATE_TRIGGER -> configRepository.setStateTrigger(primary, action)

        BindingFamily.APP_STATE -> {
            val packageName = target ?: return
            if (primary !in 0..3) return
            configRepository.update { config ->
                val actions = config.appStateTriggers[packageName].orEmpty().toMutableList()
                while (actions.size <= primary) actions.add(null)
                actions[primary] = action.takeIf { it.id != ActionId.NONE }
                val next = if (actions.all { it == null }) {
                    config.appStateTriggers - packageName
                } else {
                    config.appStateTriggers + (packageName to actions)
                }
                config.copy(appStateTriggers = next)
            }
        }

        BindingFamily.SCHEDULE -> configRepository.update { config ->
            config.copy(
                schedules = config.schedules.map { schedule ->
                    if (schedule.id == primary) schedule.copy(action = action) else schedule
                },
            )
        }

        BindingFamily.SAVED_ACTION -> configRepository.update { config ->
            val actions = config.savedMultiActions.toMutableList()
            val index = actions.indexOfFirst { it.id == primary }.takeIf { it >= 0 } ?: return@update config
            actions[index] = actions[index].copy(action = action)
            config.copy(savedMultiActions = actions)
        }

        BindingFamily.PANEL_CELL -> configRepository.update { config ->
            val panels = config.panels.toMutableList()
            val panelIndex = panels.indexOfFirst { it.id == primary }
            if (panelIndex < 0) return@update config
            val panel = panels[panelIndex]
            if (secondary !in 0 until panel.rows * panel.columns) return@update config
            panels[panelIndex] = panel.withCell(secondary / panel.columns, secondary % panel.columns, action)
            config.copy(panels = panels)
        }

        BindingFamily.SHORTCUT_PANEL_CELL -> configRepository.update { config ->
            val edge = Edge.of(primary) ?: return@update config
            val panel = config.shortcutPanels.edges[edge.index]
                ?: com.edgeflow.core.model.PanelConfig(
                    id = edge.index,
                    name = "${edge.label} shortcuts",
                    rows = 4,
                    columns = 3,
                )
            if (secondary !in 0 until panel.rows * panel.columns) return@update config
            val updated = panel.withCell(secondary / panel.columns, secondary % panel.columns, action)
            config.copy(shortcutPanels = config.shortcutPanels.copy(edges = config.shortcutPanels.edges + (edge.index to updated)))
        }
    }
}

private fun actionForId(actionId: Int): Action = when (actionId) {
    ActionId.WIFI,
    ActionId.MOBILE_DATA,
    ActionId.LOCATION,
    ActionId.BLUETOOTH,
    ActionId.NFC,
    ActionId.SYNC,
    ActionId.WIFI_AP,
    ActionId.AIRPLANE_MODE,
    ActionId.AUTO_BRIGHTNESS,
    ActionId.AUTO_ROTATION,
    ActionId.IMMERSIVE_STATUS_BAR,
    ActionId.IMMERSIVE_NAV_BAR,
    ActionId.KEEP_SCREEN_ON,
    ActionId.GESTURE_RECORD,
    ActionId.TORCH,
    ActionId.SCREEN_FILTER,
    ActionId.GESTURE_CONTROL,
    ActionId.KEY_CONTROL,
    -> Action.Toggle(id = actionId)

    ActionId.ABORT -> Action.Abort()
    ActionId.STOP_ALL -> Action.StopAll()
    else -> Action.Simple(actionId)
}

@Composable
private fun ActionConfigurationDialog(
    actionId: Int,
    config: com.edgeflow.core.model.EdgeConfig,
    onDismiss: () -> Unit,
    onConfirm: (Action) -> Unit,
    existingAction: Action? = null,
) {
    val editKey = existingAction?.hashCode() ?: 0
    var firstValue by rememberSaveable(actionId, editKey) { mutableStateOf(when (existingAction) {
        is Action.LaunchApp -> existingAction.packageName
        is Action.LaunchActivity -> existingAction.packageName
        is Action.Shell -> existingAction.command
        is Action.Delay -> existingAction.millis.toString()
        is Action.InputText -> existingAction.text
        is Action.InjectTap -> existingAction.x.toString()
        is Action.InjectKey -> existingAction.keyCode.toString()
        is Action.Toast -> existingAction.text
        is Action.Speech -> existingAction.text
        is Action.Notify -> existingAction.title
        is Action.Vibrate -> existingAction.millis.toString()
        is Action.Repeat -> existingAction.times.toString()
        is Action.KillApp -> existingAction.packageName
        is Action.SetVariable -> existingAction.name
        is Action.SavedMultiAction -> existingAction.slot.toString()
        is Action.ShowPanel -> existingAction.panelIndex.toString()
        is Action.Adjust -> existingAction.delta.toString()
        is Action.PlaySound -> existingAction.uri.orEmpty()
        else -> ""
    }) }
    var secondValue by rememberSaveable(actionId, editKey) { mutableStateOf(when (existingAction) {
        is Action.LaunchActivity -> existingAction.className.orEmpty()
        is Action.InjectTap -> existingAction.y.toString()
        is Action.InjectKey -> existingAction.metaState.toString()
        is Action.Notify -> existingAction.text
        is Action.SetVariable -> existingAction.value
        else -> ""
    }) }
    var thirdValue by rememberSaveable(actionId, editKey) { mutableStateOf(
        (existingAction as? Action.LaunchActivity)?.data.orEmpty(),
    ) }
    var edgeIndex by rememberSaveable(actionId, editKey) { mutableIntStateOf(when (existingAction) {
        is Action.ShowPie -> existingAction.edge
        is Action.ShowSideBar -> existingAction.edge
        is Action.ShowShortcutPanel -> existingAction.edge
        else -> Edge.LEFT.index
    }) }
    var ringerMode by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.RingerMode)?.mode?.name ?: RingerModeValue.NORMAL.name)
    }
    var adjustStream by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.Adjust)?.stream?.name ?: AdjustStream.MUSIC.name)
    }
    var mediaCommand by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.MediaControl)?.command?.name ?: MediaCommand.PLAY_PAUSE.name)
    }
    var editTextCommand by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.EditText)?.command?.name ?: EditTextCommand.COPY.name)
    }
    var successiveTarget by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SuccessiveAdjust)?.target?.name ?: SuccessiveAdjustTarget.VOLUME.name)
    }
    var successiveStream by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SuccessiveAdjust)?.stream?.name ?: AdjustStream.MUSIC.name)
    }
    var successiveIncrease by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SuccessiveAdjust)?.increase ?: true)
    }
    var slideTarget by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SlideAdjust)?.target?.name ?: SlideAdjustTarget.VOLUME.name)
    }
    var slideStream by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SlideAdjust)?.stream?.name ?: AdjustStream.MUSIC.name)
    }
    var variableOperator by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.SetVariable)?.operator?.name ?: VariableOperator.ASSIGN.name)
    }
    var launchAsRoot by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.LaunchApp)?.root ?: false)
    }
    var shellAsRoot by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.Shell)?.asRoot ?: false)
    }
    var recordedGestureId by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.ReplayGesture)?.recordingId.orEmpty())
    }
    var recordedGestureSpeed by rememberSaveable(actionId, editKey) {
        mutableIntStateOf((existingAction as? Action.ReplayGesture)?.speedPercent ?: 100)
    }
    var recordedGesturePlacement by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.ReplayGesture)?.placement ?: GesturePlacement.ORIGINAL)
    }
    var gestureAnchorX by rememberSaveable(actionId, editKey) {
        mutableIntStateOf((existingAction as? Action.ReplayGesture)?.anchorXPercent ?: 50)
    }
    var gestureAnchorY by rememberSaveable(actionId, editKey) {
        mutableIntStateOf((existingAction as? Action.ReplayGesture)?.anchorYPercent ?: 50)
    }
    val sequenceActions = remember(actionId, existingAction) {
        mutableStateListOf<Action>().also {
            it.addAll(when (existingAction) {
                is Action.Sequence -> existingAction.actions
                is Action.Repeat -> existingAction.actions
                else -> emptyList()
            })
        }
    }
    val subGestureActions = remember(actionId, existingAction) {
        mutableStateListOf<Action>().apply {
            addAll(com.edgeflow.core.ActionEditorDefaults.secondaryGestures(existingAction))
        }
    }
    var showChildActionPicker by remember { mutableStateOf(false) }
    var childActionConfigId by remember { mutableIntStateOf(-1) }
    var editingChildAction by remember { mutableStateOf<Action?>(null) }
    var childActionTarget by remember { mutableStateOf("sequence") }
    var condition by remember(actionId, existingAction) {
        mutableStateOf<Condition?>(com.edgeflow.core.ActionEditorDefaults.condition(existingAction))
    }
    var thenAction by remember(actionId, existingAction) {
        mutableStateOf(com.edgeflow.core.ActionEditorDefaults.thenBranch(existingAction))
    }
    var elseAction by remember(actionId, existingAction) {
        mutableStateOf(com.edgeflow.core.ActionEditorDefaults.elseBranch(existingAction))
    }
    var editableInnerAction by remember(actionId, existingAction) {
        mutableStateOf((existingAction as? Action.EditableAction)?.innerAction)
    }
    var editableCustomLabel by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.EditableAction)?.customLabel.orEmpty())
    }
    var showEditableActionPicker by remember { mutableStateOf(false) }
    var editableActionConfigId by rememberSaveable { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val liveConfig by (context.applicationContext as EdgeFlowApp)
        .configRepository.config.collectAsStateWithLifecycle()
    var awaitingRecording by remember(actionId) { mutableStateOf(false) }
    var previousRecordingIds by remember(actionId) { mutableStateOf(emptySet<String>()) }
    var recordingError by remember(actionId) { mutableStateOf<String?>(null) }

    fun launchGestureRecorder() {
        previousRecordingIds = liveConfig.recordedGestures.mapTo(mutableSetOf()) { it.id }
        awaitingRecording = true
        recordingError = null
        runCatching {
            context.startForegroundService(
                Intent(context, OverlayHostService::class.java)
                    .setAction(Ipc.SHOW_OVERLAY)
                    .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_GESTURE_RECORD),
            )
        }.onFailure {
            awaitingRecording = false
            recordingError = "Could not open the gesture recorder. Check the overlay permission."
        }
    }

    val gesturePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            launchGestureRecorder()
        } else {
            recordingError = "Allow display over other apps to record a gesture."
        }
    }
    LaunchedEffect(liveConfig.recordedGestures, awaitingRecording) {
        if (awaitingRecording) {
            liveConfig.recordedGestures.lastOrNull { it.id !in previousRecordingIds }?.let {
                recordedGestureId = it.id
                awaitingRecording = false
                recordingError = null
            }
        }
    }
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember(context) {
        AppWidgetHost(context, Action.FloatingWidget.HOST_ID)
    }
    var floatingWidgetId by rememberSaveable(actionId, editKey) {
        mutableIntStateOf((existingAction as? Action.FloatingWidget)?.widgetId ?: 0)
    }
    var pendingWidgetId by rememberSaveable(actionId) { mutableIntStateOf(0) }
    var floatingWidgetMessage by rememberSaveable(actionId) { mutableStateOf<String?>(null) }
    var widgetWidthDp by rememberSaveable(actionId, editKey) {
        mutableStateOf(((existingAction as? Action.FloatingWidget)?.widthDp ?: 0).toString())
    }
    var widgetHeightDp by rememberSaveable(actionId, editKey) {
        mutableStateOf(((existingAction as? Action.FloatingWidget)?.heightDp ?: 0).toString())
    }
    var widgetXPercent by rememberSaveable(actionId, editKey) {
        mutableStateOf(((existingAction as? Action.FloatingWidget)?.initialXPercent ?: 50).toString())
    }
    var widgetYPercent by rememberSaveable(actionId, editKey) {
        mutableStateOf(((existingAction as? Action.FloatingWidget)?.initialYPercent ?: 20).toString())
    }
    var widgetTimeoutSeconds by rememberSaveable(actionId, editKey) {
        mutableStateOf(((existingAction as? Action.FloatingWidget)?.timeoutSeconds ?: 0).toString())
    }
    var widgetRememberPosition by rememberSaveable(actionId, editKey) {
        mutableStateOf((existingAction as? Action.FloatingWidget)?.rememberLastPosition ?: true)
    }
    val widgetProviders = remember(context) {
        runCatching {
            appWidgetManager.installedProviders.sortedBy { provider ->
                runCatching { provider.loadLabel(context.packageManager).toString() }
                    .getOrDefault(provider.provider.className)
                    .lowercase()
            }
        }.getOrDefault(emptyList())
    }

    fun acceptFloatingWidget(widgetId: Int) {
        // Never destroy an existing widget just because the user is editing
        // its action. Until confirmation, other saved bindings may still
        // reference that widget ID.
        val originalWidgetId = (existingAction as? Action.FloatingWidget)?.widgetId ?: 0
        if (floatingWidgetId > 0 && floatingWidgetId != widgetId &&
            floatingWidgetId != originalWidgetId
        ) {
            runCatching { appWidgetHost.deleteAppWidgetId(floatingWidgetId) }
        }
        floatingWidgetId = widgetId
        pendingWidgetId = 0
        floatingWidgetMessage = null
    }

    val configureWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val widgetId = pendingWidgetId
        if (widgetId <= 0) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK && runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull() != null) {
            acceptFloatingWidget(widgetId)
        } else {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
            pendingWidgetId = 0
            floatingWidgetMessage = "Widget setup was canceled."
        }
    }

    fun completeWidgetBinding(widgetId: Int) {
        val provider = runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull()
        if (provider == null) {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
            pendingWidgetId = 0
            floatingWidgetMessage = "The selected widget could not be bound."
            return
        }
        val configureActivity = provider.configure
        if (configureActivity == null) {
            acceptFloatingWidget(widgetId)
            return
        }
        try {
            configureWidgetLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(configureActivity)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
        } catch (t: Throwable) {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
            pendingWidgetId = 0
            floatingWidgetMessage = t.message ?: "Could not open widget setup."
        }
    }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val widgetId = pendingWidgetId
        if (widgetId <= 0) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK && runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull() != null) {
            completeWidgetBinding(widgetId)
        } else {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
            pendingWidgetId = 0
            floatingWidgetMessage = "Widget binding was canceled."
        }
    }

    fun chooseWidgetProvider(provider: AppWidgetProviderInfo) {
        if (pendingWidgetId != 0) return
        val widgetId = try {
            appWidgetHost.allocateAppWidgetId()
        } catch (t: Throwable) {
            floatingWidgetMessage = t.message ?: "Could not allocate an app widget ID."
            return
        }
        pendingWidgetId = widgetId
        floatingWidgetMessage = ""
        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
        }.getOrDefault(false)
        if (bound) {
            completeWidgetBinding(widgetId)
        } else {
            try {
                bindWidgetLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider),
                )
            } catch (t: Throwable) {
                runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
                pendingWidgetId = 0
                floatingWidgetMessage = t.message ?: "Could not start widget binding."
            }
        }
    }

    fun discardWidgetSelection() {
        val originalWidgetId = (existingAction as? Action.FloatingWidget)?.widgetId ?: 0
        setOf(floatingWidgetId, pendingWidgetId)
            .filter { it > 0 && it != originalWidgetId }
            .forEach { widgetId ->
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
        }
        floatingWidgetId = 0
        pendingWidgetId = 0
    }

    val panelId = firstValue.toIntOrNull()
    val savedSlot = firstValue.toIntOrNull()
    val builtAction: Action? = when (actionId) {
        ActionId.FLOATING_WIDGET -> {
            val width = widgetWidthDp.toIntOrNull()?.takeIf { it in 0..1600 }
            val height = widgetHeightDp.toIntOrNull()?.takeIf { it in 0..1600 }
            val x = widgetXPercent.toIntOrNull()?.takeIf { it in 0..100 }
            val y = widgetYPercent.toIntOrNull()?.takeIf { it in 0..100 }
            val timeout = widgetTimeoutSeconds.toIntOrNull()?.takeIf { it in 0..3600 }
            if (floatingWidgetId > 0 && width != null && height != null &&
                x != null && y != null && timeout != null
            ) {
                Action.FloatingWidget(
                    widgetId = floatingWidgetId,
                    widthDp = width,
                    heightDp = height,
                    initialXPercent = x,
                    initialYPercent = y,
                    timeoutSeconds = timeout,
                    rememberLastPosition = widgetRememberPosition,
                )
            } else null
        }
        ActionId.LAUNCH_APP -> firstValue.trim().takeIf(String::isNotEmpty)?.let {
            Action.LaunchApp(packageName = it, root = launchAsRoot)
        }
        ActionId.KILL_APP -> firstValue.trim().takeIf(String::isNotEmpty)?.let { Action.KillApp(it) }
        ActionId.LAUNCH_ACTIVITY,
        ActionId.ACTIVITY_FINISH,
        ActionId.ACTIVITY_113,
        ActionId.ACTIVITY_114,
        -> firstValue.trim().takeIf(String::isNotEmpty)?.let { packageName ->
            Action.LaunchActivity(
                packageName = packageName,
                className = secondValue.trim().takeIf(String::isNotEmpty),
                data = thirdValue.trim().takeIf(String::isNotEmpty),
                id = actionId,
            )
        }

        ActionId.SHELL, ActionId.SHELL_SU -> firstValue.trim().takeIf(String::isNotEmpty)?.let {
            Action.Shell(command = it, asRoot = actionId == ActionId.SHELL_SU || shellAsRoot, id = actionId)
        }
        ActionId.DELAY -> firstValue.toLongOrNull()?.takeIf { it >= 0 }?.let { Action.Delay(it) }
        ActionId.INJECT_TEXT, ActionId.INJECT_TEXT_ALT -> firstValue.takeIf(String::isNotEmpty)?.let {
            Action.InputText(text = it, id = actionId)
        }
        ActionId.INJECT_KEY_EVENT, ActionId.INJECT_KEY_EVENT_ALT -> {
            val keyCode = firstValue.toIntOrNull()?.takeIf { it >= 0 }
            val metaState = secondValue.ifBlank { "0" }.toIntOrNull()?.takeIf { it >= 0 }
            if (keyCode != null && metaState != null) {
                Action.InjectKey(keyCode = keyCode, metaState = metaState, id = actionId)
            } else null
        }
        ActionId.INJECT_GESTURE -> {
            if (recordedGestureId.isNotBlank()) {
                if (liveConfig.recordedGestures.any { it.id == recordedGestureId }) {
                    Action.ReplayGesture(
                        recordedGestureId,
                        speedPercent = recordedGestureSpeed,
                        placement = recordedGesturePlacement,
                        anchorXPercent = gestureAnchorX,
                        anchorYPercent = gestureAnchorY,
                    )
                } else null
            } else (existingAction as? Action.InjectTap)
        }
        ActionId.PIE -> Action.ShowPie(edgeIndex)
        ActionId.SIDE_BAR -> Action.ShowSideBar(edgeIndex)
        ActionId.SHORTCUTS_PANEL -> Action.ShowShortcutPanel(edgeIndex)
        ActionId.CUSTOM_PANEL -> panelId?.let { Action.ShowPanel(it) }
        ActionId.SAVED_MULTI_ACTION, ActionId.SAVED_MULTI_ACTION_ALT -> savedSlot
            ?.takeIf { it in config.savedMultiActions.indices &&
                !config.savedMultiActions[it].deleted &&
                config.savedMultiActions[it].action.id != ActionId.NONE }
            ?.let { Action.SavedMultiAction(slot = it, id = actionId) }
        ActionId.ADJUST, ActionId.ADJUST_VOLUME -> {
            val stream = AdjustStream.entries.firstOrNull { it.name == adjustStream }
            val delta = firstValue.toIntOrNull()
            if (stream != null && delta != null && delta != 0) Action.Adjust(stream, delta, actionId) else null
        }
        ActionId.RINGER_MODE -> RingerModeValue.entries.firstOrNull { it.name == ringerMode }
            ?.let { Action.RingerMode(it) }
        ActionId.MEDIA_CONTROL -> MediaCommand.entries.firstOrNull { it.name == mediaCommand }
            ?.let { Action.MediaControl(it) }
        ActionId.EDIT_TEXT -> EditTextCommand.entries.firstOrNull { it.name == editTextCommand }
            ?.let { Action.EditText(it) }
        ActionId.SUCCESSIVE_ADJUST -> {
            val target = SuccessiveAdjustTarget.entries.firstOrNull { it.name == successiveTarget }
            val stream = AdjustStream.entries.firstOrNull { it.name == successiveStream }
            if (target != null && stream != null) {
                Action.SuccessiveAdjust(target, successiveIncrease, stream)
            } else null
        }
        ActionId.SLIDE_ADJUST -> {
            val target = SlideAdjustTarget.entries.firstOrNull { it.name == slideTarget }
            val stream = AdjustStream.entries.firstOrNull { it.name == slideStream }
            if (target != null && stream != null) Action.SlideAdjust(target, stream) else null
        }
        ActionId.EDITABLE_ACTION -> editableInnerAction?.let { innerAction ->
            Action.EditableAction(
                innerAction = innerAction,
                customLabel = editableCustomLabel.trim().takeIf(String::isNotEmpty),
            )
        }
        ActionId.MULTI_ACTION -> sequenceActions.takeIf { it.isNotEmpty() }
            ?.let { Action.Sequence(it.toList()) }
        ActionId.REPEAT -> {
            val times = firstValue.toIntOrNull()?.takeIf { it >= 0 }
            if (times != null && sequenceActions.isNotEmpty()) Action.Repeat(sequenceActions.toList(), times) else null
        }
        ActionId.CONDITIONAL -> condition?.takeIf(::isValidCondition)
            ?.let { selectedCondition -> Action.Conditional(selectedCondition, thenAction, elseAction) }
        ActionId.SUB_GESTURES -> if (subGestureActions.any { it.id != ActionId.NONE }) {
            Action.SubGestures(
                hold = subGestureActions[0],
                swipeLeft = subGestureActions[1],
                swipeRight = subGestureActions[2],
                swipeUp = subGestureActions[3],
                swipeDown = subGestureActions[4],
            )
        } else null
        ActionId.SET_VARIABLE -> firstValue.trim().takeIf(String::isNotEmpty)?.let {
            Action.SetVariable(
                name = it,
                value = secondValue,
                operator = VariableOperator.valueOf(variableOperator),
            )
        }
        ActionId.TOAST -> firstValue.takeIf(String::isNotEmpty)?.let { Action.Toast(it) }
        ActionId.SPEECH -> firstValue.takeIf(String::isNotEmpty)?.let { Action.Speech(it) }
        ActionId.NOTIFY -> if (firstValue.isNotBlank() && secondValue.isNotBlank()) {
            Action.Notify(title = firstValue, text = secondValue)
        } else null
        ActionId.VIBRATE -> firstValue.toLongOrNull()?.takeIf { it >= 0 }?.let { Action.Vibrate(it) }
        ActionId.PLAY_SOUND -> Action.PlaySound(firstValue.trim().takeIf(String::isNotEmpty))
        else -> null
    }

    fun requestChildAction(target: String) {
        childActionTarget = target
        showChildActionPicker = true
    }

    fun acceptChildAction(action: Action) {
        when {
            childActionTarget == "sequence" -> sequenceActions.add(action)
            childActionTarget.startsWith("sequence:") -> {
                childActionTarget.substringAfter(':').toIntOrNull()
                    ?.takeIf { it in sequenceActions.indices }
                    ?.let { sequenceActions[it] = action }
            }
            childActionTarget == "then" -> thenAction = action
            childActionTarget == "else" -> elseAction = action
            childActionTarget.startsWith("sub:") -> {
                childActionTarget.substringAfter(':').toIntOrNull()
                    ?.takeIf { it in subGestureActions.indices }
                    ?.let { subGestureActions[it] = action }
            }
        }
        showChildActionPicker = false
    }

    fun dismissConfiguration() {
        discardWidgetSelection()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::dismissConfiguration,
        title = { Text("Set up ${ActionCatalog.title(actionId)}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                when (actionId) {
                    ActionId.FLOATING_WIDGET -> {
                        Text("Choose an installed widget, then configure its movable, closable overlay.")
                        ConfigField(widgetWidthDp, { widgetWidthDp = it }, "Width in dp (0 = preferred)", numeric = true)
                        ConfigField(widgetHeightDp, { widgetHeightDp = it }, "Height in dp (0 = preferred)", numeric = true)
                        ConfigField(widgetXPercent, { widgetXPercent = it }, "Starting X position (0–100%)", numeric = true)
                        ConfigField(widgetYPercent, { widgetYPercent = it }, "Starting Y position (0–100%)", numeric = true)
                        ConfigField(widgetTimeoutSeconds, { widgetTimeoutSeconds = it }, "Auto-close seconds (0 = never)", numeric = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = widgetRememberPosition,
                                onCheckedChange = { widgetRememberPosition = it },
                            )
                            Text("Remember position after dragging")
                        }
                        if (floatingWidgetId > 0) {
                            val selectedProvider = runCatching {
                                appWidgetManager.getAppWidgetInfo(floatingWidgetId)
                            }.getOrNull()
                            val selectedLabel = selectedProvider?.let { provider ->
                                runCatching { provider.loadLabel(context.packageManager).toString() }
                                    .getOrDefault(provider.provider.className)
                            } ?: "Widget #$floatingWidgetId"
                            Text("Selected: $selectedLabel")
                        }
                        if (pendingWidgetId > 0) {
                            Text("Finish setup in Android to continue.")
                        }
                        if (widgetProviders.isEmpty()) {
                            Text("No installed app widgets are available.")
                        } else {
                            widgetProviders.forEach { provider ->
                                val label = runCatching { provider.loadLabel(context.packageManager).toString() }
                                    .getOrDefault(provider.provider.className)
                                TextButton(
                                    enabled = pendingWidgetId == 0,
                                    onClick = { chooseWidgetProvider(provider) },
                                ) {
                                    Text("Use $label")
                                }
                            }
                        }
                        floatingWidgetMessage?.takeIf(String::isNotBlank)?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ActionId.LAUNCH_APP, ActionId.KILL_APP -> {
                        ConfigField(
                            value = firstValue,
                            onValueChange = { firstValue = it },
                            label = "Package name",
                        )
                        if (actionId == ActionId.LAUNCH_APP) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = launchAsRoot, onCheckedChange = { launchAsRoot = it })
                                Text("Launch through root shell")
                            }
                        }
                    }
                    ActionId.LAUNCH_ACTIVITY, ActionId.ACTIVITY_FINISH, ActionId.ACTIVITY_113, ActionId.ACTIVITY_114 -> {
                        ConfigField(firstValue, { firstValue = it }, "Package name")
                        ConfigField(secondValue, { secondValue = it }, "Activity class (optional)")
                        ConfigField(thirdValue, { thirdValue = it }, "Intent data URI (optional)")
                    }
                    ActionId.SHELL, ActionId.SHELL_SU -> {
                        ConfigField(
                            firstValue,
                            { firstValue = it },
                            "Shell command",
                            singleLine = false,
                        )
                        if (actionId == ActionId.SHELL) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = shellAsRoot, onCheckedChange = { shellAsRoot = it })
                                Text("Run with root")
                            }
                        }
                    }
                    ActionId.DELAY -> ConfigField(firstValue, { firstValue = it }, "Milliseconds", numeric = true)
                    ActionId.INJECT_TEXT, ActionId.INJECT_TEXT_ALT -> ConfigField(
                        firstValue,
                        { firstValue = it },
                        "Text to type",
                        singleLine = false,
                    )
                    ActionId.INJECT_KEY_EVENT, ActionId.INJECT_KEY_EVENT_ALT -> {
                        ConfigField(firstValue, { firstValue = it }, "Android key code", numeric = true)
                        ConfigField(secondValue, { secondValue = it }, "Meta state (optional, default 0)", numeric = true)
                    }
                    ActionId.INJECT_GESTURE -> {
                        Text("Choose a recorded gesture or record a new one. No coordinates are needed.")
                        Button(onClick = {
                            if (Settings.canDrawOverlays(context)) {
                                launchGestureRecorder()
                            } else {
                                gesturePermissionLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        }) {
                            Text("Record new gesture")
                        }
                        if (awaitingRecording) {
                            Text("After saving the gesture, it will be selected automatically.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        recordingError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        if (liveConfig.recordedGestures.isEmpty()) {
                            Text("No recorded gestures yet. Tap Record new gesture to get started.")
                        }
                        liveConfig.recordedGestures.forEach { record ->
                            FilterChip(
                                selected = recordedGestureId == record.id,
                                onClick = { recordedGestureId = record.id },
                                label = {
                                    Text("${record.name} · ${com.edgeflow.core.GestureTracePolicy.duration(record)} ms")
                                },
                            )
                        }
                        if (recordedGestureId.isNotBlank()) {
                            Text("Playback speed: ${recordedGestureSpeed}%")
                            Slider(
                                value = recordedGestureSpeed.toFloat(),
                                onValueChange = {
                                    recordedGestureSpeed = (it / 25f).toInt().coerceIn(2, 8) * 25
                                },
                                valueRange = 50f..200f,
                                steps = 5,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                GesturePlacement.entries.forEach { placement ->
                                    FilterChip(
                                        selected = recordedGesturePlacement == placement,
                                        onClick = { recordedGesturePlacement = placement },
                                        label = { Text(when (placement) {
                                            GesturePlacement.ORIGINAL -> "Original position"
                                            GesturePlacement.CENTER -> "Center on screen"
                                            GesturePlacement.CUSTOM -> "Custom anchor"
                                        }) },
                                    )
                                }
                            }
                            if (recordedGesturePlacement == GesturePlacement.CUSTOM) {
                                Text("Anchor X: ${gestureAnchorX}%")
                                Slider(gestureAnchorX.toFloat(), { gestureAnchorX = it.toInt() },
                                    valueRange = 0f..100f)
                                Text("Anchor Y: ${gestureAnchorY}%")
                                Slider(gestureAnchorY.toFloat(), { gestureAnchorY = it.toInt() },
                                    valueRange = 0f..100f)
                            }
                        } else if (existingAction is Action.InjectTap) {
                            Text("Legacy coordinate tap: choose a recording to replace it, or cancel to keep it unchanged.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ActionId.PIE, ActionId.SIDE_BAR, ActionId.SHORTCUTS_PANEL -> {
                        Text("Choose the edge where the menu should appear.")
                        Edge.entries.chunked(2).forEach { rowEdges ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                rowEdges.forEach { edge ->
                                    FilterChip(
                                        selected = edgeIndex == edge.index,
                                        onClick = { edgeIndex = edge.index },
                                        label = { Text(edge.label) },
                                    )
                                }
                            }
                        }
                    }
                    ActionId.CUSTOM_PANEL -> {
                        Text("Available panels: ${config.panels.joinToString { "${it.id}: ${it.name}" }.ifEmpty { "none" }}")
                        ConfigField(firstValue, { firstValue = it }, "Panel ID", numeric = true)
                    }
                    ActionId.SAVED_MULTI_ACTION, ActionId.SAVED_MULTI_ACTION_ALT -> {
                        Text("Saved actions: ${config.savedMultiActions.size}")
                        ConfigField(firstValue, { firstValue = it }, "Saved action slot (starting at 0)", numeric = true)
                    }
                    ActionId.ADJUST, ActionId.ADJUST_VOLUME -> {
                        ConfigField(firstValue, { firstValue = it }, "Adjustment amount", numeric = true)
                        ConfigField(adjustStream, { adjustStream = it.uppercase() }, "Stream (${AdjustStream.entries.joinToString()})")
                    }
                    ActionId.RINGER_MODE -> {
                        RingerModeValue.entries.forEach { mode ->
                            FilterChip(
                                selected = ringerMode == mode.name,
                                onClick = { ringerMode = mode.name },
                                label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                            )
                        }
                    }
                    ActionId.MEDIA_CONTROL -> {
                        Text("Choose a media playback command.")
                        MediaCommand.entries.chunked(2).forEach { commands ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                commands.forEach { command ->
                                    FilterChip(
                                        selected = mediaCommand == command.name,
                                        onClick = { mediaCommand = command.name },
                                        label = { Text(command.label) },
                                    )
                                }
                            }
                        }
                    }
                    ActionId.EDIT_TEXT -> {
                        Text("Send the selected keyboard shortcut to the currently focused text field.")
                        EditTextCommand.entries.chunked(2).forEach { commands ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                commands.forEach { command ->
                                    FilterChip(
                                        selected = editTextCommand == command.name,
                                        onClick = { editTextCommand = command.name },
                                        label = { Text(command.label) },
                                    )
                                }
                            }
                        }
                    }
                    ActionId.SUCCESSIVE_ADJUST -> {
                        Text("Target")
                        SuccessiveAdjustTarget.entries.chunked(2).forEach { targets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                targets.forEach { target ->
                                    FilterChip(
                                        selected = successiveTarget == target.name,
                                        onClick = { successiveTarget = target.name },
                                        label = { Text(target.label) },
                                    )
                                }
                            }
                        }
                        if (successiveTarget == SuccessiveAdjustTarget.VOLUME.name) {
                            Text("Audio stream")
                            AdjustStream.entries.chunked(3).forEach { streams ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    streams.forEach { stream ->
                                        FilterChip(
                                            selected = successiveStream == stream.name,
                                            onClick = { successiveStream = stream.name },
                                            label = { Text(stream.name.lowercase().replace('_', ' ')) },
                                        )
                                    }
                                }
                            }
                        }
                        val directions = when (successiveTarget) {
                            SuccessiveAdjustTarget.SCROLL.name -> "Up" to "Down"
                            SuccessiveAdjustTarget.CARET.name -> "Left" to "Right"
                            else -> "Increase" to "Decrease"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            FilterChip(
                                selected = successiveIncrease,
                                onClick = { successiveIncrease = true },
                                label = { Text(directions.first) },
                            )
                            FilterChip(
                                selected = !successiveIncrease,
                                onClick = { successiveIncrease = false },
                                label = { Text(directions.second) },
                            )
                        }
                    }
                    ActionId.SLIDE_ADJUST -> {
                        Text("Continuous adjustment is intended for swipe gestures. Moving up or right increases the value; moving down or left decreases it.")
                        SlideAdjustTarget.entries.chunked(2).forEach { targets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                targets.forEach { target ->
                                    FilterChip(
                                        selected = slideTarget == target.name,
                                        onClick = { slideTarget = target.name },
                                        label = { Text(target.label) },
                                    )
                                }
                            }
                        }
                        if (slideTarget == SlideAdjustTarget.VOLUME.name) {
                            Text("Audio stream")
                            AdjustStream.entries.chunked(3).forEach { streams ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    streams.forEach { stream ->
                                        FilterChip(
                                            selected = slideStream == stream.name,
                                            onClick = { slideStream = stream.name },
                                            label = { Text(stream.name.lowercase().replace('_', ' ')) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ActionId.EDITABLE_ACTION -> {
                        Text("Choose an action to run.")
                        TextButton(onClick = { showEditableActionPicker = true }) {
                            Text("Action: ${editableInnerAction?.let { ActionCatalog.describe(it) } ?: "Choose an action"}")
                        }
                        ConfigField(
                            value = editableCustomLabel,
                            onValueChange = { editableCustomLabel = it },
                            label = "Custom label (optional)",
                        )
                    }
                    ActionId.MULTI_ACTION -> ActionStepsEditor(
                        actions = sequenceActions,
                        addLabel = "Add step",
                        onAdd = { requestChildAction("sequence") },
                        onRemove = { sequenceActions.removeAt(it) },
                        onEdit = { index ->
                            val original = sequenceActions[index]
                            val spec = ActionCatalog.all.firstOrNull { it.id == original.id }
                            if (spec?.requiresConfig == true) {
                                childActionTarget = "sequence:$index"
                                editingChildAction = original
                                childActionConfigId = original.id
                            } else requestChildAction("sequence:$index")
                        },
                        onMove = { index, direction ->
                            val target = index + direction
                            if (index in sequenceActions.indices && target in sequenceActions.indices) {
                                val action = sequenceActions.removeAt(index)
                                sequenceActions.add(target, action)
                            }
                        },
                        onDuplicate = { index ->
                            if (index in sequenceActions.indices) {
                                sequenceActions.add(index + 1, sequenceActions[index])
                            }
                        },
                    )
                    ActionId.REPEAT -> {
                        ConfigField(firstValue, { firstValue = it }, "Repeat count (0 means until stopped)", numeric = true)
                        ActionStepsEditor(
                            actions = sequenceActions,
                            addLabel = "Add repeated action",
                            onAdd = { requestChildAction("sequence") },
                            onRemove = { sequenceActions.removeAt(it) },
                            onEdit = { index ->
                                val original = sequenceActions[index]
                                val spec = ActionCatalog.all.firstOrNull { it.id == original.id }
                                if (spec?.requiresConfig == true) {
                                    childActionTarget = "sequence:$index"
                                    editingChildAction = original
                                    childActionConfigId = original.id
                                } else requestChildAction("sequence:$index")
                            },
                            onMove = { index, direction ->
                                val target = index + direction
                                if (index in sequenceActions.indices && target in sequenceActions.indices) {
                                    val action = sequenceActions.removeAt(index)
                                    sequenceActions.add(target, action)
                                }
                            },
                            onDuplicate = { index ->
                                if (index in sequenceActions.indices) sequenceActions.add(index + 1, sequenceActions[index])
                            },
                        )
                    }
                    ActionId.CONDITIONAL -> {
                        ConditionEditor(
                            condition = condition,
                            onConditionChange = { condition = it },
                        )
                        TextButton(onClick = { requestChildAction("then") }) {
                            Text("Then: ${ActionCatalog.describe(thenAction)}")
                        }
                        TextButton(onClick = { requestChildAction("else") }) {
                            Text("Otherwise: ${ActionCatalog.describe(elseAction)}")
                        }
                    }
                    ActionId.SUB_GESTURES -> {
                        Text("Use this on an edge swipe or long press. After the first gesture, continue moving or hold to trigger the selected secondary action.")
                        SUB_GESTURE_LABELS.forEachIndexed { index, label ->
                            TextButton(onClick = { requestChildAction("sub:$index") }) {
                                Text("$label: ${ActionCatalog.describe(subGestureActions[index])}")
                            }
                        }
                    }
                    ActionId.SET_VARIABLE -> {
                        ConfigField(firstValue, { firstValue = it }, "Variable name")
                        ConfigField(secondValue, { secondValue = it }, "Value")
                        Text("Operation")
                        VariableOperator.entries.chunked(3).forEach { operators ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                operators.forEach { operator ->
                                    FilterChip(
                                        selected = variableOperator == operator.name,
                                        onClick = { variableOperator = operator.name },
                                        label = { Text(operator.name.lowercase()) },
                                    )
                                }
                            }
                        }
                    }
                    ActionId.TOAST, ActionId.SPEECH -> ConfigField(
                        firstValue,
                        { firstValue = it },
                        if (actionId == ActionId.TOAST) "Message" else "Text to speak",
                        singleLine = false,
                    )
                    ActionId.NOTIFY -> {
                        ConfigField(firstValue, { firstValue = it }, "Notification title")
                        ConfigField(secondValue, { secondValue = it }, "Notification message", singleLine = false)
                    }
                    ActionId.VIBRATE -> ConfigField(firstValue, { firstValue = it }, "Milliseconds", numeric = true)
                    ActionId.PLAY_SOUND -> ConfigField(firstValue, { firstValue = it }, "Sound URI (blank uses default)")
                    else -> Text(
                        "No parameter editor is available for this action.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (builtAction == null) {
                    val explanation = when (actionId) {
                        ActionId.INJECT_GESTURE -> "Record a gesture or select an existing recording before saving."
                        ActionId.INJECT_KEY_EVENT, ActionId.INJECT_KEY_EVENT_ALT ->
                            "Enter a non-negative Android key code and optional meta-state."
                        ActionId.REPEAT -> "Enter a valid repeat count and add at least one action."
                        ActionId.MULTI_ACTION -> "Add at least one action to this sequence."
                        ActionId.CONDITIONAL -> "Select a valid condition before saving."
                        ActionId.SUB_GESTURES -> "Bind at least one secondary gesture action."
                        ActionId.SAVED_MULTI_ACTION, ActionId.SAVED_MULTI_ACTION_ALT ->
                            "Choose the slot of an existing, non-deleted saved action."
                        else -> "Complete the required fields to enable Save."
                    }
                    Text(explanation, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = builtAction != null,
                onClick = { builtAction?.let(onConfirm) },
            ) {
                Text("Use action")
            }
        },
        dismissButton = { TextButton(onClick = ::dismissConfiguration) { Text("Cancel") } },
    )

    if (showChildActionPicker) {
        SimpleActionSelectionDialog(
            onDismiss = { showChildActionPicker = false },
            onSelect = { spec ->
                showChildActionPicker = false
                if (spec.requiresConfig) {
                    editingChildAction = null
                    childActionConfigId = spec.id
                }
                else acceptChildAction(actionForId(spec.id))
            },
        )
    }
    if (childActionConfigId >= 0) {
        ActionConfigurationDialog(
            actionId = childActionConfigId,
            config = config,
            existingAction = editingChildAction,
            onDismiss = { childActionConfigId = -1; editingChildAction = null },
            onConfirm = { action ->
                childActionConfigId = -1
                editingChildAction = null
                acceptChildAction(action)
            },
        )
    }
    if (showEditableActionPicker) {
        EditableActionSelectionDialog(
            onDismiss = { showEditableActionPicker = false },
            onSelect = { spec ->
                showEditableActionPicker = false
                if (spec.requiresConfig) {
                    editableActionConfigId = spec.id
                } else {
                    editableInnerAction = actionForId(spec.id)
                }
            },
        )
    }
    if (editableActionConfigId >= 0) {
        ActionConfigurationDialog(
            actionId = editableActionConfigId,
            config = config,
            onDismiss = { editableActionConfigId = -1 },
            onConfirm = { action ->
                editableInnerAction = action
                editableActionConfigId = -1
            },
        )
    }
}

@Composable
private fun ActionStepsEditor(
    actions: List<Action>,
    addLabel: String,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDuplicate: (Int) -> Unit,
) {
    Text(
        "Steps run in order. Configure parameters, replace, duplicate or reorder individual steps.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    actions.forEachIndexed { index, action ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${index + 1}. ${ActionCatalog.describe(action)}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onEdit(index) }) { Text("Edit") }
                TextButton(enabled = index > 0, onClick = { onMove(index, -1) }) { Text("↑") }
                TextButton(enabled = index < actions.lastIndex, onClick = { onMove(index, 1) }) { Text("↓") }
                TextButton(onClick = { onDuplicate(index) }) { Text("Copy") }
            }
            TextButton(onClick = { onRemove(index) }) { Text("Remove step") }
        }
    }
    TextButton(onClick = onAdd) { Text(addLabel) }
}

@Composable
private fun SimpleActionSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (ActionSpec) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(query) {
        ActionCatalog.betaSearch(query).filterNot { it.id == ActionId.DEFAULT }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose action") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(options, key = ActionSpec::id) { spec ->
                        ActionRow(spec = spec, onClick = { onSelect(spec) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditableActionSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (ActionSpec) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(query) {
        ActionCatalog.betaSearch(query).filterNot { it.id == ActionId.EDITABLE_ACTION }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose wrapped action") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(options, key = ActionSpec::id) { spec ->
                        ActionRow(spec = spec, onClick = { onSelect(spec) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val SUB_GESTURE_LABELS = listOf("Hold", "Swipe left", "Swipe right", "Swipe up", "Swipe down")

@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,
        ),
    )
}

/** Title describing what the picker is about to change. */
private fun pickerTitle(family: BindingFamily, primary: Int, secondary: Int, target: String?): String = when (family) {
    BindingFamily.EDGE_GESTURE -> {
        val zone = EdgeZone.of(primary)?.label ?: "Zone"
        val gesture = Gesture.of(secondary)?.label ?: "Gesture"
        "$zone · $gesture"
    }

    BindingFamily.HARDWARE_KEY -> {
        val key = HardwareKey.of(primary)?.label ?: "Key"
        val gesture = KeyGesture.of(secondary)?.label ?: "Gesture"
        "$key · $gesture"
    }

    BindingFamily.PIE_SLOT -> {
        val edge = Edge.of(primary)?.label ?: "Pie"
        val level = if (secondary < com.edgeflow.core.model.PieConfig.LEVEL_1_SLOTS) 1 else 2
        val index = if (level == 1) secondary + 1 else secondary - com.edgeflow.core.model.PieConfig.LEVEL_1_SLOTS + 1
        "$edge pie · L$level item $index"
    }

    BindingFamily.SIDE_BAR -> {
        val edge = Edge.of(primary)?.label ?: "Side bar"
        "$edge side bar · item ${secondary + 1}"
    }

    BindingFamily.STATE_TRIGGER -> "Trigger $primary"
    BindingFamily.APP_STATE -> "${target ?: "App"} · ${listOf("started", "stopped", "focus gained", "focus lost").getOrElse(primary) { "event" }}"
    BindingFamily.SAVED_ACTION -> "Saved action ${secondary + 1}"
    BindingFamily.PANEL_CELL -> "Panel cell"
    BindingFamily.SCHEDULE -> "Schedule $primary"
    BindingFamily.SHORTCUT_PANEL_CELL -> "${Edge.of(primary)?.label ?: "Edge"} shortcuts · item ${secondary + 1}"
}

private const val SEARCH_RESULT_LIMIT = 12
