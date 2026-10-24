package com.edgeflow.core.model

import com.edgeflow.core.ActionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single thing a trigger can do.
 *
 * ## Shape
 *
 * Actions form a tree: [Sequence] holds children, so multi-actions, conditionals,
 * repeats and sub-gesture groups all nest naturally with no special cases. An
 * `Action` may therefore contain an `Action` at any depth.
 *
 * ## Parameter order
 *
 * Every variant takes its payload first and its [id] last, because the payload is
 * the only thing a caller usually has to supply. That makes the common
 * constructions read as `Action.Sequence(steps)` and
 * `Action.LaunchApp("com.example")`, with the id defaulting to the right value.
 * An id-first order would force a named argument at every call site.
 *
 * ## Persistence
 *
 * [id] is written to disk as the discriminator for the polymorphic serialiser and
 * must never be reassigned once released.
 */
@Serializable
sealed interface Action {

    /** Stable identifier; the discriminator used by the polymorphic serialiser. */
    val id: Int

    /** An action with no parameters. */
    @Serializable
    @SerialName("simple")
    data class Simple(override val id: Int) : Action

    /**
     * A toggleable system switch. [mode] selects toggle / turn-on / turn-off,
     * matching the reference implementation's three-state switch family.
     */
    @Serializable
    @SerialName("toggle")
    data class Toggle(val mode: ToggleMode = ToggleMode.TOGGLE, override val id: Int) : Action

    /** Launch an application by package name. [root] requests a `su`-based fallback. */
    @Serializable
    @SerialName("app")
    data class LaunchApp(
        val packageName: String,
        val root: Boolean = false,
        override val id: Int = ActionId.LAUNCH_APP,
    ) : Action

    /** Launch a component; [data] optionally carries an intent URI. */
    @Serializable
    @SerialName("activity")
    data class LaunchActivity(
        val packageName: String,
        val className: String? = null,
        val data: String? = null,
        override val id: Int = ActionId.LAUNCH_ACTIVITY,
    ) : Action

    /** A shell command. [asRoot] routes it through `su`. */
    @Serializable
    @SerialName("shell")
    data class Shell(
        val command: String,
        val asRoot: Boolean = false,
        override val id: Int = ActionId.SHELL,
    ) : Action

    /** Pause the current action sequence. */
    @Serializable
    @SerialName("delay")
    data class Delay(val millis: Long, override val id: Int = ActionId.DELAY) : Action

    /** Type text into the focused field. */
    @Serializable
    @SerialName("text")
    data class InputText(val text: String, override val id: Int = ActionId.INJECT_TEXT) : Action

    /** Send a standard editing shortcut to the focused text field. */
    @Serializable
    @SerialName("edit_text")
    data class EditText(
        val command: EditTextCommand,
        override val id: Int = ActionId.EDIT_TEXT,
    ) : Action

    /** Synthesise a key press. */
    @Serializable
    @SerialName("key")
    data class InjectKey(
        val keyCode: Int,
        val metaState: Int = 0,
        override val id: Int = ActionId.INJECT_KEY_EVENT,
    ) : Action

    /** Synthesise a tap at screen coordinates. */
    @Serializable
    @SerialName("tap")
    data class InjectTap(val x: Int, val y: Int, override val id: Int = ActionId.INJECT_GESTURE) : Action

    /** Replay a user-recorded trace through the native input bridge. */
    @Serializable
    @SerialName("replay_gesture")
    data class ReplayGesture(
        val recordingId: String,
        override val id: Int = ActionId.INJECT_GESTURE,
        /** 100 = original speed. Range 25..300, persisted with legacy-safe default. */
        val speedPercent: Int = 100,
        val placement: GesturePlacement = GesturePlacement.ORIGINAL,
        val anchorXPercent: Int = 50,
        val anchorYPercent: Int = 50,
    ) : Action

    /** Show a transient message. */
    @Serializable
    @SerialName("toast")
    data class Toast(val text: String, override val id: Int = ActionId.TOAST) : Action

    /** Post a notification. */
    @Serializable
    @SerialName("notify")
    data class Notify(
        val title: String,
        val text: String,
        override val id: Int = ActionId.NOTIFY,
    ) : Action

    /** Text-to-speech. */
    @Serializable
    @SerialName("speech")
    data class Speech(val text: String, override val id: Int = ActionId.SPEECH) : Action

