package com.edgeflow.core.model

import com.edgeflow.core.Edge
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.KeyGesture
import com.edgeflow.core.StateTriggerId
import kotlinx.serialization.Serializable

/**
 * The complete user configuration.
 *
 * The shape is deliberately close to the reference implementation's in-memory
 * model so that behaviour ports across cleanly: gestures are a sparse
 * `zone -> gesture -> action` table, keys are `key -> gesture -> action`, and
 * the remaining trigger families each own a flat list.
 */
@Serializable
data class EdgeConfig(
    val version: Int = SCHEMA_VERSION,
    val gestures: GestureTable = GestureTable(),
    val keys: KeyTable = KeyTable(),
    val pie: PieConfig = PieConfig(),
    val panels: List<PanelConfig> = emptyList(),
    val shortcutPanels: ShortcutPanelConfig = ShortcutPanelConfig(),
    val sideBars: SideBarConfig = SideBarConfig(),
    val stateTriggers: Map<Int, Action> = emptyMap(),
    val appStateTriggers: Map<String, List<Action?>> = emptyMap(),
    val schedules: List<ScheduleEntry> = emptyList(),
    val savedMultiActions: List<NamedAction> = emptyList(),
    val actionCollection: List<NamedAction> = emptyList(),
    val recordedGestures: List<RecordedGesture> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val freezer: FreezerConfig = FreezerConfig(),
    val variables: Map<String, String> = emptyMap(),
    val settings: AppSettings = AppSettings(),
) {
    /**
     * Legacy saved-action bindings store a list INDEX, not a stable ID.
     * Removing a list element would silently redirect every later binding to
     * another user's action. Preserve the position as an inert tombstone;
     * new entries are always appended, never recycled.
     */
    fun deleteSavedAction(savedId: Int): EdgeConfig = copy(
        savedMultiActions = savedMultiActions.map { entry ->
            if (entry.id == savedId) entry.copy(action = Action.None, deleted = true)
            else entry
        },
    )

    companion object {
        /**
         * Bumped whenever the on-disk shape changes in a way that needs
         * migration. Never reuse a value.
         */
        const val SCHEMA_VERSION = 4
    }

    /** Applies additive, default-preserving migrations to the current schema. */
    fun migrateToCurrentVersion(): EdgeConfig {
        require(version <= SCHEMA_VERSION) {
            "Configuration schema $version is newer than supported schema $SCHEMA_VERSION."
        }
        if (version >= SCHEMA_VERSION) return this
        if (version == 3) {
            // v3 selected only full edges. Preserve those user choices by
            // enabling their three physical segments, without touching any
            // existing per-zone gesture binding.
            return copy(
                version = SCHEMA_VERSION,
                gestures = gestures.copy(
                    enabledZones = gestures.enabledEdges.flatMap { parentId ->
                        EdgeZone.of(parentId)?.let(EdgeZone::segmentsOf).orEmpty()
                            .map(EdgeZone::id)
                    }.filterNot { it in gestures.disabledZones }.toSet(),
                ),
            )
        }
        // v1/v2 treated all edges and keys as enabled unless explicitly
        // disabled. The new opt-in policy requires an explicit user choice.
        // Keep every existing action/binding but disable interception until
        // its edge or key is manually enabled again.
        return copy(
            version = SCHEMA_VERSION,
            gestures = gestures.copy(enabledEdges = emptySet(), enabledZones = emptySet()),
            keys = keys.copy(enabledKeys = emptySet()),
            settings = settings.copy(gestureControlEnabled = false, keyControlEnabled = false),
        )
    }
}

/**
 * Sparse gesture bindings.
 *
 * Keys are [EdgeZone] ids; values map a [Gesture] index to an [Action].
 * A missing entry means "fall back to the parent edge", and a missing parent
 * entry means "do nothing" — the same resolution order the reference
 * implementation uses.
 */
