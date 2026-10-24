package com.edgeflow.core

/**
 * Action identifiers, mirroring the catalogue of the reference implementation.
 *
 * Ids are part of the on-disk configuration format: they are written into the
 * serialised config and must stay stable once released.
 */
object ActionId {
    const val DEFAULT = 0
    const val NONE = 1
    const val BACK = 2
    const val HOME = 3
    const val RECENT_APPS = 4
    const val MENU = 5
    const val SLEEP_WAKE = 6
    const val MEDIA_PLAY = 8
    const val MEDIA_PLAY_PAUSE = 9
    const val MEDIA_STOP = 10
    const val MEDIA_PREVIOUS = 11
    const val MEDIA_NEXT = 12
    const val BRIGHTNESS_UP = 13
    const val BRIGHTNESS_DOWN = 14
    const val EXPAND_NOTIFICATIONS = 15
    const val EXPAND_QUICK_SETTINGS = 16
    const val SCREENSHOT_FULL = 17
    const val AUTO_BRIGHTNESS = 19
    const val AUTO_ROTATION = 20
    const val WIFI = 21
    const val MOBILE_DATA = 22
    const val LOCATION = 23
    const val BLUETOOTH = 24
    const val NFC = 25
    const val SYNC = 26
    const val WIFI_AP = 27
    const val AIRPLANE_MODE = 28
    const val IMMERSIVE_NAV_BAR = 29
    const val KEEP_SCREEN_ON = 30
    const val GESTURE_RECORD = 31
    const val TORCH = 32
    const val SCREEN_FILTER = 33
    const val GESTURE_CONTROL = 34
    const val KEY_CONTROL = 35
    const val LAST_APP = 36
    const val KILL_FOREGROUND_APP = 37
    const val CLEAR_BACKGROUND_APPS = 38
    const val PEN = 39
    const val SHORTCUTS_PANEL = 40
    const val FREEZER_DRAWER = 42
    const val POWER_MENU = 43
    const val REBOOT = 44
    const val SOFT_REBOOT = 45
    const val SHUTDOWN = 46
    const val LAUNCH_APP = 47
    const val LAUNCH_ACTIVITY = 48
    const val SAVED_MULTI_ACTION = 50
    const val PIE = 51
    const val INJECT_KEY_EVENT = 52
    const val INJECT_GESTURE = 53
    const val DELAY = 54
    const val RUNNING_APPS_DRAWER = 55
    const val INJECT_TEXT = 56
    const val READ_CLIPBOARD = 59
    const val ADJUST_VOLUME = 60
    const val SHELL_SU = 61
    const val CONDITIONAL = 62
    const val MULTI_ACTION = 63
    const val FAST_SCROLL = 64
    const val SIDE_BAR = 65
    const val PREVIOUS_APP = 66
    const val NEXT_APP = 67
    const val SLIDE_ADJUST = 68
    const val RINGER_MODE = 70
    const val APP_OPTION_MENU = 71
    const val EDITABLE_ACTION = 72
    const val IMMERSIVE_STATUS_BAR = 73
    const val ROTATE_SCREEN = 74
    const val DISMISS_KEYGUARD = 75
    const val ACTIVITY_FINISH = 76
    const val KILL_APP = 77
    const val NOOP_79 = 79
    const val REFREEZE = 81
    const val APPS_DRAWER = 82
    const val CUSTOM_PANEL = 83
    const val SAVED_MULTI_ACTION_ALT = 84
    const val SOFT_KEYBOARD = 85
    const val SUCCESSIVE_ADJUST = 86
    const val EDIT_TEXT = 87
    const val SET_VARIABLE = 88
    const val ADJUST = 89
    const val CLIPBOARD = 90
    const val VIBRATE = 92
    const val PLAY_SOUND = 93
    const val FLOATING_WIDGET = 94
    const val SCREENSHOT_REGION = 95
    const val SPLIT_SCREEN = 96
    const val GAME_MODE = 97
    const val UNIVERSAL_COPY = 98
    const val FINISH_ACTIVITY = 99
    const val SHELL = 100
    const val REPEAT = 101
    const val ABORT = 102
    const val MEDIA_CONTROL = 104
    const val INJECT_KEY_EVENT_ALT = 105
    const val GESTURE_POINTER = 106
    const val SUB_GESTURES = 107
    const val INJECT_TEXT_ALT = 108
    const val NOTIFY = 109
    const val SPEECH = 110
    const val TOAST = 111
    const val STOP_ALL = 112
    const val ACTIVITY_113 = 113
    const val ACTIVITY_114 = 114
    const val APP_115 = 115

