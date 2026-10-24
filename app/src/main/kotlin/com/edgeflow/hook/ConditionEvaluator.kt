package com.edgeflow.hook

import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import com.edgeflow.core.ConditionId
import com.edgeflow.core.model.Condition
import com.edgeflow.core.model.ConditionOperator
import com.edgeflow.hook.util.XLog
import com.edgeflow.hook.util.Reflect
import java.util.Calendar

/**
 * Evaluates conditions.
 *
 * Public evaluation is total: a condition that cannot be evaluated (an
 * unavailable service, a malformed operand) does not match. Internally that
 * state is kept distinct from `false` so negation cannot turn it into a match.
 */
internal class ConditionEvaluator(private val repository: ConfigRepository? = null) {

    private enum class Result { MATCH, NO_MATCH, UNAVAILABLE }

    /** Stackless sentinel used to keep unavailable leaf reads distinct from false. */
    private class UnavailableConditionException : RuntimeException(null, null, false, false)

    /** System services are cached because evaluation can happen on the action thread. */
    @Volatile
    private var context: Context? = null

    fun attachContext(context: Context) {
        this.context = context
    }

    /**
     * @param variables current workflow variables, for [Condition.Variable]
     */
    fun evaluate(condition: Condition, variables: Map<String, String>): Boolean =
        evaluateResult(condition, variables) == Result.MATCH

    private fun evaluateResult(condition: Condition, variables: Map<String, String>): Result =
        try {
            when (condition) {
                is Condition.Unknown -> unavailable("condition ${condition.id} is unknown")
                is Condition.Flag -> evaluateFlag(condition.id).toResult()
                is Condition.And -> evaluateAnd(condition.items, variables)
                is Condition.Or -> evaluateOr(condition.items, variables)
                is Condition.Not -> when (evaluateResult(condition.item, variables)) {
                    Result.MATCH -> Result.NO_MATCH
                    Result.NO_MATCH -> Result.MATCH
                    Result.UNAVAILABLE -> Result.UNAVAILABLE
                }
                is Condition.BatteryLevel -> evaluateBattery(condition).toResult()
                is Condition.TimePeriod -> evaluateTimePeriod(condition).toResult()
                is Condition.DaysOfWeek -> evaluateDaysOfWeek(condition).toResult()
                is Condition.DaysOfMonth -> evaluateDaysOfMonth(condition).toResult()
                is Condition.Months -> evaluateMonth(condition).toResult()
                is Condition.AppFocused -> (ForegroundApp.current == condition.packageName).toResult()
                is Condition.WifiSsid -> evaluateWifiSsid(condition).toResult()
                is Condition.Orientation -> evaluateOrientation(condition).toResult()
                is Condition.Variable -> evaluateVariable(condition, variables).toResult()
            }
        } catch (_: UnavailableConditionException) {
            Result.UNAVAILABLE
        } catch (t: Throwable) {
            XLog.w("condition ${condition.id} could not be evaluated (${t.javaClass.simpleName}): ${t.message}")
            Result.UNAVAILABLE
        }

    private fun evaluateAnd(items: List<Condition>, variables: Map<String, String>): Result {
        for (item in items) {
            when (evaluateResult(item, variables)) {
                Result.MATCH -> Unit
                Result.NO_MATCH -> return Result.NO_MATCH
                Result.UNAVAILABLE -> return Result.UNAVAILABLE
            }
        }
        return Result.MATCH
    }

    private fun evaluateOr(items: List<Condition>, variables: Map<String, String>): Result {
        var hasUnavailable = false
        for (item in items) {
            when (evaluateResult(item, variables)) {
                Result.MATCH -> return Result.MATCH
                Result.NO_MATCH -> Unit
                Result.UNAVAILABLE -> hasUnavailable = true
            }
        }
        return if (hasUnavailable) Result.UNAVAILABLE else Result.NO_MATCH
    }

    private fun Boolean.toResult(): Result = if (this) Result.MATCH else Result.NO_MATCH