    /** Play a sound; [uri] defaults to the notification tone. */
    @Serializable
    @SerialName("sound")
    data class PlaySound(val uri: String? = null, override val id: Int = ActionId.PLAY_SOUND) : Action

    /** Vibrate for [millis]. */
    @Serializable
    @SerialName("vibrate")
    data class Vibrate(val millis: Long = 50, override val id: Int = ActionId.VIBRATE) : Action

    /** Set a ringer mode. */
    @Serializable
    @SerialName("ringer")
    data class RingerMode(
        val mode: RingerModeValue,
        override val id: Int = ActionId.RINGER_MODE,
    ) : Action

    /** Adjust a continuous value such as a volume stream. */
    @Serializable
    @SerialName("adjust")
    data class Adjust(
        val stream: AdjustStream = AdjustStream.MUSIC,
        val delta: Int = 0,
        override val id: Int = ActionId.ADJUST_VOLUME,
    ) : Action

    /** Send one selected playback command to the active media session. */
    @Serializable
    @SerialName("media_control")
    data class MediaControl(
        val command: MediaCommand,
        override val id: Int = ActionId.MEDIA_CONTROL,
    ) : Action

    /** Apply one configured step to brightness, volume, scrolling, or caret position. */
    @Serializable
    @SerialName("successive_adjust")
    data class SuccessiveAdjust(
        val target: SuccessiveAdjustTarget,
        val increase: Boolean = true,
        val stream: AdjustStream = AdjustStream.MUSIC,
        override val id: Int = ActionId.SUCCESSIVE_ADJUST,
    ) : Action

    /** Adjust a configured target continuously while a bound swipe remains active. */
    @Serializable
    @SerialName("slide_adjust")
    data class SlideAdjust(
        val target: SlideAdjustTarget,
        val stream: AdjustStream = AdjustStream.MUSIC,
        override val id: Int = ActionId.SLIDE_ADJUST,
    ) : Action

    /** Show one app widget hosted by EdgeFlow. [widgetId] is allocated by host id 1. */
    @Serializable
    @SerialName("floating_widget")
    data class FloatingWidget(
        val widgetId: Int,
        override val id: Int = ActionId.FLOATING_WIDGET,
        /** 0 uses the provider's preferred dimensions. */
        val widthDp: Int = 0,
        val heightDp: Int = 0,
        /** Percentage of free on-screen travel space, not raw screen coordinates. */
        val initialXPercent: Int = 50,
        val initialYPercent: Int = 20,
        /** 0 disables automatic close. */
        val timeoutSeconds: Int = 0,
        val rememberLastPosition: Boolean = true,
    ) : Action {
        companion object {
            /** Must match the host ID used by Android's AppWidgetHost in the app and overlay service. */
            const val HOST_ID = 1
        }
    }

    /** Show a pie menu on the given edge. */
    @Serializable
    @SerialName("pie")
    data class ShowPie(val edge: Int, override val id: Int = ActionId.PIE) : Action

    /** Show a side bar on the given edge. */
    @Serializable
    @SerialName("sidebar")
    data class ShowSideBar(val edge: Int, override val id: Int = ActionId.SIDE_BAR) : Action

    /** Show a custom panel identified by its index. */
    @Serializable
    @SerialName("panel")
    data class ShowPanel(val panelIndex: Int, override val id: Int = ActionId.CUSTOM_PANEL) : Action

    /** Show the shortcut panel on a selected edge. */
    @Serializable
    @SerialName("shortcut_panel")
    data class ShowShortcutPanel(val edge: Int, override val id: Int = ActionId.SHORTCUTS_PANEL) : Action

    /** Run a stored multi-action. */
    @Serializable
    @SerialName("saved")
    data class SavedMultiAction(
        val slot: Int,
        override val id: Int = ActionId.SAVED_MULTI_ACTION,
    ) : Action

    /** Kill a specific package. */
    @Serializable
    @SerialName("kill")
    data class KillApp(val packageName: String, override val id: Int = ActionId.KILL_APP) : Action

    /** Assign a workflow variable. */
    @Serializable
    @SerialName("setvar")
    data class SetVariable(
        val name: String,
        val value: String,
        val operator: VariableOperator = VariableOperator.ASSIGN,
        override val id: Int = ActionId.SET_VARIABLE,
    ) : Action

