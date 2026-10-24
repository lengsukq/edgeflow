package com.edgeflow.core

/**
 * Presentation metadata for every action.
 *
 * Kept separate from [com.edgeflow.core.model.Action] so the hook side can
 * serialise actions without pulling in UI concerns, while the app side can
 * render a picker without hard-coding strings per action type.
 */
data class ActionSpec(
    val id: Int,
    val title: String,
    val category: ActionCategory,
    val summary: String = "",
    /** True when the action needs extra parameters before it can run. */
    val requiresConfig: Boolean = false,
    /** True when the action only exists in a paid build. */
    val premium: Boolean = false,
)

enum class ActionCategory(val label: String) {
    BASIC("Basic"),
    SYSTEM("System"),
    SWITCHES("Switches"),
    APPS("Apps"),
    PANELS("Panels"),
    INPUT("Input"),
    FLOW("Flow"),
    FEEDBACK("Feedback"),
    MEDIA("Media"),
    POWER("Power"),
}

/**
 * The action catalogue.
 *
 * Ordering here is the order shown in the picker. Ids are the persisted
 * identifiers and must never be reassigned.
 */
object ActionCatalog {
    /**
     * Old IDs and JSON types remain intact for restoring historical backups.
     * Beta hides non-core features from NEW bindings; existing ones still
     * render with their original names until the user explicitly replaces them.
     */
    val hiddenInBeta: Set<Int> = setOf(
        ActionId.IMMERSIVE_STATUS_BAR, ActionId.IMMERSIVE_NAV_BAR,
        ActionId.FREEZER_DRAWER, ActionId.REFREEZE,
        ActionId.READ_CLIPBOARD, ActionId.CLIPBOARD, ActionId.UNIVERSAL_COPY,
        ActionId.SCREENSHOT_REGION, ActionId.PEN, ActionId.FLOATING_WIDGET,
        ActionId.APP_OPTION_MENU, ActionId.APPS_DRAWER, ActionId.RUNNING_APPS_DRAWER,
        ActionId.SHORTCUTS_PANEL, ActionId.CUSTOM_PANEL,
        ActionId.PIE, ActionId.SIDE_BAR,
    )

    fun betaSearch(query: String): List<ActionSpec> =
        search(query).filterNot { it.id in hiddenInBeta }