    private fun evaluateFlag(id: Int): Boolean {
        val ctx = context ?: return unavailable("flag condition $id: system context is not attached")
        return when (id) {
            ConditionId.SCREEN_ON -> (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive
                ?: unavailable("screen-on condition: power service is unavailable")
            ConditionId.KEYGUARD_ON -> (ctx.getSystemService(KeyguardManager::class.java))?.isKeyguardLocked
                ?: unavailable("keyguard condition: keyguard service is unavailable")
            ConditionId.AUTO_ROTATION -> systemSetting(ctx, "accelerometer_rotation") == 1
            ConditionId.AUTO_BRIGHTNESS -> systemSetting(ctx, "screen_brightness_mode") == 1
            ConditionId.WIFI_ON -> isWifiEnabled(ctx)
            ConditionId.MOBILE_DATA_ON -> isMobileDataEnabled(ctx)
            ConditionId.AIRPLANE_MODE_ON -> globalSetting(ctx, "airplane_mode_on") == 1
            ConditionId.GESTURE_CONTROL_ON -> RuntimeToggles.gesturesEnabled
                ?: repository?.config?.settings?.gestureControlEnabled ?: false
            ConditionId.KEY_CONTROL_ON -> RuntimeToggles.keysEnabled
                ?: repository?.config?.settings?.keyControlEnabled ?: false
            ConditionId.BLUETOOTH_ON -> isBluetoothEnabled(ctx)
            ConditionId.NFC_ON -> isNfcEnabled(ctx)
            ConditionId.SYNC_ON -> ContentResolver.getMasterSyncAutomatically()
            ConditionId.WIFI_AP_ON -> isHotspotEnabled(ctx)
            ConditionId.LOCATION_ON -> secureSetting(ctx, "location_mode") != 0
            ConditionId.POWER_CONNECTED -> isPowerConnected(ctx)
            ConditionId.USB_PLUGGED -> isUsbPlugged(ctx)
            ConditionId.HEADSET_PLUGGED -> isHeadsetPlugged(ctx)
            ConditionId.NET_CONNECTED -> isNetworkConnected(ctx)
            ConditionId.WIFI_CONNECTED -> isWifiConnected(ctx)
            ConditionId.MUSIC_ACTIVE -> isMusicActive(ctx)
            ConditionId.RINGING -> isCallState(ctx, TelephonyManager.CALL_STATE_RINGING, "ringing")
            ConditionId.OFF_HOOK -> isCallState(ctx, TelephonyManager.CALL_STATE_OFFHOOK, "off-hook")
            ConditionId.SOFT_KEYBOARD_SHOWING -> SystemUiState.softKeyboardVisible
                ?: unavailable("soft-keyboard visibility has not been observed yet")
            ConditionId.STATUS_BAR_SHOWING -> SystemUiState.statusBarVisible
                ?: unavailable("status-bar visibility has not been observed yet")
            ConditionId.NAV_BAR_SHOWING -> SystemUiState.navBarVisible
                ?: unavailable("navigation-bar visibility has not been observed yet")
            else -> unavailable("condition $id is not implemented")
        }
    }

    private fun evaluateBattery(condition: Condition.BatteryLevel): Boolean {
        val ctx = context ?: return unavailable("battery condition: system context is not attached")
        val manager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return unavailable("battery condition: battery service is unavailable")
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent < 0) return unavailable("battery condition: capacity is unavailable")
        return percent in condition.min..condition.max
    }

    private fun evaluateTimePeriod(condition: Condition.TimePeriod): Boolean {
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = condition.startMinutes
        val end = condition.endMinutes
        return if (start <= end) {
            minutes in start..end
        } else {
            // The window wraps past midnight, so it is an OR of two ranges.
            minutes >= start || minutes <= end
        }
    }

    private fun evaluateDaysOfWeek(condition: Condition.DaysOfWeek): Boolean {
        // Calendar.MONDAY is 2, so shift to a zero-based Monday-first bit index.
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val index = (today + 5) % 7
        return (condition.mask shr index) and 1 == 1
    }

    private fun evaluateDaysOfMonth(condition: Condition.DaysOfMonth): Boolean =
        isOneBasedMaskSelected(
            condition.mask,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
            count = 31,
        )