    /** Run [actions] in order. */
    @Serializable
    @SerialName("sequence")
    data class Sequence(
        val actions: List<Action> = emptyList(),
        override val id: Int = ActionId.MULTI_ACTION,
    ) : Action

    /** Pick a branch at run time. */
    @Serializable
    @SerialName("conditional")
    data class Conditional(
        val condition: Condition = Condition.Unknown(),
        val thenAction: Action = Simple(ActionId.NONE),
        val elseAction: Action = Simple(ActionId.NONE),
        override val id: Int = ActionId.CONDITIONAL,
    ) : Action

    /** Run [actions] [times] times, or forever when [times] is zero. */
    @Serializable
    @SerialName("repeat")
    data class Repeat(
        val actions: List<Action> = emptyList(),
        val times: Int = 1,
        override val id: Int = ActionId.REPEAT,
    ) : Action

    /** Run a configured action, optionally presenting it under a custom label. */
    @Serializable
    @SerialName("editable_action")
    data class EditableAction(
        val innerAction: Action,
        val customLabel: String? = null,
        override val id: Int = ActionId.EDITABLE_ACTION,
    ) : Action

    /** Leave the current sequence early. */
    @Serializable
    @SerialName("abort")
    data class Abort(override val id: Int = ActionId.ABORT) : Action

    /** Stop every running sequence. */
    @Serializable
    @SerialName("stopall")
    data class StopAll(override val id: Int = ActionId.STOP_ALL) : Action

    /** A gesture that resolves only once a second gesture is performed inside it. */
    @Serializable
    @SerialName("subgestures")
    data class SubGestures(
        val hold: Action = Simple(ActionId.NONE),
        val swipeLeft: Action = Simple(ActionId.NONE),
        val swipeRight: Action = Simple(ActionId.NONE),
        val swipeUp: Action = Simple(ActionId.NONE),
        val swipeDown: Action = Simple(ActionId.NONE),
        override val id: Int = ActionId.SUB_GESTURES,
    ) : Action {
        /** @param gestureIndex one of the `Gesture` swipe constants */
        fun actionFor(gestureIndex: Int): Action = when (gestureIndex) {
            0 -> hold
            3 -> swipeLeft
            4 -> swipeRight
            5 -> swipeUp
            6 -> swipeDown
            else -> Simple(ActionId.NONE)
        }
    }

    /** Broadcast- or activity-triggered action, matching the reference `s0` type. */
    @Serializable
    @SerialName("special")
    data class Special(
        val packageName: String? = null,
        val action: String? = null,
        val extras: Map<String, String> = emptyMap(),
        override val id: Int = ActionId.SPECIAL,
    ) : Action

