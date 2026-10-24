package com.edgeflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.Edge
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.setGestureAction
import com.edgeflow.data.setZoneEnabled
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing
import com.edgeflow.R

/**
 * Edge gesture configuration.
 *
 * ## Layout rationale
 *
 * There are 16 zones and 7 gestures, so a flat list of 112 rows would be
 * unusable. The screen is therefore grouped by edge, and within an edge by
 * segment, with the gesture bindings shown as a compact grid of chips. That
 * mirrors how the user thinks about it — "the right edge does these things" —
 * and keeps the common case (one full-length edge) at three visible rows.
 *
 * A zone with nothing bound shows "Not set" and resolves to its parent edge at
 * run time, which the row spells out so the fallback is not a hidden surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesScreen(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Edge gestures") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // The small status diagram stays visible while the long gesture
            // configuration list scrolls below it.
            ScreenPreview(
                gestureTable = config.gestures,
                globallyEnabled = config.settings.gestureControlEnabled,
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.xxl,
                ),
            ) {
                items(Edge.entries.toList()) { edge ->
                    EdgeSection(
                        edge = edge,
                        configRepository = configRepository,
                        onPickAction = onPickAction,
                        gestureTable = config.gestures,
                    )
                    CardSpacer()
                }
            }
        }
    }
}

/**
 * A schematic of the device with the gesture strips drawn on it.
 *
 * Drawn rather than shipped as an image so it adapts to any aspect ratio and
 * picks up the theme colours. It is the fastest way to answer "which strip am I
 * actually configuring?".
 */