    private fun evaluateMonth(condition: Condition.Months): Boolean {
        // Calendar.MONTH is zero-based; the model's mask is one-based for readability.
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return isOneBasedMaskSelected(condition.mask, month, count = 12)
    }

    private fun evaluateOrientation(condition: Condition.Orientation): Boolean {
        val orientation = context?.resources?.configuration?.orientation
            ?: return unavailable("orientation condition: system context/configuration is unavailable")
        if (orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            return unavailable("orientation condition: configuration reports unknown orientation $orientation")
        }
        val landscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return landscape == condition.landscape
    }

    private fun evaluateVariable(condition: Condition.Variable, variables: Map<String, String>): Boolean {
        val actual = variables[condition.name]
            ?: return unavailable("variable condition: '${condition.name}' has no current value")
        return compareConditionValues(actual, condition.value, condition.operator)
            ?: unavailable("variable condition '${condition.name}': invalid numeric operand or unsupported operator ${condition.operator}")
    }

    private fun evaluateWifiSsid(condition: Condition.WifiSsid): Boolean {
        val ctx = context ?: return unavailable("Wi-Fi SSID condition: system context is not attached")
        if (condition.ssid.isEmpty()) return unavailable("Wi-Fi SSID condition: expected SSID is empty")

        val connectivity = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return unavailable("Wi-Fi SSID condition: connectivity service is unavailable")
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return unavailable("Wi-Fi SSID condition: active network capabilities are unavailable")
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
        var ssid = info?.ssid
        if (!ssid.isUsableSsid()) {
            // connectionInfo is deprecated on recent Android, but remains a useful
            // fallback where transportInfo is absent or redacted.
            ssid = (ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.ssid
        }
        if (!ssid.isUsableSsid()) {
            return unavailable("Wi-Fi SSID condition: SSID is unavailable or redacted by permissions/location policy/OS version")
        }
        return normalizeSsid(ssid!!) == normalizeSsid(condition.ssid)
    }

    private fun isHeadsetPlugged(ctx: Context): Boolean {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_HEADSET_PLUG))
            ?: return unavailable("headset condition: sticky ACTION_HEADSET_PLUG state is unavailable")
        val state = intent.getIntExtra("state", -1)
        return when (state) {
            0 -> false
            1 -> true
            else -> unavailable("headset condition: sticky broadcast has invalid state $state")
        }
    }

    private fun isUsbPlugged(ctx: Context): Boolean {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return unavailable("USB condition: sticky ACTION_BATTERY_CHANGED state is unavailable")
        val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return (plugType and BatteryManager.BATTERY_PLUGGED_USB) != 0
    }

    private fun isMusicActive(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return unavailable("music-active condition: audio service is unavailable")
        return manager.isMusicActive
    }

    private fun isCallState(ctx: Context, expectedState: Int, label: String): Boolean {
        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return unavailable("$label condition: telephony service is unavailable")
        return manager.callState == expectedState
    }

    private fun isHotspotEnabled(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return unavailable("Wi-Fi hotspot condition: Wi-Fi service is unavailable")
        val enabled = runCatching { Reflect.callMethod(manager, "isWifiApEnabled") as? Boolean }.getOrNull()
        return enabled ?: unavailable("Wi-Fi hotspot state is unavailable on this Android version")
    }

    private fun isWifiEnabled(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return unavailable("Wi-Fi condition: Wi-Fi service is unavailable")
        return manager.isWifiEnabled
    }

    private fun isMobileDataEnabled(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return unavailable("mobile-data condition: telephony service is unavailable")
        return try {
            manager.isDataEnabled
        } catch (_: SecurityException) {
            unavailable("mobile-data condition: telephony permission denied")
        }
    }

    private fun isBluetoothEnabled(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return unavailable("Bluetooth condition: Bluetooth service is unavailable")
        val adapter = manager.adapter
            ?: return unavailable("Bluetooth condition: this device has no Bluetooth adapter")
        return adapter.isEnabled
    }

    private fun isNfcEnabled(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.NFC_SERVICE) as? NfcManager
            ?: return unavailable("NFC condition: NFC service is unavailable")
        val adapter = manager.defaultAdapter
            ?: return unavailable("NFC condition: this device has no NFC adapter")
        return adapter.isEnabled
    }

    private fun isPowerConnected(ctx: Context): Boolean {
        val batteryState = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return unavailable("power-connected condition: battery state is unavailable")
        if (!batteryState.hasExtra(BatteryManager.EXTRA_PLUGGED)) {
            return unavailable("power-connected condition: plugged state is unavailable")
        }
        return isPowerSourceConnected(batteryState.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
    }

    private fun isNetworkConnected(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return unavailable("network condition: connectivity service is unavailable")
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return unavailable("network condition: active network capabilities are unavailable")
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isWifiConnected(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return unavailable("Wi-Fi connection condition: connectivity service is unavailable")
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return unavailable("Wi-Fi connection condition: active network capabilities are unavailable")
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun systemSetting(ctx: Context, key: String): Int =
        android.provider.Settings.System.getInt(ctx.contentResolver, key, 0)

    private fun globalSetting(ctx: Context, key: String): Int =
        android.provider.Settings.Global.getInt(ctx.contentResolver, key, 0)

    private fun secureSetting(ctx: Context, key: String): Int =
        android.provider.Settings.Secure.getInt(ctx.contentResolver, key, 0)

    private fun unavailable(reason: String): Nothing {
        XLog.w("condition could not be evaluated: $reason")
        throw UnavailableConditionException()
    }
}

internal object SystemUiState {
    @Volatile var softKeyboardVisible: Boolean? = null
    @Volatile var statusBarVisible: Boolean? = null
    @Volatile var navBarVisible: Boolean? = null
}

/** Tests/evaluates one-based calendar selectors without tying them to today's date. */
internal fun isOneBasedMaskSelected(mask: Int, value: Int, count: Int): Boolean {
    if (value !in 1..count) return false
    return mask and (1 shl (value - 1)) != 0
}

internal fun isPowerSourceConnected(plugType: Int): Boolean = plugType != 0

/**
 * Compares condition operands. `null` means the operator or its numeric operands
 * are invalid; callers must fail closed and log the reason.
 */
internal fun compareConditionValues(
    actual: String,
    expected: String,
    operator: ConditionOperator,
): Boolean? {
    val actualParsed = actual.toDoubleOrNull()
    val expectedParsed = expected.toDoubleOrNull()
    if (actualParsed != null && !actualParsed.isFinite()) return null
    if (expectedParsed != null && !expectedParsed.isFinite()) return null

    val actualNumber = actualParsed
    val expectedNumber = expectedParsed
    return when (operator) {
        ConditionOperator.EQUAL -> if (actualNumber != null && expectedNumber != null) {
            actualNumber == expectedNumber
        } else {
            actual == expected
        }
        ConditionOperator.NOT_EQUAL -> if (actualNumber != null && expectedNumber != null) {
            actualNumber != expectedNumber
        } else {
            actual != expected
        }
        ConditionOperator.GREATER_THAN -> actualNumber?.let { a -> expectedNumber?.let { a > it } }
        ConditionOperator.GREATER_THAN_OR_EQUAL -> actualNumber?.let { a -> expectedNumber?.let { a >= it } }
        ConditionOperator.LESS_THAN -> actualNumber?.let { a -> expectedNumber?.let { a < it } }
        ConditionOperator.LESS_THAN_OR_EQUAL -> actualNumber?.let { a -> expectedNumber?.let { a <= it } }
        ConditionOperator.LEGACY_ADD,
        ConditionOperator.LEGACY_SUBTRACT,
        ConditionOperator.LEGACY_MULTIPLY,
        ConditionOperator.LEGACY_DIVIDE,
        ConditionOperator.LEGACY_MODULO,
        -> null
    }
}

private fun normalizeSsid(value: String): String = value.removeSurrounding("\"")

private fun String?.isUsableSsid(): Boolean {
    if (this == null) return false
    val normalized = normalizeSsid(this)
    val comparable = normalized.trim()
    return normalized.isNotEmpty() &&
        !comparable.equals("<unknown ssid>", ignoreCase = true) &&
        !comparable.equals("unknown ssid", ignoreCase = true)
}
