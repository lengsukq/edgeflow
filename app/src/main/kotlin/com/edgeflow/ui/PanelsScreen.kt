package com.edgeflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.Edge
import com.edgeflow.core.model.PanelConfig
import com.edgeflow.core.model.PieConfig
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.ValueRow
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing

/**
 * Panels: pie menus and side bars.
 *
 * Split into tabs rather than stacked sections because they are alternatives the
 * user picks between, not steps in a sequence. The pie is drawn as a ring
 * because a list of twelve numbered slots cannot convey which direction each
 * item appears in, which is the only thing that matters when choosing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Panels") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Pie", "Side bars", "Shortcuts", "Custom").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> PieTab(configRepository, onPickAction)
                1 -> SideBarTab(configRepository, onPickAction)
                2 -> ShortcutPanelsTab(configRepository, onPickAction)
                else -> CustomPanelsTab(configRepository, onPickAction)
            }
        }
    }
}

/** Pie menu configuration for each edge. */
@Composable
private fun PieTab(
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl,
        ),
    ) {
        item {
            PieLayoutCard()
            CardSpacer()
        }

        items(Edge.entries.toList()) { edge ->
            val pieEdge = config.pie.edge(edge)
            SectionCard(
                title = "${edge.label} pie",
                subtitle = "Inner ring is level 1, outer ring is level 2.",
            ) {
                PieSlotRows(
                    label = "Level 1",
                    count = PieConfig.LEVEL_1_SLOTS,
                    startSlot = 0,
                    actionAt = pieEdge::slot,
                    onPick = { slot -> onPickAction(BindingFamily.PIE_SLOT.id, edge.index, slot) },
                )
                PieSlotRows(
                    label = "Level 2",
                    count = PieConfig.LEVEL_2_SLOTS,
                    startSlot = PieConfig.LEVEL_1_SLOTS,
                    actionAt = pieEdge::slot,
                    onPick = { slot -> onPickAction(BindingFamily.PIE_SLOT.id, edge.index, slot) },
                )
            }
            CardSpacer()
        }
    }
}

/** The slot rows for one ring of one edge's pie. */
@Composable
private fun PieSlotRows(
    label: String,
    count: Int,
    startSlot: Int,
    actionAt: (Int) -> com.edgeflow.core.model.Action,
    onPick: (Int) -> Unit,
) {
    repeat(count) { offset ->
        val slot = startSlot + offset
        val action = actionAt(slot)
        ValueRow(
            title = "$label · ${offset + 1}",
            value = if (action.id == ActionId.NONE) "Not set" else ActionCatalog.describe(action),
            onClick = { onPick(slot) },
        )
    }
}

/**
 * Illustrates the pie's two rings.
 *
 * Drawn on a [Canvas] so the slot positions come from the same polar maths the
 * overlay will use at run time. Keeping the geometry here means the preview
 * cannot drift from the real layout.
 */
@Composable
private fun PieLayoutCard() {
    SectionCard(
        title = "Pie layout",
        subtitle = "${PieConfig.SLOTS_PER_EDGE} slots per edge. Level 1 sits on the inner ring, " +
            "level 2 on the outer.",
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
        val outlineColor = MaterialTheme.colorScheme.outlineVariant

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f),
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val innerRadius = maxRadius * 0.34f
                val outerRadius = maxRadius * 0.78f
                val markerRadius = maxRadius * 0.075f

                drawCircle(color = surfaceColor, radius = maxRadius, center = center)
                drawCircle(
                    color = outlineColor,
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = maxRadius * 0.02f),
                )

                drawRing(center, innerRadius, PieConfig.LEVEL_1_SLOTS, markerRadius, primaryColor)
                drawRing(center, outerRadius, PieConfig.LEVEL_2_SLOTS, markerRadius, secondaryColor)
            }
        }
    }
}