    companion object {
        /** Does nothing, and is what an unbound trigger resolves to. */
        val None: Action = Simple(ActionId.NONE)

        /** Falls back to whatever the parent edge has bound. */
        val Default: Action = Simple(ActionId.DEFAULT)

        val Back: Action = Simple(ActionId.BACK)
        val Home: Action = Simple(ActionId.HOME)
        val Recents: Action = Simple(ActionId.RECENT_APPS)
        val Menu: Action = Simple(ActionId.MENU)
        val SleepWake: Action = Simple(ActionId.SLEEP_WAKE)
        val MediaPlayPause: Action = Simple(ActionId.MEDIA_PLAY_PAUSE)
        val MediaStop: Action = Simple(ActionId.MEDIA_STOP)
        val MediaPrevious: Action = Simple(ActionId.MEDIA_PREVIOUS)
        val MediaNext: Action = Simple(ActionId.MEDIA_NEXT)
        val BrightnessUp: Action = Simple(ActionId.BRIGHTNESS_UP)
        val BrightnessDown: Action = Simple(ActionId.BRIGHTNESS_DOWN)
        val ExpandNotifications: Action = Simple(ActionId.EXPAND_NOTIFICATIONS)
        val ExpandQuickSettings: Action = Simple(ActionId.EXPAND_QUICK_SETTINGS)
        val Screenshot: Action = Simple(ActionId.SCREENSHOT_FULL)
        val ScreenshotRegion: Action = Simple(ActionId.SCREENSHOT_REGION)
        val LastApp: Action = Simple(ActionId.LAST_APP)
        val PreviousApp: Action = Simple(ActionId.PREVIOUS_APP)
        val NextApp: Action = Simple(ActionId.NEXT_APP)
        val KillForegroundApp: Action = Simple(ActionId.KILL_FOREGROUND_APP)
        val ClearBackgroundApps: Action = Simple(ActionId.CLEAR_BACKGROUND_APPS)
        val PowerMenu: Action = Simple(ActionId.POWER_MENU)
        val Reboot: Action = Simple(ActionId.REBOOT)
        val SoftReboot: Action = Simple(ActionId.SOFT_REBOOT)
        val Shutdown: Action = Simple(ActionId.SHUTDOWN)
        val RotateScreen: Action = Simple(ActionId.ROTATE_SCREEN)
        val DismissKeyguard: Action = Simple(ActionId.DISMISS_KEYGUARD)
        val SoftKeyboard: Action = Simple(ActionId.SOFT_KEYBOARD)
        val FastScroll: Action = Simple(ActionId.FAST_SCROLL)
        val ReadClipboard: Action = Simple(ActionId.READ_CLIPBOARD)
        val Clipboard: Action = Simple(ActionId.CLIPBOARD)
        val UniversalCopy: Action = Simple(ActionId.UNIVERSAL_COPY)
        val SplitScreen: Action = Simple(ActionId.SPLIT_SCREEN)
        val Pen: Action = Simple(ActionId.PEN)
        val StopAll: Action = Simple(ActionId.STOP_ALL)
        val Abort: Action = Simple(ActionId.ABORT)
        val GameMode: Action = Simple(ActionId.GAME_MODE)
        val AppsDrawer: Action = Simple(ActionId.APPS_DRAWER)
        val RunningAppsDrawer: Action = Simple(ActionId.RUNNING_APPS_DRAWER)
        val FreezerDrawer: Action = Simple(ActionId.FREEZER_DRAWER)
        val Refreeze: Action = Simple(ActionId.REFREEZE)
        val ShortcutsPanel: Action = Simple(ActionId.SHORTCUTS_PANEL)
        val Pie: Action = Simple(ActionId.PIE)
        val SideBar: Action = Simple(ActionId.SIDE_BAR)
        val FloatingWidget: Action = Simple(ActionId.FLOATING_WIDGET)
        val AppOptionMenu: Action = Simple(ActionId.APP_OPTION_MENU)
        val GesturePointer: Action = Simple(ActionId.GESTURE_POINTER)
        val EditText: Action = Simple(ActionId.EDIT_TEXT)
        val MediaControl: Action = Simple(ActionId.MEDIA_CONTROL)
        val AdjustAction: Action = Simple(ActionId.ADJUST)
        val SlideAdjust: Action = Simple(ActionId.SLIDE_ADJUST)

        /** Convenience for the many three-state switches. */
        fun toggle(id: Int, mode: ToggleMode = ToggleMode.TOGGLE): Action = Toggle(mode, id)
    }
}

enum class ToggleMode { TOGGLE, ON, OFF }

enum class RingerModeValue { SILENT, VIBRATE, NORMAL }

@Serializable
enum class AdjustStream {
    DEFAULT, VOICE_CALL, SYSTEM, RING, MUSIC, ALARM, NOTIFICATION, BLUETOOTH_SCO, SYSTEM_ENFORCED, DTMF, TTS
}

@Serializable
enum class SuccessiveAdjustTarget(val label: String) {
    SCREEN_FILTER_ALPHA("Screen filter"),
    BRIGHTNESS("Brightness"),
    VOLUME("Volume"),
    SCROLL("Scroll"),
    CARET("Caret"),
}

@Serializable
enum class SlideAdjustTarget(val label: String) {
    SCREEN_FILTER_ALPHA("Screen filter"),
    BRIGHTNESS("Brightness"),
    VOLUME("Volume"),
    CARET("Caret"),
}

@Serializable
enum class MediaCommand(val label: String) {
    PLAY("Play"),
    PLAY_PAUSE("Play / pause"),
    STOP("Stop"),
    PREVIOUS("Previous track"),
    NEXT("Next track"),
}

@Serializable
enum class EditTextCommand(val label: String) {
    CUT("Cut"),
    COPY("Copy"),
    PASTE("Paste"),
    SELECT_ALL("Select all"),
    UNDO("Undo"),
    REDO("Redo"),
}