    val all: List<ActionSpec> = buildList {
        add(ActionSpec(ActionId.NONE, "No action", ActionCategory.BASIC))
        add(ActionSpec(ActionId.DEFAULT, "Default", ActionCategory.BASIC, "Fall back to the parent edge"))
        add(ActionSpec(ActionId.BACK, "Back", ActionCategory.BASIC))
        add(ActionSpec(ActionId.HOME, "Home", ActionCategory.BASIC))
        add(ActionSpec(ActionId.RECENT_APPS, "Recent apps", ActionCategory.BASIC))
        add(ActionSpec(ActionId.MENU, "Menu", ActionCategory.BASIC))
        add(ActionSpec(ActionId.LAST_APP, "Last app", ActionCategory.APPS))
        add(ActionSpec(ActionId.PREVIOUS_APP, "Previous app", ActionCategory.APPS))
        add(ActionSpec(ActionId.NEXT_APP, "Next app", ActionCategory.APPS))
        add(ActionSpec(ActionId.SLEEP_WAKE, "Sleep / wake", ActionCategory.POWER))
        add(ActionSpec(ActionId.POWER_MENU, "Power menu", ActionCategory.POWER))
        add(ActionSpec(ActionId.REBOOT, "Reboot", ActionCategory.POWER))
        add(ActionSpec(ActionId.SOFT_REBOOT, "Soft reboot", ActionCategory.POWER))
        add(ActionSpec(ActionId.SHUTDOWN, "Shut down", ActionCategory.POWER))

        add(ActionSpec(ActionId.LAUNCH_APP, "Launch app", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.LAUNCH_ACTIVITY, "Launch activity", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.ACTIVITY_FINISH, "Launch activity (legacy)", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.ACTIVITY_113, "Launch activity (alternate)", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.ACTIVITY_114, "Launch activity (alternate 2)", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.KILL_APP, "Kill app", ActionCategory.APPS, requiresConfig = true))
        add(ActionSpec(ActionId.KILL_FOREGROUND_APP, "Kill foreground app", ActionCategory.APPS))
        add(ActionSpec(ActionId.FINISH_ACTIVITY, "Finish activity", ActionCategory.APPS))
        add(ActionSpec(ActionId.CLEAR_BACKGROUND_APPS, "Kill background apps", ActionCategory.APPS))
        add(ActionSpec(ActionId.FREEZER_DRAWER, "Freezer drawer", ActionCategory.APPS))
        add(ActionSpec(ActionId.REFREEZE, "Refreeze frozen apps", ActionCategory.APPS))
        add(ActionSpec(ActionId.APPS_DRAWER, "Apps drawer", ActionCategory.APPS))
        add(ActionSpec(ActionId.RUNNING_APPS_DRAWER, "Running apps drawer", ActionCategory.APPS))

        add(ActionSpec(ActionId.SCREENSHOT_FULL, "Screenshot", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.SCREENSHOT_REGION, "Screenshot region", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.EXPAND_NOTIFICATIONS, "Expand notifications", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.EXPAND_QUICK_SETTINGS, "Expand quick settings", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.SPLIT_SCREEN, "Split screen", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.DISMISS_KEYGUARD, "Request Android unlock", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.ROTATE_SCREEN, "Rotate screen", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.FAST_SCROLL, "Fast scroll", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.UNIVERSAL_COPY, "Universal copy", ActionCategory.SYSTEM))
        add(ActionSpec(ActionId.GAME_MODE, "Game mode", ActionCategory.SYSTEM))

        add(ActionSpec(ActionId.WIFI, "Wi-Fi", ActionCategory.SWITCHES, premium = false))
        add(ActionSpec(ActionId.MOBILE_DATA, "Mobile data", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.BLUETOOTH, "Bluetooth", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.NFC, "NFC", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.LOCATION, "Location mode", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.SYNC, "Auto-sync", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.WIFI_AP, "Wi-Fi hotspot", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.AIRPLANE_MODE, "Airplane mode", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.AUTO_BRIGHTNESS, "Auto brightness", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.AUTO_ROTATION, "Auto rotation", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.TORCH, "Torch", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.SCREEN_FILTER, "Screen filter", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.KEEP_SCREEN_ON, "Stay awake", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.IMMERSIVE_STATUS_BAR, "Hide status bar", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.IMMERSIVE_NAV_BAR, "Hide navigation bar", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.GESTURE_CONTROL, "Gesture control", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.KEY_CONTROL, "Key control", ActionCategory.SWITCHES))
        add(ActionSpec(ActionId.GESTURE_RECORD, "Gesture recording", ActionCategory.SWITCHES))

        add(ActionSpec(ActionId.PIE, "Pie", ActionCategory.PANELS, requiresConfig = true))
        add(ActionSpec(ActionId.SIDE_BAR, "Side bar", ActionCategory.PANELS, requiresConfig = true))
        add(ActionSpec(ActionId.SHORTCUTS_PANEL, "Shortcuts panel", ActionCategory.PANELS, requiresConfig = true))
        add(ActionSpec(ActionId.CUSTOM_PANEL, "Custom panel", ActionCategory.PANELS, requiresConfig = true))
        add(ActionSpec(ActionId.FLOATING_WIDGET, "Floating widget", ActionCategory.PANELS, requiresConfig = true))
        add(ActionSpec(ActionId.PEN, "Pen", ActionCategory.PANELS))
        add(ActionSpec(ActionId.GESTURE_POINTER, "Gesture pointer", ActionCategory.PANELS))

        add(ActionSpec(ActionId.INJECT_KEY_EVENT, "Inject key event", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.INJECT_KEY_EVENT_ALT, "Inject key event (alternate)", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.INJECT_GESTURE, "Inject gesture", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.INJECT_TEXT, "Input text", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.INJECT_TEXT_ALT, "Input text (alternate)", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.SOFT_KEYBOARD, "Soft keyboard", ActionCategory.INPUT))
        add(ActionSpec(ActionId.READ_CLIPBOARD, "Read clipboard text", ActionCategory.INPUT))
        add(ActionSpec(ActionId.CLIPBOARD, "Clipboard", ActionCategory.INPUT))
        add(ActionSpec(ActionId.EDIT_TEXT, "Edit text", ActionCategory.INPUT, "Cut, copy, paste, select all, undo, or redo", requiresConfig = true))
        add(ActionSpec(ActionId.ADJUST, "Adjust", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.SLIDE_ADJUST, "Slide adjust", ActionCategory.INPUT, requiresConfig = true))
        add(ActionSpec(ActionId.SUCCESSIVE_ADJUST, "Successive adjust", ActionCategory.INPUT, requiresConfig = true))

        add(ActionSpec(ActionId.MULTI_ACTION, "Multi-action", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.SAVED_MULTI_ACTION, "Saved multi-action", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.SAVED_MULTI_ACTION_ALT, "Saved multi-action (alternate)", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.CONDITIONAL, "Conditional", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.REPEAT, "Repeat", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.DELAY, "Delay", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.ABORT, "Abort", ActionCategory.FLOW, premium = true))
        add(ActionSpec(ActionId.STOP_ALL, "Stop all", ActionCategory.FLOW))
        add(ActionSpec(ActionId.SET_VARIABLE, "Set variable", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.SUB_GESTURES, "Sub gestures", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.SHELL_SU, "Root shell command", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.SHELL, "Shell command", ActionCategory.FLOW, requiresConfig = true, premium = true))
        add(ActionSpec(ActionId.EDITABLE_ACTION, "Select and edit", ActionCategory.FLOW, requiresConfig = true, premium = true))

        add(ActionSpec(ActionId.MEDIA_PLAY_PAUSE, "Play / pause", ActionCategory.MEDIA))
        add(ActionSpec(ActionId.MEDIA_STOP, "Stop", ActionCategory.MEDIA))
        add(ActionSpec(ActionId.MEDIA_PREVIOUS, "Previous track", ActionCategory.MEDIA))
        add(ActionSpec(ActionId.MEDIA_NEXT, "Next track", ActionCategory.MEDIA))
        add(ActionSpec(ActionId.MEDIA_CONTROL, "Media control", ActionCategory.MEDIA, requiresConfig = true))
        add(ActionSpec(ActionId.ADJUST_VOLUME, "Adjust volume", ActionCategory.MEDIA, requiresConfig = true))
        add(ActionSpec(ActionId.RINGER_MODE, "Ringer mode", ActionCategory.MEDIA, requiresConfig = true))
        add(ActionSpec(ActionId.BRIGHTNESS_UP, "Brightness up", ActionCategory.MEDIA))
        add(ActionSpec(ActionId.BRIGHTNESS_DOWN, "Brightness down", ActionCategory.MEDIA))

        add(ActionSpec(ActionId.TOAST, "Toast", ActionCategory.FEEDBACK, requiresConfig = true))
        add(ActionSpec(ActionId.NOTIFY, "Notification", ActionCategory.FEEDBACK, requiresConfig = true))
        add(ActionSpec(ActionId.SPEECH, "Speech", ActionCategory.FEEDBACK, requiresConfig = true))
        add(ActionSpec(ActionId.VIBRATE, "Vibrate", ActionCategory.FEEDBACK, requiresConfig = true))
        add(ActionSpec(ActionId.PLAY_SOUND, "Play sound", ActionCategory.FEEDBACK, requiresConfig = true))
        add(ActionSpec(ActionId.APP_OPTION_MENU, "App option menu", ActionCategory.BASIC))
    }