@Composable
private fun ScreenPreview(
    gestureTable: com.edgeflow.core.model.GestureTable,
    globallyEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeSegments = EdgeZone.entries.count { zone ->
        !zone.isFullEdge && globallyEnabled && gestureTable.isZoneEnabled(zone)
    }
    SectionCard(
        title = stringResource(R.string.gesture_preview_segment_count, activeSegments),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 188.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                PreviewEdge(EdgeZone.LEFT, gestureTable, globallyEnabled,
                    Modifier.align(Alignment.CenterStart))
                PreviewEdge(EdgeZone.RIGHT, gestureTable, globallyEnabled,
                    Modifier.align(Alignment.CenterEnd))
                PreviewEdge(EdgeZone.TOP, gestureTable, globallyEnabled,
                    Modifier.align(Alignment.TopCenter))
                PreviewEdge(EdgeZone.BOTTOM, gestureTable, globallyEnabled,
                    Modifier.align(Alignment.BottomCenter))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = if (activeSegments == 0) stringResource(R.string.gesture_preview_off)
                        else stringResource(R.string.gesture_preview_on),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (activeSegments > 0)
                        stringResource(R.string.gesture_preview_bind_hint)
                    else stringResource(R.string.gesture_preview_default_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Renders three individually highlighted physical touch regions per edge. */
@Composable
private fun PreviewEdge(
    edge: EdgeZone,
    table: com.edgeflow.core.model.GestureTable,
    globallyEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val segments = EdgeZone.segmentsOf(edge)
    val vertical = edge == EdgeZone.LEFT || edge == EdgeZone.RIGHT
    val axis = if (vertical) Modifier.width(11.dp).fillMaxHeight()
        else Modifier.height(11.dp).fillMaxWidth()
    val enabledColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val boundColor = MaterialTheme.colorScheme.tertiary
    val neutralColor = MaterialTheme.colorScheme.surface
    @Composable
    fun Segment(zone: EdgeZone, partModifier: Modifier) {
        val active = globallyEnabled && table.isZoneEnabled(zone)
        val bound = Gesture.entries.any { table.resolve(zone, it).id != ActionId.NONE }
        Box(
            modifier = partModifier
                .padding(1.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (active) enabledColor else disabledColor)
                .border(1.dp, if (active && bound) boundColor else neutralColor,
                    MaterialTheme.shapes.extraSmall),
        )
    }
    if (vertical) Column(modifier.then(axis)) {
        segments.forEach { zone -> Segment(zone, Modifier.fillMaxWidth().weight(1f)) }
    } else Row(modifier.then(axis)) {
        segments.forEach { zone -> Segment(zone, Modifier.fillMaxHeight().weight(1f)) }
    }
}

/** All gesture bindings for one edge, grouped by segment. */
@Composable
private fun EdgeSection(
    edge: Edge,
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
    gestureTable: com.edgeflow.core.model.GestureTable,
) {
    val fullEdge = EdgeZone.entries.first { it.parent == edgeToZone(edge) && it.isFullEdge }
    val segments = EdgeZone.segmentsOf(fullEdge)

    SectionCard(title = edge.label) {
        val edgeEnabled = gestureTable.isZoneEnabled(fullEdge)
        val enabledPartCount = segments.count { gestureTable.isZoneEnabled(it) }
        val activeBindings = (listOf(fullEdge) + segments).sumOf { zone ->
            gestureTable.bindings[zone.id].orEmpty().values.count { it.id != ActionId.NONE }
        }
        SwitchRow(
            title = stringResource(R.string.gesture_switch_whole_edge),
            checked = edgeEnabled,
            onCheckedChange = { enabled -> configRepository.setZoneEnabled(fullEdge, enabled) },
            subtitle = when {
                enabledPartCount == 0 -> stringResource(R.string.gesture_edge_off)
                !edgeEnabled -> stringResource(R.string.gesture_edge_partial, enabledPartCount)
                activeBindings == 0 -> stringResource(R.string.gesture_edge_no_action)
                else -> stringResource(R.string.gesture_edge_count, activeBindings)
            },
        )
        RowDivider()
        GestureBindingList(
            zone = fullEdge,
            label = "Full ${edge.label.lowercase()} edge",
            configRepository = configRepository,
            onPickAction = onPickAction,
            enabled = enabledPartCount > 0,
        )

        segments.forEach { segment ->
            RowDivider()
            val segmentEnabled = gestureTable.isZoneEnabled(segment)
            SwitchRow(
                title = stringResource(R.string.gesture_switch_part, segment.label),
                checked = segmentEnabled,
                onCheckedChange = { configRepository.setZoneEnabled(segment, it) },
                subtitle = if (segmentEnabled) stringResource(R.string.gesture_segment_on)
                    else stringResource(R.string.gesture_segment_off),
            )
            GestureBindingList(
                zone = segment,
                label = segment.label,
                configRepository = configRepository,
                onPickAction = onPickAction,
                enabled = segmentEnabled,
            )
        }
    }
}

/** The seven gesture rows for one zone. */
@Composable
private fun GestureBindingList(
    zone: EdgeZone,
    label: String,
    configRepository: ConfigRepository,
    onPickAction: (Int, Int, Int) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val config by configRepository.config.collectAsStateWithLifecycle()

    val boundCount = Gesture.entries.count {
        config.gestures.bindings[zone.id]?.get(it.index)?.id?.let { id -> id != ActionId.NONE } == true
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.gesture_row_disabled)
                        boundCount == 0 -> stringResource(R.string.gesture_row_assign)
                        else -> stringResource(R.string.gesture_row_bound, boundCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                Gesture.entries.forEach { gesture ->
                    val action = config.gestures.bindings[zone.id]?.get(gesture.index)
                    val isBound = action != null && action.id != ActionId.NONE
                    SettingRow(
                        title = gesture.label,
                        subtitle = action?.let { ActionCatalog.describe(it) } ?: "Not set",
                        modifier = Modifier.padding(start = Spacing.sm),
                        onClick = {
                            onPickAction(BindingFamily.EDGE_GESTURE.id, zone.id, gesture.index)
                        },
                    )
                }
                if (!zone.isFullEdge) {
                    Text(
                        text = "Unset gestures fall back to the full-length ${zone.parent.label.lowercase()}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.lg + Spacing.sm, top = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** Maps an [Edge] to its full-length [EdgeZone]. */
private fun edgeToZone(edge: Edge): EdgeZone = when (edge) {
    Edge.LEFT -> EdgeZone.LEFT
    Edge.RIGHT -> EdgeZone.RIGHT
    Edge.TOP -> EdgeZone.TOP
    Edge.BOTTOM -> EdgeZone.BOTTOM
}