    /** Emitted by [com.edgeflow.core.model.SpecialAction]; carries a broadcast or activity target. */
    const val SPECIAL = 65535
}

/** Condition identifiers, mirroring the reference catalogue. */
object ConditionId {
    const val INVALID = 0
    const val AND = 1
    const val OR = 2
    const val NOT = 3
    const val SCREEN_ON = 4
    const val KEYGUARD_ON = 5
    const val AUTO_BRIGHTNESS = 6
    const val AUTO_ROTATION = 7
    const val WIFI_ON = 8
    const val MOBILE_DATA_ON = 9
    const val LOCATION_ON = 10
    const val BLUETOOTH_ON = 11
    const val NFC_ON = 12
    const val SYNC_ON = 13
    const val WIFI_AP_ON = 14
    const val AIRPLANE_MODE_ON = 15
    const val GESTURE_CONTROL_ON = 16
    const val KEY_CONTROL_ON = 17
    const val POWER_CONNECTED = 18
    const val WIFI_CONNECTED = 19
    const val NET_CONNECTED = 20
    const val HEADSET_PLUGGED = 21
    const val USB_PLUGGED = 22
    const val MUSIC_ACTIVE = 23
    const val RINGING = 24
    const val OFF_HOOK = 25
    const val BATTERY_LEVEL = 26
    const val TIME_PERIOD = 27
    const val DAYS_OF_WEEK = 28
    const val DAYS_OF_MONTH = 29
    const val MONTHS = 30
    const val WIFI_SSID = 31
    const val APP_FOCUSED = 32
    const val ORIENTATION = 33
    const val SOFT_KEYBOARD_SHOWING = 34
    const val STATUS_BAR_SHOWING = 35
    const val NAV_BAR_SHOWING = 36
    const val VARIABLE = 37
    const val WIFI_SSID_ALT = 38
}

/** System state trigger IDs, based on `a.p.g`; USER_SWITCHED is an EdgeFlow extension. */
object StateTriggerId {
    const val BOOT_COMPLETED = 0
    const val SCREEN_ON = 1
    const val SCREEN_OFF = 2
    const val USER_PRESENT = 3
    const val BATTERY_FULL = 4
    const val BATTERY_LOW = 5
    const val BATTERY_OKAY = 6
    const val POWER_CONNECTED = 7
    const val POWER_DISCONNECTED = 8
    const val WIFI_CONNECTED = 9
    const val WIFI_DISCONNECTED = 10
    const val NET_CONNECTED = 11
    const val NET_DISCONNECTED = 12
    const val AIRPLANE_MODE_ON = 13
    const val AIRPLANE_MODE_OFF = 14
    const val HEADSET_PLUGGED = 15
    const val HEADSET_UNPLUGGED = 16
    const val USB_PLUGGED = 17
    const val USB_UNPLUGGED = 18
    const val SCREEN_ROTATED = 19
    const val ROTATED_PORTRAIT = 20
    const val ROTATED_LANDSCAPE = 21
    const val SMS_RECEIVED = 22
    const val INCOMING_CALL_RINGING = 23
    const val INCOMING_CALL_CONNECTED = 24
    const val INCOMING_CALL_ENDED = 25
    const val OUTGOING_CALL_DIALED = 26
    const val OUTGOING_CALL_CONNECTED = 27
    const val OUTGOING_CALL_ENDED = 28
    const val SOFT_KEYBOARD_SHOWN = 29
    const val SOFT_KEYBOARD_HIDDEN = 30
    const val STATUS_BAR_SHOWN = 31
    const val STATUS_BAR_HIDDEN = 32
    const val NAV_BAR_SHOWN = 33
    const val NAV_BAR_HIDDEN = 34
    const val FULL_SCREEN = 35
    const val EXIT_FULL_SCREEN = 36

    /** EdgeFlow extension; the reference uses this broadcast to update its active user instead. */
    const val USER_SWITCHED = 37
}

/** Application lifecycle triggers, mirroring `a.f` of the reference implementation. */
object AppStateTriggerId {
    const val STARTED = 0
    const val STOPPED = 1
    const val GAINED_FOCUS = 2
    const val LOST_FOCUS = 3
}
