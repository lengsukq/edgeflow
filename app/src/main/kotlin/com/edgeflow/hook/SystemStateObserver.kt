package com.edgeflow.hook

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.AlarmManager
import android.app.PendingIntent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.edgeflow.core.Edge
import com.edgeflow.core.AutomationSchedulePolicy
import com.edgeflow.core.ScheduleDispatchGate
import com.edgeflow.core.Ipc
import com.edgeflow.core.StateTriggerId
import com.edgeflow.core.model.Action
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap
import java.util.UUID

/**
 * Observes system state and fires the corresponding triggers.
 *
 * ## Why not just register a BroadcastReceiver
 *
 * Half of the states we care about have no broadcast: whether the status bar is
 * hidden, whether the navigation bar is hidden, which app has focus, and whether
 * a soft keyboard is up are all internal state. Those are observed by hooking the
 * component that owns the state. The rest are ordinary broadcasts and are
 * handled as such, because a receiver is far cheaper than a hook.
 *
 * Every hook here is read-only: it observes and never alters the value it sees.
 */
internal class SystemStateObserver(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val repository: ConfigRepository,
    private val executor: ActionExecutor,
    private val inputInterceptor: InputInterceptor,
) : ManagedComponent {

    override val name: String = "system-state"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var receiver: android.content.BroadcastReceiver? = null
    private var userSwitchedReceiver: android.content.BroadcastReceiver? = null
    private var overlaySelectionReceiver: android.content.BroadcastReceiver? = null
    private val lastScheduleExecution = mutableMapOf<Int, Long>()
    private var alarmToken: String? = null
    private var scheduledAlarm: PendingIntent? = null
    private var unsubscribeScheduleConfig: (() -> Unit)? = null
    private val rescheduleAlarm = Runnable { scheduleNextAlarm() }
    private val activityStates = WeakHashMap<Any, TrackedActivityState>()
    private val activeActivityCounts = mutableMapOf<String, Int>()
    private val schedulePoll = object : Runnable {
        override fun run() {
            runDueSchedules()
            val millisToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L) + 250L
            mainHandler.postDelayed(this, millisToNextMinute)
        }
    }

    /** Last known bar visibility, so triggers fire on change rather than on every callback. */
    private var statusBarVisible: Boolean? = null
    private var navBarVisible: Boolean? = null
    private var softKeyboardVisible: Boolean? = null
    private var immersive: Boolean? = null
    private var wifiConnected: Boolean? = null
    private var networkConnected: Boolean? = null
    private var usbPlugged: Boolean? = null
    private var batteryWasFull: Boolean? = null
    private var lastOrientation: Int? = null
    private var unregisterCallState: (() -> Unit)? = null
    private var lastCallState: Int = TelephonyManager.CALL_STATE_IDLE
    private var incomingCall = false

    override fun install(hooks: HookRegistrar) {
        // SystemServer.start* are critical boot methods. Never hook them just
        // to discover Context; use the verified ActivityManager-ready Context
        // supplied by SystemServerStatusHook.
        installActivityFocusHooks(hooks)
        installInputMethodHooks(hooks)
        installBarVisibilityHooks(hooks)
    }

    override fun uninstall() {
        receiver?.let { runCatching { context?.unregisterReceiver(it) } }
        userSwitchedReceiver?.let { runCatching { context?.unregisterReceiver(it) } }
        overlaySelectionReceiver?.let { runCatching { context?.unregisterReceiver(it) } }
        unregisterCallState?.let { runCatching { it() } }
        mainHandler.removeCallbacks(schedulePoll)
        mainHandler.removeCallbacks(rescheduleAlarm)
        unsubscribeScheduleConfig?.invoke()
        unsubscribeScheduleConfig = null
        cancelScheduledAlarm()
        receiver = null
        userSwitchedReceiver = null
        overlaySelectionReceiver = null
        unregisterCallState = null
    }

    fun onSystemContextReady(ctx: Context) {
        if (context != null) return
        context = ctx
        lastOrientation = ctx.resources.configuration.orientation
        KeyguardState.refresh(ctx)
        executor.setContext(ctx)
        inputInterceptor.bindFromLocalServices()
        inputInterceptor.onSystemContextReady(ctx)
        registerReceivers(ctx)
        registerCallStateListener(ctx)
        registerOverlaySelectionReceiver(ctx)
        unsubscribeScheduleConfig = repository.addConfigListener {
            mainHandler.removeCallbacks(rescheduleAlarm)
            mainHandler.postDelayed(rescheduleAlarm, 150)
        }
        mainHandler.post(schedulePoll)
        mainHandler.post(rescheduleAlarm)
        XLog.i("system context acquired")
    }

    /**
     * Registers broadcasts. API 33+ uses RECEIVER_EXPORTED explicitly;
     * the legacy four-argument overload is reachable on Android 9-12 only.
     */
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceivers(ctx: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_NEW_OUTGOING_CALL)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Ipc.SCHEDULE_ALARM)
        }

        val handler = SystemStateReceiver { intent -> onBroadcast(intent) }
        receiver = handler
        try {
            // registerReceiverForAllUsers is required: the module must see events
            // for every user profile, not only the one system_server runs as.
            // Reflection may successfully invoke a void/nullable-returning
            // registerReceiverForAllUsers, which legitimately returns null.
            // Check INVOCATION SUCCESS, never the returned Intent, otherwise
            // the same receiver is registered twice and receives two events.
            val allUsersMethod = Reflect.methodByArity(
                ctx.javaClass, "registerReceiverForAllUsers", 4,
            )
            val allUsersRegistered = allUsersMethod?.let { method ->
                runCatching {
                    method.invoke(ctx, handler, filter, null, mainHandler)
                }.onFailure {
                    XLog.w("all-users state receiver registration failed; using ordinary receiver")
                }.isSuccess
            } ?: false
            if (!allUsersRegistered) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.registerReceiver(handler, filter, null, mainHandler, Context.RECEIVER_EXPORTED)
                } else {
                    ctx.registerReceiver(handler, filter, null, mainHandler)
                }
            }
            XLog.d("state receivers registered")
        } catch (t: Throwable) {
            XLog.w("could not register state receivers: ${t.message}")
        }
        registerUserSwitchedReceiver(ctx)
    }

    private fun registerUserSwitchedReceiver(ctx: Context) {
        val filter = IntentFilter(StateBroadcastMapping.ACTION_USER_SWITCHED)
        val handler = UserSwitchedReceiver { intent, senderUid ->
            StateBroadcastMapping.userSwitchedTriggerId(
                action = intent.action,
                userHandle = intent.getIntExtra(EXTRA_USER_HANDLE, -1),
                senderUid = senderUid,
            )?.let(::fire)
        }
        userSwitchedReceiver = handler
        try {
            // The sender permission also protects older Android releases where
            // BroadcastReceiver.sentFromUid is not available.
            val registerForAllUsers = Reflect.methodByArity(ctx.javaClass, "registerReceiverForAllUsers", 4)
            if (registerForAllUsers != null) {
                registerForAllUsers.invoke(ctx, handler, filter, USER_SWITCH_SENDER_PERMISSION, mainHandler)
            } else {
                ctx.registerReceiver(handler, filter, USER_SWITCH_SENDER_PERMISSION, mainHandler)
            }
            XLog.d("user-switch receiver registered")
        } catch (t: Throwable) {
            userSwitchedReceiver = null
            XLog.w("could not register user-switch receiver: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun registerCallStateListener(ctx: Context) {
        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            unregisterCallState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Api31TelephonyCallState.register(manager, ctx.mainExecutor, ::handleCallStateChanged)
            } else {
                val listener = object : PhoneStateListener() {
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChanged(state)
                    }
                }
                manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                val unregister = { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
                unregister
            }
            XLog.d("telephony call-state listener registered")
        } catch (t: Throwable) {
            XLog.w("call-state triggers unavailable: ${t.message}")
        }
    }

    private fun handleCallStateChanged(state: Int) {
        val previous = lastCallState
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (previous != TelephonyManager.CALL_STATE_RINGING) {
                    incomingCall = true
                    fire(StateTriggerId.INCOMING_CALL_RINGING)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> when (previous) {
                TelephonyManager.CALL_STATE_RINGING -> fire(StateTriggerId.INCOMING_CALL_CONNECTED)
                TelephonyManager.CALL_STATE_IDLE -> {
                    incomingCall = false
                    fire(StateTriggerId.OUTGOING_CALL_CONNECTED)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> if (previous != TelephonyManager.CALL_STATE_IDLE) {
                fire(
                    if (incomingCall) StateTriggerId.INCOMING_CALL_ENDED
                    else StateTriggerId.OUTGOING_CALL_ENDED,
                )
                incomingCall = false
            }
        }
        lastCallState = state
    }

    // Newer Android versions explicitly use RECEIVER_EXPORTED below. The
    // two-argument registration is reachable on API 28-32 only.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerOverlaySelectionReceiver(ctx: Context) {
        val appUid = try {
            ctx.packageManager.getApplicationInfo(Ipc.APP_PACKAGE, 0).uid
        } catch (t: Throwable) {
            XLog.w("cannot resolve EdgeFlow UID; overlay selections will be ignored: ${t.message}")
            return
        }

        val selectionReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val request = intent ?: return
                val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    sentFromUid.takeIf { it >= 0 } ?: Binder.getCallingUid()
                } else {
                    Binder.getCallingUid()
                }
                if (senderUid != appUid) {
                    XLog.w("rejected overlay/freezer request from uid $senderUid")
                    return
                }
                when (request.action) {
                    Ipc.OVERLAY_SELECTION -> handleOverlaySelection(request)
                    Ipc.PERFORM_ACTION -> handleActionRequest(request)
                    Ipc.STOP_ALL -> executor.stopAll()
                    Ipc.RESUME_AUTOMATION -> executor.resumeAfterUserUnlock()
                    Ipc.FREEZE_APP -> request.getStringExtra(Ipc.EXTRA_PACKAGE)?.let(executor::freezeApp)
                    Ipc.UNFREEZE_APP -> request.getStringExtra(Ipc.EXTRA_PACKAGE)?.let(executor::unfreezeApp)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Ipc.OVERLAY_SELECTION)
            addAction(Ipc.PERFORM_ACTION)
            addAction(Ipc.STOP_ALL)
            addAction(Ipc.RESUME_AUTOMATION)
            addAction(Ipc.FREEZE_APP)
            addAction(Ipc.UNFREEZE_APP)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(selectionReceiver, filter, null, mainHandler, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(selectionReceiver, filter, null, mainHandler)
            }
            overlaySelectionReceiver = selectionReceiver
            XLog.d("overlay selection receiver registered")
        } catch (t: Throwable) {
            XLog.w("could not register overlay selection receiver: ${t.message}")
        }
    }

    private fun handleActionRequest(intent: Intent) {
        val action = if (intent.hasExtra(Ipc.EXTRA_TRIGGER)) {
            val triggerId = intent.getIntExtra(Ipc.EXTRA_TRIGGER, -1)
            repository.config.stateTriggers[triggerId]
        } else {
            val actionId = intent.getIntExtra(Ipc.EXTRA_SLOT, -1)
            repository.config.savedMultiActions.firstOrNull { it.id == actionId }?.action
        }
        if (action == null || action.id == com.edgeflow.core.ActionId.NONE) {
            XLog.w("ignored empty or unknown external action request")
            return
        }
        executor.run(action, "external app request")
    }

    private fun handleOverlaySelection(intent: Intent) {
        val kind = intent.getStringExtra(Ipc.EXTRA_OVERLAY_KIND) ?: return
        val edgeIndex = intent.getIntExtra(Ipc.EXTRA_EDGE, Edge.LEFT.index)
        val edge = Edge.of(edgeIndex) ?: Edge.LEFT
        val index = intent.getIntExtra(Ipc.EXTRA_INDEX, -1)
        val config = repository.config
        if (kind == Ipc.KIND_APP_OPTION_MENU) {
            executor.handleAppOptionMenuSelection(index, intent.getStringExtra(Ipc.EXTRA_PACKAGE))
            return
        }
        if (kind == Ipc.KIND_APP_ACTIVITIES) {
            executor.handleAppActivitySelection(
                intent.getStringExtra(Ipc.EXTRA_PACKAGE),
                intent.getStringExtra(Ipc.EXTRA_ACTIVITY_CLASS),
            )
            return
        }
        if (kind == Ipc.KIND_FREEZER) {
            intent.getStringExtra(Ipc.EXTRA_PACKAGE)?.let(executor::unfreezeApp)
            return
        }
        val action = when (kind) {
            Ipc.KIND_PIE -> config.pie.edge(edge).slot(index)
            Ipc.KIND_SIDE_BAR -> config.sideBars.items(edge).getOrNull(index)
            Ipc.KIND_PANEL -> config.panels.firstOrNull { it.id == edgeIndex }?.cells?.getOrNull(index)
            Ipc.KIND_SHORTCUT_PANEL -> config.shortcutPanels.edges[edge.index]?.cells?.getOrNull(index)
            Ipc.KIND_APPS, Ipc.KIND_RUNNING_APPS -> intent.getStringExtra(Ipc.EXTRA_PACKAGE)
                ?.takeIf(String::isNotBlank)
                ?.let { Action.LaunchApp(it) }
            else -> null
        }
        if (action == null || action.id == com.edgeflow.core.ActionId.NONE) {
            XLog.d("overlay $kind closed without selecting an action")
            return
        }
        executor.run(action, "overlay $kind selection=$index")
    }

    private fun onBroadcast(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> fire(StateTriggerId.BOOT_COMPLETED)
            Intent.ACTION_SCREEN_ON -> {
                context?.let(KeyguardState::refresh)
                fire(StateTriggerId.SCREEN_ON)
            }
            Intent.ACTION_SCREEN_OFF -> {
                context?.let(KeyguardState::refresh)
                inputInterceptor.onScreenOff()
                fire(StateTriggerId.SCREEN_OFF)
            }
            Intent.ACTION_USER_PRESENT -> {
                KeyguardState.isShowing = false
                fire(StateTriggerId.USER_PRESENT)
            }
            Intent.ACTION_POWER_CONNECTED -> fire(StateTriggerId.POWER_CONNECTED)
            Intent.ACTION_POWER_DISCONNECTED -> fire(StateTriggerId.POWER_DISCONNECTED)
            Intent.ACTION_BATTERY_LOW -> fire(StateTriggerId.BATTERY_LOW)
            Intent.ACTION_BATTERY_OKAY -> fire(StateTriggerId.BATTERY_OKAY)
            Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged(intent)
            ConnectivityManager.CONNECTIVITY_ACTION,
            android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION,
            -> onNetworkChanged()
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                fire(if (on) StateTriggerId.AIRPLANE_MODE_ON else StateTriggerId.AIRPLANE_MODE_OFF)
            }
            Intent.ACTION_HEADSET_PLUG -> {
                val plugged = intent.getIntExtra("state", 0) == 1
                fire(if (plugged) StateTriggerId.HEADSET_PLUGGED else StateTriggerId.HEADSET_UNPLUGGED)
            }
            Intent.ACTION_CONFIGURATION_CHANGED -> onConfigurationChanged()
            Intent.ACTION_NEW_OUTGOING_CALL -> fire(StateTriggerId.OUTGOING_CALL_DIALED)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                runDueSchedules()
                scheduleNextAlarm()
            }
            Ipc.SCHEDULE_ALARM -> {
                if (intent.getStringExtra(Ipc.EXTRA_SCHEDULE_NONCE) != alarmToken ||
                    alarmToken == null
                ) return
                runDueSchedules()
                scheduleNextAlarm()
            }
            else -> Unit
        }
    }

    private fun runDueSchedules() {
        val now = java.util.Calendar.getInstance()
        val dateKey = (now.get(java.util.Calendar.YEAR) * 366L + now.get(java.util.Calendar.DAY_OF_YEAR)) * 1_440L
        val nowMillis = System.currentTimeMillis()
        val schedules = repository.config.schedules
        val configuredIds = schedules.mapTo(mutableSetOf()) { it.id }
        lastScheduleExecution.keys.retainAll(configuredIds)
        val lastRunPrefs = runCatching {
            context?.getSharedPreferences("edgeflow_schedule_execution", Context.MODE_PRIVATE)
        }.getOrNull()

        schedules.forEach { schedule ->
            if (!AutomationSchedulePolicy.dueNow(schedule, nowMillis)) return@forEach
            val minuteKey = dateKey + schedule.hour * 60 + schedule.minute
            if (lastScheduleExecution[schedule.id] == minuteKey ||
                lastRunPrefs?.getLong("schedule_${schedule.id}", Long.MIN_VALUE) == minuteKey
            ) return@forEach

            // Never consume a schedule before the executor actually accepts
            // it. A busy worker rejects the run, and the minute fallback
            // must be allowed to retry during the five-minute grace window.
            val accepted = ScheduleDispatchGate.acceptThenMark(
                alreadyDelivered = false,
                accept = { executor.run(schedule.action, "schedule ${schedule.id}") },
                markDelivered = {
                    lastScheduleExecution[schedule.id] = minuteKey
                    // Persistent deduplication across system_server restarts.
                    runCatching {
                        lastRunPrefs?.edit()?.putLong("schedule_${schedule.id}", minuteKey)?.commit()
                    }.onFailure { XLog.w("could not persist schedule execution marker") }
                },
            )
            if (!accepted) XLog.w("schedule ${schedule.id} deferred: worker busy or unavailable")
        }
    }

    private fun cancelScheduledAlarm() {
        val ctx = context ?: return
        scheduledAlarm?.let { alarm ->
            runCatching {
                (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(alarm)
                alarm.cancel()
            }
        }
        scheduledAlarm = null
        alarmToken = null
    }

    /**
     * The exact RTC wake alarm is primary. Minute polling stays as a backup
     * for vendor ROMs that restrict AlarmManager or dynamic system broadcasts.
     */
    private fun scheduleNextAlarm() {
        val ctx = context ?: return
        cancelScheduledAlarm()
        val triggerMillis = AutomationSchedulePolicy.nextRun(
            System.currentTimeMillis(),
            repository.config.schedules,
        ) ?: return
        val manager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val token = UUID.randomUUID().toString()
        val request = Intent(Ipc.SCHEDULE_ALARM)
            .setPackage(Ipc.SYSTEM_PACKAGE)
            .putExtra(Ipc.EXTRA_SCHEDULE_NONCE, token)
        val alarm = runCatching {
            PendingIntent.getBroadcast(
                ctx,
                0x45464c,
                request,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrElse {
            XLog.w("cannot create EdgeFlow scheduled alarm: ${it.javaClass.simpleName}")
            return
        }
        val scheduled = runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, alarm)
        }.recoverCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, alarm)
            XLog.w("exact scheduled alarm unavailable; using idle-aware fallback")
        }.isSuccess
        if (scheduled) {
            scheduledAlarm = alarm
            alarmToken = token
        } else {
            alarm.cancel()
            XLog.w("AlarmManager rejected scheduled action; minute polling remains as fallback")
        }
    }

    private fun onBatteryChanged(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val usbNow = plugged and BatteryManager.BATTERY_PLUGGED_USB != 0
        usbPlugged?.let { previous ->
            if (previous != usbNow) {
                fire(if (usbNow) StateTriggerId.USB_PLUGGED else StateTriggerId.USB_UNPLUGGED)
            }
        }
        usbPlugged = usbNow

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val isFull = level * 100 / scale >= 100
        if (batteryWasFull == false && isFull) fire(StateTriggerId.BATTERY_FULL)
        batteryWasFull = isFull
    }

    private fun onNetworkChanged() {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeNetwork = manager.activeNetwork
        val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
        val connectedNow = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifiNow = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true

        networkConnected?.let { previous ->
            if (previous != connectedNow) {
                fire(if (connectedNow) StateTriggerId.NET_CONNECTED else StateTriggerId.NET_DISCONNECTED)
            }
        }
        wifiConnected?.let { previous ->
            if (previous != wifiNow) {
                fire(if (wifiNow) StateTriggerId.WIFI_CONNECTED else StateTriggerId.WIFI_DISCONNECTED)
            }
        }
        networkConnected = connectedNow
        wifiConnected = wifiNow
    }

    private fun onConfigurationChanged() {
        inputInterceptor.onDisplayChanged()
        val orientation = context?.resources?.configuration?.orientation ?: return
        if (orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT
        ) return
        val previous = lastOrientation
        lastOrientation = orientation
        if (previous == null || previous == orientation) return

        fire(StateTriggerId.SCREEN_ROTATED)
        fire(
            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                StateTriggerId.ROTATED_LANDSCAPE
            } else {
                StateTriggerId.ROTATED_PORTRAIT
            },
        )
    }

    /**
     * Tracks the foreground package.
     *
     * `ActivityRecord.setState` is where an activity's lifecycle state is
     * committed, which makes it the earliest reliable signal that focus moved.
     * The reference implementation hooks the same transition from
     * `ActivityTaskManagerService`; `ActivityRecord` is used here because the
     * method survives the ActivityTaskManager refactor that removed several of
     * its neighbours.
     */
    private fun installActivityFocusHooks(hooks: HookRegistrar) {
        val activityRecord = Reflect.findClass("com.android.server.wm.ActivityRecord", classLoader) ?: run {
            XLog.w("ActivityRecord not found; app focus triggers and per-app rules stay disabled")
            return
        }
        val setState = Reflect.method(activityRecord, "setState", activityRecord.declaredClasses.firstOrNull { it.isEnum } ?: String::class.java, String::class.java)
            ?: Reflect.methodAny(activityRecord, "setState")
            ?: run {
                XLog.w("ActivityRecord.setState not found; app focus triggers disabled")
                return
            }

        hooks.install("ActivityRecord.setState", setState) { chain ->
            val result = chain.proceed()
            XLog.guarded("foreground app update") {
                val packageName = Reflect.readField(chain.thisObject, "packageName") as? String
                val state = chain.args.firstOrNull()?.toString()
                if (packageName != null && state != null) {
                    updateActivityState(chain.thisObject, packageName, state)
                }
            }
            result
        }
    }

    private fun updateActivityState(record: Any, packageName: String, rawState: String) {
        val state = rawState.substringAfterLast('.').uppercase()
        updateForegroundActivityToken(record, state)
        val nextState = TrackedActivityState(packageName, state)
        val events = mutableListOf<Pair<String, AppFocus>>()
        var packageBecameInactive = false

        synchronized(activityStates) {
            val previous = activityStates.put(record, nextState)
            val wasActive = previous?.state in ACTIVE_ACTIVITY_STATES
            val isActive = state in ACTIVE_ACTIVITY_STATES

            if (previous != null && previous.packageName != packageName && wasActive) {
                decrementActiveCount(previous.packageName)
                if (activeActivityCounts[previous.packageName] == null) {
                    events += previous.packageName to AppFocus.STOPPED
                }
            }

            if (!wasActive && isActive) {
                val count = (activeActivityCounts[packageName] ?: 0) + 1
                activeActivityCounts[packageName] = count
                if (count == 1) events += packageName to AppFocus.STARTED
            } else if (wasActive && !isActive) {
                decrementActiveCount(packageName)
                if (activeActivityCounts[packageName] == null) {
                    events += packageName to AppFocus.STOPPED
                    packageBecameInactive = true
                }
            }
        }

        events.forEach { (app, focus) -> fireAppState(app, focus) }
        if (state == "RESUMED" && ForegroundApp.current != packageName) {
            val previous = ForegroundApp.current
            ForegroundApp.previous = previous
            ForegroundApp.current = packageName
            if (previous != null) fireAppState(previous, AppFocus.LOST)
            fireAppState(packageName, AppFocus.GAINED)
        } else if (packageBecameInactive && ForegroundApp.current == packageName) {
            ForegroundApp.current = null
        }
    }

    private fun updateForegroundActivityToken(record: Any, state: String) {
        val tokenField = if (Build.VERSION.SDK_INT >= 33) "token" else "appToken"
        val token = Reflect.readField(record, tokenField) ?: return
        if (state == "RESUMED") {
            ForegroundActivity.token = token
        } else if (ForegroundActivity.token === token) {
            ForegroundActivity.token = null
        }
    }

    private fun decrementActiveCount(packageName: String) {
        val next = (activeActivityCounts[packageName] ?: 1) - 1
        if (next <= 0) activeActivityCounts.remove(packageName) else activeActivityCounts[packageName] = next
    }

    /**
     * Tracks soft-keyboard visibility.
     *
     * `InputMethodManagerService.setImeWindowStatusLocked` is called whenever the
     * IME window changes visibility, which is exactly the transition we want.
     * The method is private and its parameter list has grown over time, so it is
     * resolved by name and arity.
     */
    private fun installInputMethodHooks(hooks: HookRegistrar) {
        val imm = Reflect.findClass("com.android.server.inputmethod.InputMethodManagerService", classLoader) ?: run {
            XLog.w("InputMethodManagerService not found; keyboard triggers disabled")
            return
        }
        val setImeStatus = Reflect.methodByArity(imm, "setImeWindowStatusLocked", 3)
            ?: Reflect.methodByArity(imm, "setImeWindowStatusLocked", 4)
            ?: Reflect.methodAny(imm, "setImeWindowStatusLocked")
            ?: run {
                XLog.w("setImeWindowStatusLocked not found; keyboard triggers disabled")
                return
            }

        hooks.install("InputMethodManagerService.setImeWindowStatusLocked", setImeStatus) { chain ->
            val result = chain.proceed()
            XLog.guarded("ime visibility") {
                // Older releases prepend the window token; newer releases put
                // the visibility integer first. IME_VISIBLE is a bit, not zero/nonzero.
                val visibility = ImeWindowVisibility.valueFrom(chain.args)
                if (visibility == null) {
                    XLog.w("could not read the IME visibility argument")
                    return@guarded
                }
                val visible = ImeWindowVisibility.isVisible(visibility)
                if (visible != softKeyboardVisible) {
                    softKeyboardVisible = visible
                    SystemUiState.softKeyboardVisible = visible
                    fire(
                        if (visible) StateTriggerId.SOFT_KEYBOARD_SHOWN
                        else StateTriggerId.SOFT_KEYBOARD_HIDDEN,
                    )
                }
            }
            result
        }
    }

    /**
     * Tracks status bar and navigation bar visibility.
     *
     * Android 11+ commits bar visibility through InsetsSourceProvider. Android
     * 9–10 still use BarController, whose return value reports whether the
     * requested showing state actually changed.
     */
    private fun installBarVisibilityHooks(hooks: HookRegistrar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            installInsetsSourceProviderHook(hooks)
        } else {
            installLegacyBarControllerHook(hooks, Build.VERSION.SDK_INT)
        }
    }

    /** API 28–29 only. The newer InsetsSourceProvider hook remains exclusive to API 30+. */
    private fun installLegacyBarControllerHook(hooks: HookRegistrar, apiLevel: Int) {
        val className = BarVisibilityMapping.legacyControllerClassName(apiLevel) ?: run {
            XLog.w("BarController not supported on API $apiLevel; status bar and nav bar triggers disabled")
            return
        }
        val controller = Reflect.findClass(className, classLoader) ?: run {
            XLog.w("$className not found; status bar and nav bar triggers disabled")
            return
        }
        val setBarShowing = Reflect.method(
            controller,
            "setBarShowingLw",
            Boolean::class.javaPrimitiveType,
        ) ?: run {
            XLog.w("$className.setBarShowingLw(boolean) not found; status bar and nav bar triggers disabled")
            return
        }

        hooks.install("BarController.setBarShowingLw", setBarShowing) { chain ->
            val result = chain.proceed()
            if (result != true) return@install result
            XLog.guarded("bar visibility") {
                val showing = chain.args.firstOrNull() as? Boolean ?: return@guarded
                val tag = Reflect.readField(chain.thisObject, "mTag") as? String
                val change = BarVisibilityMapping.legacyChange(
                    apiLevel = apiLevel,
                    controllerClassName = controller.name,
                    controllerTag = tag,
                    changed = result as? Boolean,
                    showing = showing,
                ) ?: return@guarded
                onBarVisibilityChanged(change)
            }
            result
        }
    }

    /** API 30+ only. */
    private fun installInsetsSourceProviderHook(hooks: HookRegistrar) {
        val provider = Reflect.findClass("com.android.server.wm.InsetsSourceProvider", classLoader) ?: run {
            XLog.w("InsetsSourceProvider not found; status bar and nav bar triggers disabled")
            return
        }
        val updateVisibility = Reflect.methodByArity(provider, "updateVisibility", 0)
            ?: Reflect.methodAny(provider, "updateVisibility")
            ?: run {
                XLog.w("updateVisibility not found; status bar and nav bar triggers disabled")
                return
            }

        hooks.install("InsetsSourceProvider.updateVisibility", updateVisibility) { chain ->
            val result = chain.proceed()
            XLog.guarded("bar visibility") {
                val source = Reflect.readField(chain.thisObject, "mSource") ?: return@guarded
                val type = Reflect.callMethod(source, "getType") as? Int ?: return@guarded
                val sourceVisible = Reflect.callMethod(source, "isVisible") as? Boolean
                val serverVisible = Reflect.readBooleanField(chain.thisObject, "mServerVisible", true)
                val visible = BarVisibilityMapping.effectiveSourceVisibility(sourceVisible, serverVisible)
                onInsetVisibilityChanged(type, visible)
            }
            result
        }
    }

    private fun onInsetVisibilityChanged(type: Int, visible: Boolean) {
        val bar = BarVisibilityMapping.barForInsetType(type) ?: return
        onBarVisibilityChanged(BarVisibilityMapping.Change(bar, visible))
    }

    private fun onBarVisibilityChanged(change: BarVisibilityMapping.Change) {
        when (change.bar) {
            BarVisibilityMapping.Bar.STATUS -> {
                if (change.visible == statusBarVisible) return
                statusBarVisible = change.visible
                SystemUiState.statusBarVisible = change.visible
            }
            BarVisibilityMapping.Bar.NAVIGATION -> {
                if (change.visible == navBarVisible) return
                navBarVisible = change.visible
                SystemUiState.navBarVisible = change.visible
            }
        }
        fire(BarVisibilityMapping.triggerId(change))
        updateImmersive()
    }

    /** Derives the composite "full screen" trigger from the two bar states. */
    private fun updateImmersive() {
        val status = statusBarVisible ?: return
        val nav = navBarVisible ?: return
        val nowImmersive = !status && !nav
        if (nowImmersive == immersive) return
        immersive = nowImmersive
        fire(if (nowImmersive) StateTriggerId.FULL_SCREEN else StateTriggerId.EXIT_FULL_SCREEN)
    }

    private fun fireAppState(packageName: String, focus: AppFocus) {
        val actions = repository.config.appStateTriggers[packageName] ?: return
        val index = when (focus) {
            AppFocus.STARTED -> 0
            AppFocus.STOPPED -> 1
            AppFocus.GAINED -> 2
            AppFocus.LOST -> 3
        }
        actions.getOrNull(index)?.let { executor.run(it, "app $packageName focus=$focus") }
    }

    /** Runs the action bound to a state trigger, if any. */
    private fun fire(triggerId: Int) {
        val action = repository.config.stateTriggers[triggerId] ?: return
        if (action.id == com.edgeflow.core.ActionId.NONE) return
        executor.run(action, "state $triggerId")
    }

    private data class TrackedActivityState(val packageName: String, val state: String)

    private enum class AppFocus { STARTED, STOPPED, GAINED, LOST }

    private companion object {
        val ACTIVE_ACTIVITY_STATES = setOf("STARTED", "RESUMED", "PAUSED")

        const val EXTRA_USER_HANDLE = "android.intent.extra.user_handle"
        const val USER_SWITCH_SENDER_PERMISSION = "android.permission.INTERACT_ACROSS_USERS_FULL"
    }
}

/** Adapts a lambda to the broadcast receiver interface. */
private class SystemStateReceiver(
    private val onReceiveBlock: (Intent) -> Unit,
) : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let(onReceiveBlock)
    }
}

/** Captures the broadcast sender before dispatching its event to the observer. */
private class UserSwitchedReceiver(
    private val onReceiveBlock: (Intent, Int) -> Unit,
) : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val event = intent ?: return
        val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromUid.takeIf { it >= 0 } ?: Binder.getCallingUid()
        } else {
            Binder.getCallingUid()
        }
        onReceiveBlock(event, senderUid)
    }
}
