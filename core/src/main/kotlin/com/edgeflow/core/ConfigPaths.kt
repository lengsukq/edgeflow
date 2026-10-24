package com.edgeflow.core

/**
 * Where configuration lives.
 *
 * Both the module app and the injected system_server code read the same files,
 * so the location has to be reachable from both. `/data/system/` is used
 * because it is owned by the system and readable by system_server without any
 * IPC; the module app writes there through a privileged shell helper.
 *
 * This mirrors the reference implementation's approach and is the reason the
 * app and the hook can share state without a Binder round-trip on every read.
 */
object ConfigPaths {
    const val ROOT = "/data/system/edgeflow"
    const val CONFIG_FILE = "$ROOT/config.json"
    const val LOG_DIR = "$ROOT/log"
    const val GESTURE_DIR = "$ROOT/gestures"
    const val ICON_DIR = "$ROOT/icons"

    /** Fallback used when the privileged location is not writable. */
    const val FALLBACK_ROOT_SUFFIX = "/files/edgeflow"

    fun fallbackRoot(appDataDir: String) = appDataDir + FALLBACK_ROOT_SUFFIX
}

/** Names of the broadcasts the module app and the hook use to talk. */
object Ipc {
    const val PREFIX = "com.edgeflow"
    const val CONFIG_CHANGED = "$PREFIX.CONFIG_CHANGED"
    const val REQUEST_STATUS = "$PREFIX.REQUEST_STATUS"
    const val STATUS_REPLY = "$PREFIX.STATUS_REPLY"
    const val PERFORM_ACTION = "$PREFIX.PERFORM_ACTION"
    /** Emergency cancellation, allowed only when broadcast sender is our app UID. */
    const val STOP_ALL = "$PREFIX.STOP_ALL"
    const val RESUME_AUTOMATION = "$PREFIX.RESUME_AUTOMATION"
    const val AUTOMATION_HISTORY = "$PREFIX.AUTOMATION_HISTORY"
    /** Privileged system_server -> app update for the UI/overlay Game Mode source of truth. */
    const val GAME_MODE_CHANGED = "$PREFIX.GAME_MODE_CHANGED"
    const val SCHEDULE_ALARM = "$PREFIX.SCHEDULE_ALARM"
    const val SHOW_OVERLAY = "$PREFIX.SHOW_OVERLAY"
    const val OVERLAY_SELECTION = "$PREFIX.OVERLAY_SELECTION"
    const val FREEZE_APP = "$PREFIX.FREEZE_APP"
    const val UNFREEZE_APP = "$PREFIX.UNFREEZE_APP"
    const val UNFREEZE_APP_SUCCEEDED = "$PREFIX.UNFREEZE_APP_SUCCEEDED"

    const val EXTRA_ACTION = "action"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_STATUS = "status"
    const val EXTRA_GAME_MODE_ENABLED = "game_mode_enabled"
    const val EXTRA_OVERLAY_KIND = "overlay_kind"
    const val EXTRA_EDGE = "edge"
    const val EXTRA_PANEL = "panel"
    const val EXTRA_INDEX = "index"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_PACKAGES = "packages"
    const val EXTRA_ACTIVITY_CLASS = "activity_class"
    const val EXTRA_FILTER_ALPHA = "filter_alpha"
    const val EXTRA_SLIDE_PROGRESS = "slide_progress"
    const val EXTRA_SLIDE_LABEL = "slide_label"
    const val EXTRA_POINTER_VISIBLE = "pointer_visible"
    const val EXTRA_POINTER_X = "pointer_x"
    const val EXTRA_POINTER_Y = "pointer_y"
    const val EXTRA_POINTER_SCALE = "pointer_scale"
    const val EXTRA_POINTER_COLOR = "pointer_color"
    const val EXTRA_WIDGET_ID = "widget_id"
    const val EXTRA_WIDGET_WIDTH_DP = "widget_width_dp"
    const val EXTRA_WIDGET_HEIGHT_DP = "widget_height_dp"
    const val EXTRA_WIDGET_X_PERCENT = "widget_x_percent"
    const val EXTRA_WIDGET_Y_PERCENT = "widget_y_percent"
    const val EXTRA_WIDGET_TIMEOUT_SECONDS = "widget_timeout_seconds"
    const val EXTRA_WIDGET_REMEMBER_POSITION = "widget_remember_position"
    const val EXTRA_TRIGGER = "trigger"