@Serializable
data class GestureTable(
    val bindings: Map<Int, Map<Int, Action>> = emptyMap(),
    /** Legacy v1/v2 field retained for lossless import/export. */
    val disabledZones: Set<Int> = emptySet(),
    /** Explicitly enabled FULL edges only. Default is fully disabled. */
    val enabledEdges: Set<Int> = emptySet(),
    /** v4: each physical third of the screen edge can be enabled separately. */
    val enabledZones: Set<Int> = emptySet(),
) {
    operator fun get(zone: EdgeZone, gesture: Gesture): Action? =
        bindings[zone.id]?.get(gesture.index)

    /**
     * Resolves a binding, walking up to the full-length edge when the specific
     * sub-zone has nothing bound.
     */
    fun resolve(zone: EdgeZone, gesture: Gesture): Action {
        val direct = get(zone, gesture)
        if (direct != null && direct.id != com.edgeflow.core.ActionId.NONE) {
            if (direct.id != com.edgeflow.core.ActionId.DEFAULT) return direct
            return parentAction(zone, gesture)
        }
        if (zone.isFullEdge) return direct ?: Action.None
        val fallback = get(zone.parent, gesture)
        return fallback?.takeUnless { it.id == com.edgeflow.core.ActionId.DEFAULT }
            ?: direct
            ?: Action.None
    }

    private fun parentAction(zone: EdgeZone, gesture: Gesture): Action {
        if (zone.isFullEdge) return Action.None
        return get(zone.parent, gesture)
            ?.takeUnless { it.id == com.edgeflow.core.ActionId.NONE || it.id == com.edgeflow.core.ActionId.DEFAULT }
            ?: Action.None
    }

    fun with(zone: EdgeZone, gesture: Gesture, action: Action): GestureTable {
        val zoneMap = bindings[zone.id].orEmpty().toMutableMap()
        if (action.id == com.edgeflow.core.ActionId.NONE) zoneMap.remove(gesture.index)
        else zoneMap[gesture.index] = action
        val next = bindings.toMutableMap()
        if (zoneMap.isEmpty()) next.remove(zone.id) else next[zone.id] = zoneMap
        return copy(bindings = next)
    }

    fun isZoneEnabled(zone: EdgeZone): Boolean {
        if (zone.isFullEdge) {
            return EdgeZone.segmentsOf(zone).all(::isZoneEnabled)
        }
        return zone.id in enabledZones &&
            zone.parent.id !in disabledZones &&
            zone.id !in disabledZones
    }

    fun withZoneEnabled(zone: EdgeZone, enabled: Boolean): GestureTable {
        val affected = if (zone.isFullEdge) EdgeZone.segmentsOf(zone).map(EdgeZone::id)
            else listOf(zone.id)
        val nextZones = if (enabled) enabledZones + affected else enabledZones - affected.toSet()
        // Keep the old edge list readable by previous export tools without
        // making it authoritative for the input interceptor.
        val completeEdges = listOf(
            EdgeZone.LEFT, EdgeZone.RIGHT, EdgeZone.TOP, EdgeZone.BOTTOM,
        ).filter { parent ->
            EdgeZone.segmentsOf(parent).all { it.id in nextZones }
        }.map(EdgeZone::id).toSet()
        return copy(
            enabledZones = nextZones,
            enabledEdges = completeEdges,
            disabledZones = if (enabled) disabledZones - affected.toSet() - zone.parent.id
                else disabledZones - affected.toSet(),
        )
    }
}

/** Sparse key bindings: key id -> key-gesture index -> action. */
@Serializable
data class KeyTable(
    val bindings: Map<Int, Map<Int, Action>> = emptyMap(),
    val disabledKeys: Set<Int> = emptySet(),
    /** Keys must be deliberately opted in before the hook intercepts events. */
    val enabledKeys: Set<Int> = emptySet(),
) {
    fun resolve(key: HardwareKey, gesture: KeyGesture): Action =
        bindings[key.id]?.get(gesture.index) ?: Action.None

    fun with(key: HardwareKey, gesture: KeyGesture, action: Action): KeyTable {
        val keyMap = bindings[key.id].orEmpty().toMutableMap()
        if (action.id == com.edgeflow.core.ActionId.NONE) keyMap.remove(gesture.index)
        else keyMap[gesture.index] = action
        val next = bindings.toMutableMap()
        if (keyMap.isEmpty()) next.remove(key.id) else next[key.id] = keyMap
        return copy(bindings = next)
    }

    fun isKeyEnabled(key: HardwareKey): Boolean =
        key.id in enabledKeys && key.id !in disabledKeys

    fun withKeyEnabled(key: HardwareKey, enabled: Boolean): KeyTable =
        copy(
            enabledKeys = if (enabled) enabledKeys + key.id else enabledKeys - key.id,
            disabledKeys = disabledKeys - key.id,
        )
}

/**
 * Pie menu layout: four edges, each with two rings of items.
 *
 * The reference implementation exposes 6 slots per level, giving 12 per edge.
 * Level-1 items are the inner ring, level-2 the outer.
 */
