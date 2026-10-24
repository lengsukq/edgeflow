package com.edgeflow.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app.
 *
 * Routes are declared as data objects rather than raw strings so navigation
 * arguments are typed and a renamed screen cannot silently break a link.
 * Argument-carrying routes expose a `route` pattern and a `create` helper, which
 * keeps the pattern and the builder next to each other.
 */
sealed interface Destination {

    /** The pattern used to register this destination. */
    val route: String

    /** Label shown in the bottom bar, or null for destinations that are not tabs. */
    val label: String?

    /** Icon shown in the bottom bar. */
    val icon: ImageVector?

    data object Home : Destination {
        override val route = "home"
        override val label = "Home"
        override val icon = Icons.Outlined.Dashboard
    }

    data object FloatingBar : Destination {
        override val route = "floating_bar"
        override val label = "Floating"
        override val icon = Icons.Outlined.Layers
    }

    data object Gestures : Destination {
        override val route = "gestures"
        override val label = "Gestures"
        override val icon = Icons.Outlined.Gesture
    }

    data object Keys : Destination {
        override val route = "keys"
        override val label = "Keys"
        override val icon = Icons.Outlined.Keyboard
    }

    data object Panels : Destination {
        override val route = "panels"
        override val label = "Panels"
        override val icon = Icons.Outlined.Layers
    }

    data object Settings : Destination {
        override val route = "settings"
        override val label = "Settings"
        override val icon = Icons.Outlined.Tune
    }

    /**
     * Action picker for a specific binding.
     *
     * The target is encoded as three integers rather than a serialised object:
     * gesture bindings, key bindings and panel slots are all addressable by
     * `(family, primary, secondary)`, which keeps the route stable and readable
     * in logs.
     */
    data object ActionPicker : Destination {
        override val route = "action_picker/{family}/{primary}/{secondary}?target={target}"
        override val label = null
        override val icon = null

        const val ARG_FAMILY = "family"
        const val ARG_PRIMARY = "primary"
        const val ARG_SECONDARY = "secondary"
        const val ARG_TARGET = "target"

        fun create(family: Int, primary: Int, secondary: Int, target: String? = null): String {
            val route = "action_picker/$family/$primary/$secondary"
            return if (target == null) route else "$route?${ARG_TARGET}=${Uri.encode(target)}"
        }
    }

    /** Detail editor for a state trigger family. */
    data object StateTriggers : Destination {
        override val route = "state_triggers"
        override val label = null
        override val icon = null
    }

    /** Detail editor for saved multi-actions. */
    data object SavedActions : Destination {
        override val route = "saved_actions"
        override val label = "Automations"
        override val icon = Icons.Outlined.List
    }

    /** About and diagnostics. */
    data object About : Destination {
        override val route = "about"
        override val label = null
        override val icon = null
    }

    companion object {
        /** Destinations shown as bottom-bar tabs, in order. */
        // Beta exposes four focused sections. Legacy gesture/key/panel routes
        // remain addressable as detail pages so older bindings are preserved.
        val tabs: List<Destination> = listOf(Home, SavedActions, FloatingBar, Settings)

        val all: List<Destination> = tabs + listOf(ActionPicker, StateTriggers, Gestures, Keys, Panels, About)
    }
}

/**
 * Which binding family an action picker is editing.
 *
 * Encoding this as an enum keeps the picker's write-back path exhaustive: adding
 * a family is a compile error until every branch handles it.
 */
enum class BindingFamily(val id: Int) {
    EDGE_GESTURE(0),
    HARDWARE_KEY(1),
    PIE_SLOT(2),
    SIDE_BAR(3),
    STATE_TRIGGER(4),
    APP_STATE(5),
    SAVED_ACTION(6),
    PANEL_CELL(7),
    SCHEDULE(8),
    SHORTCUT_PANEL_CELL(9);

    companion object {
        private val byId = entries.associateBy(BindingFamily::id)
        fun of(id: Int): BindingFamily? = byId[id]
    }
}