/**
 * Draws [count] evenly spaced markers on a ring.
 *
 * Slots start at the top and run clockwise, matching how the overlay lays them
 * out and how users describe them ("the top one").
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    center: Offset,
    radius: Float,
    count: Int,
    markerRadius: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    repeat(count) { index ->
        val angle = (2.0 * Math.PI * index / count) - (Math.PI / 2.0)
        val position = Offset(
            x = center.x + (radius * Math.cos(angle)).toFloat(),
            y = center.y + (radius * Math.sin(angle)).toFloat(),
        )
        drawCircle(color = color, radius = markerRadius, center = position)
    }
}

/** Per-edge shortcut grids. */
@Composable
private fun ShortcutPanelsTab(
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl,
        ),
    ) {
        item {
            SectionCard(
                title = "Shortcut panels",
                subtitle = "Each edge has a four-row grid. Bind each cell to an action.",
            ) {
                CardSpacer()
            }
        }

        items(Edge.entries.toList()) { edge ->
            val panel = config.shortcutPanels.edges[edge.index]
                ?: PanelConfig(id = edge.index, name = "${edge.label} shortcuts", rows = 4, columns = 3)
            SectionCard(
                title = "${edge.label} shortcuts",
                subtitle = "${panel.rows} rows · ${panel.columns} columns",
            ) {
                repeat(panel.rows * panel.columns) { index ->
                    if (index > 0) RowDivider()
                    val action = panel.cells.getOrElse(index) { com.edgeflow.core.model.Action.None }
                    ValueRow(
                        title = "Item ${index + 1}",
                        value = if (action.id == ActionId.NONE) "Not set" else ActionCatalog.describe(action),
                        onClick = {
                            onPickAction(BindingFamily.SHORTCUT_PANEL_CELL.id, edge.index, index)
                        },
                    )
                }
            }
            CardSpacer()
        }
    }
}