@Serializable
data class PieConfig(
    val edges: Map<Int, PieEdge> = emptyMap(),
    val scalePercent: Int = 100,
    val circleColor: Long = 0xFF2B2B2B,
    val iconColor: Long = 0xFFFFFFFF,
) {
    companion object {
        const val LEVEL_1_SLOTS = 6
        const val LEVEL_2_SLOTS = 6
        const val SLOTS_PER_EDGE = LEVEL_1_SLOTS + LEVEL_2_SLOTS
    }

    fun edge(edge: Edge): PieEdge = edges[edge.index] ?: PieEdge()

    fun withEdge(edge: Edge, value: PieEdge): PieConfig =
        copy(edges = edges + (edge.index to value))
}

@Serializable
data class PieEdge(
    val level1: List<Action> = List(PieConfig.LEVEL_1_SLOTS) { Action.None },
    val level2: List<Action> = List(PieConfig.LEVEL_2_SLOTS) { Action.None },
) {
    fun slot(index: Int): Action = when {
        index < 0 -> Action.None
        index < PieConfig.LEVEL_1_SLOTS -> level1.getOrElse(index) { Action.None }
        index < PieConfig.SLOTS_PER_EDGE -> level2.getOrElse(index - PieConfig.LEVEL_1_SLOTS) { Action.None }
        else -> Action.None
    }

    fun withSlot(index: Int, action: Action): PieEdge = when {
        index < 0 -> this
        index < PieConfig.LEVEL_1_SLOTS -> copy(level1 = level1.replaceAt(index, action))
        index < PieConfig.SLOTS_PER_EDGE -> copy(level2 = level2.replaceAt(index - PieConfig.LEVEL_1_SLOTS, action))
        else -> this
    }

    private fun List<Action>.replaceAt(index: Int, action: Action): List<Action> =
        toMutableList().also { if (index < it.size) it[index] = action }
}

/** A grid of actions shown as a floating panel. */
@Serializable
data class PanelConfig(
    val id: Int,
    val name: String = "",
    val rows: Int = 3,
    val columns: Int = 3,
    val cells: List<Action> = emptyList(),
    val scalePercent: Int = 100,
    val backgroundColor: Long = 0xFF1F1F1F,
    val iconColor: Long = 0xFFFFFFFF,
) {
    fun cell(row: Int, column: Int): Action {
        val index = row * columns + column
        return cells.getOrElse(index) { Action.None }
    }

    fun withCell(row: Int, column: Int, action: Action): PanelConfig {
        val index = row * columns + column
        val next = cells.toMutableList()
        while (next.size <= index) next.add(Action.None)
        next[index] = action
        return copy(cells = next)
    }
}

/**
 * Shortcut panel layout. Each edge has one grid; the reference implementation
 * splits the edge into [EdgeSplits] segments and gives each a column of rows.
 */
@Serializable
data class ShortcutPanelConfig(
    val edges: Map<Int, PanelConfig> = emptyMap(),
)

/** Left and right side bars, each an ordered list of actions. */
@Serializable
data class SideBarConfig(
    val left: List<Action> = emptyList(),
    val right: List<Action> = emptyList(),
    val showText: Boolean = true,
) {
    fun items(side: Edge): List<Action> = if (side == Edge.LEFT) left else right

    fun with(side: Edge, actions: List<Action>): SideBarConfig =
        if (side == Edge.LEFT) copy(left = actions) else copy(right = actions)
}

/** A time- or day-based trigger. */
@Serializable
data class ScheduleEntry(
    val id: Int,
    val enabled: Boolean = true,
    val hour: Int = 0,
    val minute: Int = 0,
    /** Bit 0 is Monday. */
    val daysOfWeekMask: Int = 0x7F,
    val action: Action = Action.None,
    val label: String = "",
)

/** A named action, used by saved multi-actions and the action collection. */
@Serializable
data class NamedAction(
    val id: Int,
    val name: String = "",
    val action: Action = Action.None,
    val iconKey: String? = null,
    /** Backward-compatible tombstone for index-addressed saved action references. */
    val deleted: Boolean = false,
)

/**
 * App freezer. Frozen packages are disabled via the package manager, so the
 * list is persisted rather than derived: it is the record of what we disabled
 * and therefore what we are responsible for re-enabling.
 */
@Serializable
data class FreezerConfig(
    val frozen: Set<String> = emptySet(),
    val autoFreezeOnScreenOff: Boolean = false,
    val autoFreezeDelaySeconds: Int = 30,
)

