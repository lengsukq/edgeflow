package com.edgeflow.core

/**
 * Edge zones. Index order matches the reference implementation's hit-testing
 * results and the on-disk gesture tables, so a config written by either side
 * is interpreted identically.
 */
enum class EdgeZone(val id: Int, val label: String) {
    LEFT_TOP(0, "Left top"),
    LEFT_CENTER(1, "Left center"),
    LEFT_BOTTOM(2, "Left bottom"),
    LEFT(3, "Left edge"),
    RIGHT_TOP(4, "Right top"),
    RIGHT_CENTER(5, "Right center"),
    RIGHT_BOTTOM(6, "Right bottom"),
    RIGHT(7, "Right edge"),
    TOP_LEFT(8, "Top left"),
    TOP_CENTER(9, "Top center"),
    TOP_RIGHT(10, "Top right"),
    TOP(11, "Top edge"),
    BOTTOM_LEFT(12, "Bottom left"),
    BOTTOM_CENTER(13, "Bottom center"),
    BOTTOM_RIGHT(14, "Bottom right"),
    BOTTOM(15, "Bottom edge");

    /** The full-length edge this sub-zone belongs to; used as the fallback target. */
    val parent: EdgeZone
        get() = when (this) {
            LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM -> LEFT
            RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM -> RIGHT
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> TOP
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> BOTTOM
            LEFT, RIGHT, TOP, BOTTOM -> this
        }

    val isFullEdge: Boolean get() = this == LEFT || this == RIGHT || this == TOP || this == BOTTOM

    companion object {
        private val byId = entries.associateBy(EdgeZone::id)

        fun of(id: Int): EdgeZone? = byId[id]

        /** Sub-zones of an edge, in visual order (before, center, after). */
        fun segmentsOf(edge: EdgeZone): List<EdgeZone> = when (edge) {
            LEFT -> listOf(LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM)
            RIGHT -> listOf(RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM)
            TOP -> listOf(TOP_LEFT, TOP_CENTER, TOP_RIGHT)
            BOTTOM -> listOf(BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT)
            else -> emptyList()
        }

        /** Maps any zone to its full-length parent, matching the reference `p.h`. */
        fun parentOf(id: Int): Int = of(id)?.parent?.id ?: -1
    }
}

/**
 * Gesture kinds recognised inside an edge zone. Indices match the reference
 * gesture table order (`d.c0`).
 */
enum class Gesture(val index: Int, val label: String) {
    TAP(0, "Tap"),
    DOUBLE_TAP(1, "Double tap"),
    LONG_PRESS(2, "Long press"),
    SWIPE_LEFT(3, "Swipe left"),
    SWIPE_RIGHT(4, "Swipe right"),
    SWIPE_UP(5, "Swipe up"),
    SWIPE_DOWN(6, "Swipe down");

    companion object {
        val COUNT = entries.size
        private val byIndex = entries.associateBy(Gesture::index)
        fun of(index: Int): Gesture? = byIndex[index]
    }
}

/** The four full-length edges, used by pie, panels and side bars. */
enum class Edge(val index: Int, val label: String) {
    LEFT(0, "Left"),
    RIGHT(1, "Right"),
    TOP(2, "Top"),
    BOTTOM(3, "Bottom");

    companion object {
        private val byIndex = entries.associateBy(Edge::index)
        fun of(index: Int): Edge? = byIndex[index]
    }
}

/** Hardware keys whose click / double-click / long-press can be remapped. */
enum class HardwareKey(val id: Int, val label: String) {
    BACK(0, "Back"),
    HOME(1, "Home"),
    RECENT_APPS(2, "Recent apps"),
    MENU(3, "Menu"),
    VOLUME_UP(4, "Volume up"),
    VOLUME_DOWN(5, "Volume down"),
    HEADSET_HOOK(6, "Headset hook");

    companion object {
        val COUNT = entries.size
        private val byId = entries.associateBy(HardwareKey::id)
        fun of(id: Int): HardwareKey? = byId[id]
    }
}

/** The three interaction kinds available on every hardware key. */
enum class KeyGesture(val index: Int, val label: String) {
    CLICK(0, "Click"),
    DOUBLE_CLICK(1, "Double click"),
    LONG_PRESS(2, "Long press");

    companion object {
        val COUNT = entries.size
        private val byIndex = entries.associateBy(KeyGesture::index)
        fun of(index: Int): KeyGesture? = byIndex[index]
    }
}

/**
 * Where an action fires from. Each trigger kind owns its own slice of the
 * configuration tree.
 */
enum class TriggerKind(val key: String, val label: String) {
    EDGE_GESTURE("gestures", "Gestures"),
    KEY("keys", "Keys"),
    PIE("pie", "Pie"),
    SHORTCUTS_PANEL("shortcuts_panel", "Shortcuts panel"),
    CUSTOM_PANEL("custom_panels", "Custom panels"),
    LEFT_SIDE_BAR("left_side_bar", "Left side bar"),
    RIGHT_SIDE_BAR("right_side_bar", "Right side bar"),
    TILE("tiles", "Quick settings tiles"),
    SCHEDULE("schedule", "Schedule"),
    APP_STATE("app_state", "App state"),
    STATE("more_triggers", "More triggers"),
    GESTURE_RECORD("gesture_records", "Gesture records"),
    SAVED_MULTI_ACTION("saved_multi_actions", "Saved multi-actions"),
    ACTION_COLLECTION("action_collection", "Action collection");
}