    const val APP_PACKAGE = "com.edgeflow"
    const val SYSTEM_PACKAGE = "android"
    const val KIND_PIE = "pie"
    const val KIND_SIDE_BAR = "side_bar"
    const val KIND_PANEL = "panel"
    const val KIND_SHORTCUT_PANEL = "shortcut_panel"
    const val KIND_APPS = "apps"
    const val KIND_RUNNING_APPS = "running_apps"
    const val KIND_FREEZER = "freezer"
    const val KIND_APP_OPTION_MENU = "app_option_menu"
    const val KIND_APP_ACTIVITIES = "app_activities"
    const val KIND_PEN = "pen"
    const val KIND_SCREEN_FILTER = "screen_filter"
    const val KIND_SLIDE_ADJUST = "slide_adjust"
    const val KIND_GESTURE_POINTER = "gesture_pointer"
    const val KIND_FLOATING_WIDGET = "floating_widget"
    const val KIND_CLIPBOARD_HISTORY = "clipboard_history"
    const val KIND_UNIVERSAL_COPY = "universal_copy"
    const val KIND_SCREENSHOT_REGION = "screenshot_region"
    const val KIND_GESTURE_RECORD = "gesture_record"
    const val KIND_GESTURE_RECORD_STOP = "gesture_record_stop"
    const val KIND_UNLOCK_REQUEST = "unlock_request"
    /** Read-only active-hook handshake; system_server responds explicitly. */
    const val MODULE_STATUS_REQUEST = "com.edgeflow.action.MODULE_STATUS_REQUEST"
    const val MODULE_STATUS_REPLY = "com.edgeflow.action.MODULE_STATUS_REPLY"
    const val EXTRA_STATUS_NONCE = "module_status_nonce"
    const val EXTRA_STATUS_FRAMEWORK = "module_status_framework"
    const val EXTRA_STATUS_API = "module_status_api"
    const val EXTRA_STATUS_STAGE = "module_status_stage"
    const val EXTRA_INPUT_HOOK = "status_input_hook"
    const val EXTRA_INPUT_BRIDGE = "status_input_bridge"
    const val EXTRA_INPUT_GATE = "status_input_gate"
    const val EXTRA_INPUT_VERSION = "status_input_version"
    const val EXTRA_INPUT_ZONES = "status_input_zones"
    const val EXTRA_INPUT_BINDINGS = "status_input_bindings"
    const val EXTRA_INPUT_TOUCHES = "status_input_touches"
    const val EXTRA_INPUT_FIRED = "status_input_fired"
    const val EXTRA_INPUT_INJECTION_ATTEMPTS = "status_input_injection_attempts"
    const val EXTRA_INPUT_INJECTION_ACCEPTED = "status_input_injection_accepted"
    const val EXTRA_INPUT_INJECTION_REJECTED = "status_input_injection_rejected"
    const val EXTRA_INPUT_INJECTION_LAST_RESULT = "status_input_injection_last_result"
    const val EXTRA_INPUT_EDGE_DOWNS = "status_input_edge_downs"
    const val EXTRA_INPUT_ACTIVE_DOWNS = "status_input_active_downs"
    const val EXTRA_INPUT_KEYGUARD_BLOCKED = "status_input_keyguard_blocked"
    const val EXTRA_AUTOMATION_STATE = "status_automation_state"
    const val EXTRA_AUTOMATION_ORIGIN = "status_automation_origin"
    const val EXTRA_AUTOMATION_STEPS = "status_automation_steps"
    const val EXTRA_AUTOMATION_FAILURES = "status_automation_failures"
    const val EXTRA_AUTOMATION_RECENT = "status_automation_recent"
    const val EXTRA_HISTORY_END_TIME = "history_end_time"
    const val EXTRA_HISTORY_DURATION = "history_duration"
    const val EXTRA_SCHEDULE_NONCE = "schedule_nonce"

    const val APP_OPTION_DISMISS = 0
    const val APP_OPTION_OPEN = 1
    const val APP_OPTION_FINISH_ACTIVITY = 2
    const val APP_OPTION_APP_INFO = 3
    const val APP_OPTION_PLAY_STORE = 4
    const val APP_OPTION_STOP = 5
    const val APP_OPTION_FREEZE = 6
    const val APP_OPTION_ACTIVITIES = 7

    /** Extra set when the hook replies, so the app can show activation state. */
    const val STATUS_HOOK_ACTIVE = "hook_active"
    const val STATUS_FRAMEWORK = "framework"
    const val STATUS_API_VERSION = "api_version"
}