/** Applies an unfreeze result without dropping packages whose operation failed. */
fun FreezerConfig.afterUnfreeze(packageName: String, succeeded: Boolean): FreezerConfig =
    if (succeeded) copy(frozen = frozen - packageName) else this

/** Global tunables. Distances are in inches so they behave identically across densities. */
@Serializable
data class AppSettings(
    /** Thickness of the left/right edges, in inches. */
    val edgeWidthInches: Float = 0.12f,
    /** Thickness of the top/bottom edges, in inches. */
    val edgeHeightInches: Float = 0.12f,
    /** Fraction of the edge reserved at each end, so corners stay usable. */
    val edgeCornerReservedInches: Float = 0.12f,
    /** Movement before a swipe is recognised, in inches. */
    val swipeThresholdInches: Float = 0.3f,

    val longPressTimeoutMillis: Long = 400,
    val doubleTapTimeoutMillis: Long = 300,
    val keyLongClickTimeoutMillis: Long = 300,
    val keyDoubleClickTimeoutMillis: Long = 300,

    val vibrateOnTrigger: Boolean = true,
    val triggerOnKeyguard: Boolean = false,
    val triggerOnSoftKeyboard: Boolean = false,

    val scrollSpeedPercent: Int = 100,
    val gesturePointerScalePercent: Int = 100,
    val gesturePointerColor: Long = 0xFF03A9F4,
    val showGesturePointer: Boolean = false,
    val gestureControlEnabled: Boolean = false,
    val keyControlEnabled: Boolean = false,
    val immersiveStatusBar: Boolean = false,
    val immersiveNavBar: Boolean = false,
    val keepScreenOn: Boolean = false,
    val screenFilterAlpha: Int = 0,
    val gameMode: Boolean = false,
    /** Original Game Mode's ignore-edge switches (L/R/T/B). */
    val gameIgnoreEdgesMask: Int = 0b1111,
    /** Original Game Mode's intercept-key switches (Back/Home/Recents/Menu/Vol+/Vol-). */
    val gameInterceptKeysMask: Int = 0,
    /** Allows other apps to trigger actions by broadcast; off by default. */
    val allowBroadcastTrigger: Boolean = false,
    val quickSettingsTileActionId: Int? = null,
    /** Up to twelve independent tiles. The old single-tile field is read as tile 1. */
    val quickSettingsTileActionIds: Map<Int, Int> = emptyMap(),
    /**
     * Null means a legacy install: leave all 12 existing Android components
     * alone, since an unbound tile might already be pinned in Quick Settings.
     */
    val quickSettingsTileCount: Int? = null,
    /** Opt-in movable automation handle; omitted from old configs by default. */
    val floatingAutomationBarEnabled: Boolean = false,
    /** Stable named-action IDs, never their mutable list indices. */
    val floatingAutomationBarActionIds: List<Int> = emptyList(),
    val floatingAutomationBarSizePercent: Int = 100,
    val floatingAutomationBarOpacityPercent: Int = 92,
    val floatingAutomationBarHideOnLockscreen: Boolean = true,
    val floatingAutomationBarHideInGameMode: Boolean = true,
    /** Legacy installs stay on the original list until explicitly switched. */
    val floatingAutomationBarDisplayMode: String = "list",
    /** New radial menu defaults: two rings with six sectors each. */
    val floatingAutomationBarRingCount: Int = 2,
    /** Zero-based ring * 6 + sector -> stable saved action ID. */
    val floatingAutomationBarRingActionIds: Map<Int, Int> = emptyMap(),
    val floatingAutomationBarRingHaptics: Boolean = true,
    val logToFile: Boolean = false,
) {
    /** New installs explicitly select two tiles. Legacy installs keep twelve. */
    fun effectiveTileCount(): Int = (quickSettingsTileCount ?: 12).coerceIn(0, 12)

    fun withTileCount(count: Int): AppSettings {
        require(count in 0..12)
        return copy(quickSettingsTileCount = count)
    }

    fun actionForTile(index: Int): Int? = when {
        index !in 1..12 -> null
        index in quickSettingsTileActionIds -> quickSettingsTileActionIds[index]
        index == 1 -> quickSettingsTileActionId
        else -> null
    }

    fun withTileAction(index: Int, actionId: Int?): AppSettings {
        require(index in 1..12) { "Quick Settings tile must be 1..12" }
        return copy(
            quickSettingsTileActionId = if (index == 1) null else quickSettingsTileActionId,
            quickSettingsTileActionIds = if (actionId == null) quickSettingsTileActionIds - index
                else quickSettingsTileActionIds + (index to actionId),
        )
    }
}