/** Custom panels are independently addressable and can be launched by panel ID. */
@Composable
private fun CustomPanelsTab(
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorPanel by remember { mutableStateOf<PanelConfig?>(null) }
    var editorGeneration by rememberSaveable { mutableIntStateOf(0) }
    var panelPendingDelete by remember { mutableStateOf<PanelConfig?>(null) }
    var idUnavailable by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl,
        ),
    ) {
        item {
            SectionCard(
                title = "Custom panels",
                subtitle = "Create a grid, then tap any cell to assign an action. Use the panel ID in a Custom panel action to open it.",
            ) {
                if (config.panels.isEmpty()) {
                    Text(
                        text = "No custom panels yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    )
                }
                Button(
                    onClick = {
                        editorPanel = null
                        editorGeneration++
                        editorOpen = true
                        idUnavailable = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    Text("Create panel")
                }
                if (idUnavailable) {
                    Text(
                        text = "Could not allocate a panel ID. Remove or repair an existing panel configuration first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
            CardSpacer()
        }

        items(config.panels) { panel ->
            val displayRows = panel.rows.coerceIn(MIN_CUSTOM_PANEL_DIMENSION, MAX_CUSTOM_PANEL_DIMENSION)
            val displayColumns = panel.columns.coerceIn(MIN_CUSTOM_PANEL_DIMENSION, MAX_CUSTOM_PANEL_DIMENSION)
            SectionCard(
                title = panel.name.ifBlank { "Panel ${panel.id}" },
                subtitle = "ID ${panel.id} · ${panel.rows} rows · ${panel.columns} columns",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            editorPanel = panel
                            editorGeneration++
                            editorOpen = true
                            idUnavailable = false
                        },
                    ) {
                        Text("Edit")
                    }
                    TextButton(onClick = { panelPendingDelete = panel }) {
                        Text("Delete")
                    }
                }
                repeat(displayRows * displayColumns) { index ->
                    if (index > 0) RowDivider()
                    val row = index / displayColumns
                    val column = index % displayColumns
                    val action = panel.cell(row, column)
                    ValueRow(
                        title = "Cell ${index + 1} · row ${row + 1}, column ${column + 1}",
                        value = if (action.id == ActionId.NONE) "Not set" else ActionCatalog.describe(action),
                        onClick = {
                            onPickAction(BindingFamily.PANEL_CELL.id, panel.id, index)
                        },
                    )
                }
            }
            CardSpacer()
        }
    }

    if (editorOpen) {
        PanelEditorDialog(
            initialPanel = editorPanel,
            existingPanels = config.panels,
            generation = editorGeneration,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                var allocationFailed = false
                configRepository.update { current ->
                    val panel = if (editorPanel != null) {
                        editorPanel!!.copy(name = draft.name, rows = draft.rows, columns = draft.columns)
                    } else {
                        val id = nextPanelId(current.panels)
                        if (id == null) {
                            allocationFailed = true
                            return@update current
                        }
                        PanelConfig(id = id, name = draft.name, rows = draft.rows, columns = draft.columns)
                    }
                    current.copy(panels = upsertPanel(current.panels, panel))
                }
                idUnavailable = allocationFailed
                editorOpen = false
            },
        )
    }

    panelPendingDelete?.let { panel ->
        AlertDialog(
            onDismissRequest = { panelPendingDelete = null },
            title = { Text("Delete panel?") },
            text = {
                Text("Delete ‘${panel.name.ifBlank { "Panel ${panel.id}" }}’? Actions that open this panel will no longer work.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        configRepository.update { current ->
                            current.copy(panels = removePanel(current.panels, panel.id))
                        }
                        panelPendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { panelPendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PanelEditorDialog(
    initialPanel: PanelConfig?,
    existingPanels: List<PanelConfig>,
    generation: Int,
    onDismiss: () -> Unit,
    onSave: (PanelDraftValidation.Valid) -> Unit,
) {
    var name by rememberSaveable(generation, initialPanel?.id) { mutableStateOf(initialPanel?.name.orEmpty()) }
    var rows by rememberSaveable(generation, initialPanel?.id) { mutableStateOf(initialPanel?.rows?.toString() ?: "3") }
    var columns by rememberSaveable(generation, initialPanel?.id) { mutableStateOf(initialPanel?.columns?.toString() ?: "3") }
    var validationError by rememberSaveable(generation, initialPanel?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPanel == null) "Create panel" else "Edit panel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = rows,
                        onValueChange = { rows = it; validationError = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("Rows (1–8)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = columns,
                        onValueChange = { columns = it; validationError = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("Columns (1–8)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
                Text(
                    text = "Names must be unique and no longer than $MAX_CUSTOM_PANEL_NAME_LENGTH characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                validationError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = validatePanelDraft(name, rows, columns, existingPanels, initialPanel?.id)) {
                        is PanelDraftValidation.Invalid -> validationError = result.message
                        is PanelDraftValidation.Valid -> onSave(result)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Side bar configuration. */
@Composable
private fun SideBarTab(
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xxl,
        ),
    ) {
        items(listOf(Edge.LEFT, Edge.RIGHT)) { edge ->
            val barItems = config.sideBars.items(edge)
            SectionCard(
                title = "${edge.label} side bar",
                subtitle = if (barItems.isEmpty()) {
                    "No items yet. Add entries to build a quick-action strip."
                } else {
                    "${barItems.size} item${if (barItems.size == 1) "" else "s"}"
                },
            ) {
                barItems.forEachIndexed { index, action ->
                    ValueRow(
                        title = "Item ${index + 1}",
                        value = ActionCatalog.describe(action),
                        onClick = { onPickAction(BindingFamily.SIDE_BAR.id, edge.index, index) },
                    )
                }
                ValueRow(
                    title = "Add item",
                    value = "＋",
                    onClick = { onPickAction(BindingFamily.SIDE_BAR.id, edge.index, barItems.size) },
                )
            }
            CardSpacer()
        }
    }
}