enum class VariableOperator { ASSIGN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO }

/** Relational operators used by [Condition.Variable]. */
@Serializable
enum class ConditionOperator {
    /** Kept as ASSIGN on disk so existing condition JSON remains readable. */
    @SerialName("ASSIGN")
    EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    NOT_EQUAL,

    // Preserve decoding and re-encoding of documents written with the old
    // (incorrectly shared) arithmetic operator enum. These have no comparison
    // meaning and are rejected by the evaluator rather than guessed.
    @SerialName("ADD") LEGACY_ADD,
    @SerialName("SUBTRACT") LEGACY_SUBTRACT,
    @SerialName("MULTIPLY") LEGACY_MULTIPLY,
    @SerialName("DIVIDE") LEGACY_DIVIDE,
    @SerialName("MODULO") LEGACY_MODULO,
}

private fun VariableOperator.toConditionOperator(): ConditionOperator = when (this) {
    VariableOperator.ASSIGN -> ConditionOperator.EQUAL
    VariableOperator.ADD -> ConditionOperator.LEGACY_ADD
    VariableOperator.SUBTRACT -> ConditionOperator.LEGACY_SUBTRACT
    VariableOperator.MULTIPLY -> ConditionOperator.LEGACY_MULTIPLY
    VariableOperator.DIVIDE -> ConditionOperator.LEGACY_DIVIDE
    VariableOperator.MODULO -> ConditionOperator.LEGACY_MODULO
}

/**
 * A run-time predicate.
 *
 * Composites ([And], [Or], [Not]) nest, so arbitrary boolean expressions are
 * representable. The evaluator treats anything that cannot be evaluated as
 * unavailable and fails closed at the action boundary; negation preserves that
 * unavailable state rather than turning it into a match.
 *
 * As with [Action], the payload comes first and [id] last.
 */
@Serializable
sealed interface Condition {

    val id: Int

    /** A condition with no parameters; [id] alone determines its meaning. */
    @Serializable
    @SerialName("flag")
    data class Flag(override val id: Int) : Condition

    /**
     * A condition that cannot be evaluated, and the default before the user has
     * chosen one. Named `Unknown` rather than `Invalid` so the type does not
     * shadow the companion's `Invalid` value.
     */
    @Serializable
    @SerialName("invalid")
    data class Unknown(override val id: Int = com.edgeflow.core.ConditionId.INVALID) : Condition

    @Serializable
    @SerialName("and")
    data class And(
        val items: List<Condition> = emptyList(),
        override val id: Int = com.edgeflow.core.ConditionId.AND,
    ) : Condition

    @Serializable
    @SerialName("or")
    data class Or(
        val items: List<Condition> = emptyList(),
        override val id: Int = com.edgeflow.core.ConditionId.OR,
    ) : Condition

    @Serializable
    @SerialName("not")
    data class Not(
        val item: Condition = Unknown(),
        override val id: Int = com.edgeflow.core.ConditionId.NOT,
    ) : Condition

    @Serializable
    @SerialName("battery")
    data class BatteryLevel(
        val min: Int = 0,
        val max: Int = 100,
        override val id: Int = com.edgeflow.core.ConditionId.BATTERY_LEVEL,
    ) : Condition

    @Serializable
    @SerialName("time")
    data class TimePeriod(
        val startMinutes: Int = 0,
        val endMinutes: Int = 0,
        override val id: Int = com.edgeflow.core.ConditionId.TIME_PERIOD,
    ) : Condition

    @Serializable
    @SerialName("days")
    data class DaysOfWeek(
        /** Bit 0 is Monday, matching `java.util.Calendar`. */
        val mask: Int = 0x7F,
        override val id: Int = com.edgeflow.core.ConditionId.DAYS_OF_WEEK,
    ) : Condition

    @Serializable
    @SerialName("days_of_month")
    data class DaysOfMonth(
        /** Bit 0 is day 1, through bit 30 for day 31. */
        val mask: Int = 0x7FFFFFFF,
        override val id: Int = com.edgeflow.core.ConditionId.DAYS_OF_MONTH,
    ) : Condition

    @Serializable
    @SerialName("months")
    data class Months(
        /** Bit 0 is January, through bit 11 for December. */
        val mask: Int = 0x0FFF,
        override val id: Int = com.edgeflow.core.ConditionId.MONTHS,
    ) : Condition