    private val byId = all.associateBy(ActionSpec::id)

    fun spec(id: Int): ActionSpec? = byId[id]

    fun title(id: Int): String = byId[id]?.title ?: "Action $id"

    fun byCategory(category: ActionCategory): List<ActionSpec> = all.filter { it.category == category }

    fun search(query: String): List<ActionSpec> {
        if (query.isBlank()) return all
        val needle = query.trim().lowercase()
        return all.filter {
            it.title.lowercase().contains(needle) || it.summary.lowercase().contains(needle)
        }
    }

    /**
     * Human-readable rendering of an action, including its parameters, for
     * list rows and the log.
     */
    fun describe(action: com.edgeflow.core.model.Action): String = when (action) {
        is com.edgeflow.core.model.Action.Simple -> title(action.id)
        is com.edgeflow.core.model.Action.Toggle -> {
            val base = title(action.id)
            when (action.mode) {
                com.edgeflow.core.model.ToggleMode.TOGGLE -> base
                com.edgeflow.core.model.ToggleMode.ON -> "$base on"
                com.edgeflow.core.model.ToggleMode.OFF -> "$base off"
            }
        }
        is com.edgeflow.core.model.Action.LaunchApp -> "Launch ${action.packageName}${if (action.root) " (root)" else ""}"
        is com.edgeflow.core.model.Action.LaunchActivity -> "Activity ${action.className ?: action.packageName}"
        is com.edgeflow.core.model.Action.Shell -> "${if (action.asRoot) "Root shell" else "Shell"}: ${action.command}"
        is com.edgeflow.core.model.Action.Delay -> "Delay ${action.millis} ms"
        is com.edgeflow.core.model.Action.InputText -> "Input \"${action.text}\""
        is com.edgeflow.core.model.Action.EditText -> "Edit text: ${action.command.label}"
        is com.edgeflow.core.model.Action.InjectKey -> "Key ${action.keyCode}"
        is com.edgeflow.core.model.Action.InjectTap -> "Tap (${action.x}, ${action.y})"
        is com.edgeflow.core.model.Action.ReplayGesture -> "Replay gesture ${action.recordingId}"
        is com.edgeflow.core.model.Action.Toast -> "Toast: ${action.text}"
        is com.edgeflow.core.model.Action.Notify -> "Notify: ${action.title}"
        is com.edgeflow.core.model.Action.Speech -> "Say: ${action.text}"
        is com.edgeflow.core.model.Action.PlaySound -> "Play sound"
        is com.edgeflow.core.model.Action.Vibrate -> "Vibrate ${action.millis} ms"
        is com.edgeflow.core.model.Action.RingerMode -> "Ringer ${action.mode.name.lowercase()}"
        is com.edgeflow.core.model.Action.Adjust -> "${title(action.id)} ${action.delta} ${action.stream.name.lowercase()} steps"
        is com.edgeflow.core.model.Action.MediaControl -> "${title(action.id)}: ${action.command.label}"
        is com.edgeflow.core.model.Action.SuccessiveAdjust -> {
            val direction = when (action.target) {
                com.edgeflow.core.model.SuccessiveAdjustTarget.CARET -> if (action.increase) "left" else "right"
                com.edgeflow.core.model.SuccessiveAdjustTarget.SCROLL -> if (action.increase) "up" else "down"
                else -> if (action.increase) "up" else "down"
            }
            val target = if (action.target == com.edgeflow.core.model.SuccessiveAdjustTarget.VOLUME) {
                "${action.stream.name.lowercase()} volume"
            } else {
                action.target.label
            }
            "$target $direction"
        }
        is com.edgeflow.core.model.Action.SlideAdjust -> {
            val target = if (action.target == com.edgeflow.core.model.SlideAdjustTarget.VOLUME) {
                "${action.stream.name.lowercase()} volume"
            } else {
                action.target.label
            }
            "Slide adjust: $target"
        }
        is com.edgeflow.core.model.Action.FloatingWidget -> {
            val size = if (action.widthDp > 0 || action.heightDp > 0) {
                " · ${action.widthDp}×${action.heightDp}dp"
            } else ""
            val timeout = action.timeoutSeconds.takeIf { it > 0 }?.let { " · ${it}s" } ?: ""
            "Floating widget #${action.widgetId}$size$timeout"
        }
        is com.edgeflow.core.model.Action.ShowPie -> "Pie (${Edge.of(action.edge)?.label ?: action.edge})"
        is com.edgeflow.core.model.Action.ShowSideBar -> "Side bar (${Edge.of(action.edge)?.label ?: action.edge})"
        is com.edgeflow.core.model.Action.ShowPanel -> "Panel ${action.panelIndex + 1}"
        is com.edgeflow.core.model.Action.ShowShortcutPanel -> "Shortcuts (${Edge.of(action.edge)?.label ?: action.edge})"
        is com.edgeflow.core.model.Action.SavedMultiAction -> "Saved action ${action.slot + 1}"
        is com.edgeflow.core.model.Action.KillApp -> "Kill ${action.packageName}"
        is com.edgeflow.core.model.Action.SetVariable -> "${action.name} ${action.operator.name.lowercase()} ${action.value}"
        is com.edgeflow.core.model.Action.Sequence -> "Multi-action (${action.actions.size} steps)"
        is com.edgeflow.core.model.Action.Conditional -> "If … then … else …"
        is com.edgeflow.core.model.Action.EditableAction ->
            action.customLabel?.takeIf(String::isNotBlank) ?: describe(action.innerAction)
        is com.edgeflow.core.model.Action.Repeat -> "Repeat ${if (action.times == 0) "∞" else action.times}× (${action.actions.size} steps)"
        is com.edgeflow.core.model.Action.Abort -> "Abort"
        is com.edgeflow.core.model.Action.StopAll -> "Stop all"
        is com.edgeflow.core.model.Action.SubGestures -> "Sub gestures"
        is com.edgeflow.core.model.Action.Special -> action.action ?: "Special"
    }
}