    @Serializable
    @SerialName("focused")
    data class AppFocused(
        val packageName: String,
        override val id: Int = com.edgeflow.core.ConditionId.APP_FOCUSED,
    ) : Condition

    @Serializable
    @SerialName("wifi_ssid")
    data class WifiSsid(
        val ssid: String,
        override val id: Int = com.edgeflow.core.ConditionId.WIFI_SSID,
    ) : Condition

    @Serializable
    @SerialName("orientation")
    data class Orientation(
        val landscape: Boolean = false,
        override val id: Int = com.edgeflow.core.ConditionId.ORIENTATION,
    ) : Condition

    @Serializable
    @SerialName("variable")
    data class Variable(
        val name: String,
        val value: String = "",
        val operator: ConditionOperator = ConditionOperator.EQUAL,
        override val id: Int = com.edgeflow.core.ConditionId.VARIABLE,
    ) : Condition {
        /** Source-compatible bridge for conditions built with the old shared enum. */
        constructor(
            name: String,
            value: String = "",
            operator: VariableOperator,
            id: Int = com.edgeflow.core.ConditionId.VARIABLE,
        ) : this(name, value, operator.toConditionOperator(), id)
    }

    companion object {
        /** Placeholder used before the user has chosen a condition. */
        val Invalid: Condition = Unknown()

        val ScreenOn: Condition = Flag(com.edgeflow.core.ConditionId.SCREEN_ON)
        val KeyguardOn: Condition = Flag(com.edgeflow.core.ConditionId.KEYGUARD_ON)
        val AutoBrightness: Condition = Flag(com.edgeflow.core.ConditionId.AUTO_BRIGHTNESS)
        val AutoRotation: Condition = Flag(com.edgeflow.core.ConditionId.AUTO_ROTATION)
        val WifiOn: Condition = Flag(com.edgeflow.core.ConditionId.WIFI_ON)
        val MobileDataOn: Condition = Flag(com.edgeflow.core.ConditionId.MOBILE_DATA_ON)
        val LocationOn: Condition = Flag(com.edgeflow.core.ConditionId.LOCATION_ON)
        val BluetoothOn: Condition = Flag(com.edgeflow.core.ConditionId.BLUETOOTH_ON)
        val NfcOn: Condition = Flag(com.edgeflow.core.ConditionId.NFC_ON)
        val SyncOn: Condition = Flag(com.edgeflow.core.ConditionId.SYNC_ON)
        val WifiApOn: Condition = Flag(com.edgeflow.core.ConditionId.WIFI_AP_ON)
        val AirplaneModeOn: Condition = Flag(com.edgeflow.core.ConditionId.AIRPLANE_MODE_ON)
        val GestureControlOn: Condition = Flag(com.edgeflow.core.ConditionId.GESTURE_CONTROL_ON)
        val KeyControlOn: Condition = Flag(com.edgeflow.core.ConditionId.KEY_CONTROL_ON)
        val PowerConnected: Condition = Flag(com.edgeflow.core.ConditionId.POWER_CONNECTED)
        val WifiConnected: Condition = Flag(com.edgeflow.core.ConditionId.WIFI_CONNECTED)
        val NetConnected: Condition = Flag(com.edgeflow.core.ConditionId.NET_CONNECTED)
        val HeadsetPlugged: Condition = Flag(com.edgeflow.core.ConditionId.HEADSET_PLUGGED)
        val UsbPlugged: Condition = Flag(com.edgeflow.core.ConditionId.USB_PLUGGED)
        val MusicActive: Condition = Flag(com.edgeflow.core.ConditionId.MUSIC_ACTIVE)
        val Ringing: Condition = Flag(com.edgeflow.core.ConditionId.RINGING)
        val OffHook: Condition = Flag(com.edgeflow.core.ConditionId.OFF_HOOK)
        val SoftKeyboardShowing: Condition = Flag(com.edgeflow.core.ConditionId.SOFT_KEYBOARD_SHOWING)
        val StatusBarShowing: Condition = Flag(com.edgeflow.core.ConditionId.STATUS_BAR_SHOWING)
        val NavBarShowing: Condition = Flag(com.edgeflow.core.ConditionId.NAV_BAR_SHOWING)
    }
}
