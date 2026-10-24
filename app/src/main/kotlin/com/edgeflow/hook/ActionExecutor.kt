package com.edgeflow.hook

import android.annotation.SuppressLint

import android.app.ActivityManager
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import com.edgeflow.core.ActionId
import com.edgeflow.core.ActionExecutionBudget
import com.edgeflow.core.AutomationContinuation
import com.edgeflow.core.AutomationLockPolicy
import com.edgeflow.core.BrightnessSteps
import com.edgeflow.core.Ipc
import com.edgeflow.core.UnlockPolicy
import com.edgeflow.core.UnlockSnapshot
import com.edgeflow.core.UnlockDecision
import com.edgeflow.core.GameModePolicy
import com.edgeflow.core.GestureTracePolicy
import com.edgeflow.core.GestureEventPhase
import com.edgeflow.core.GesturePlaybackPlan
import com.edgeflow.core.GesturePlaybackEvent
import com.edgeflow.core.ImmersivePolicy
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.AdjustStream
import com.edgeflow.core.model.EditTextCommand
import com.edgeflow.core.model.MediaCommand
import com.edgeflow.core.model.RingerModeValue
import com.edgeflow.core.model.SlideAdjustTarget
import com.edgeflow.core.model.SuccessiveAdjustTarget
import com.edgeflow.core.SlideAdjustUpdate
import com.edgeflow.core.model.ToggleMode
import com.edgeflow.core.model.VariableOperations
import com.edgeflow.core.model.VariableOperator
import com.edgeflow.hook.input.NativeInputBridge
import com.edgeflow.hook.input.SyntheticEvents
import com.edgeflow.hook.input.TextEditKeyMapping
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * Executes actions.
 *
 * ## Threading
 *
 * Actions are dispatched onto a single background thread. They routinely block
 * — launching an app, running a shell command, sleeping for a `delay` — and the
 * caller is usually the input dispatch thread, which must not be held. A single
 * thread rather than a pool preserves the ordering the user expects from a
 * multi-action: step two runs after step one finishes.
 *
 * ## Failure policy
 *
 * An action that throws is logged and reported, but never aborts the whole
 * sequence: the remaining steps still run, because a partially-applied
 * multi-action is more useful than nothing.
 */
internal class ActionExecutor(
    private val module: XposedModule,
    private val repository: ConfigRepository,
) {

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EdgeFlow-actions").apply { isDaemon = true }
    }

    private fun runSequence(sequence: Action.Sequence, depth: Int) {
        sequence.actions.forEachIndexed { index, child ->
            if (!actionRunActive()) return
            execute(child, depth + 1)
            if (awaitingUnlock) {
                deferredAfterUnlock = AutomationContinuation.remainingAfter(
                    sequence.actions,
                    index,
                    deferredAfterUnlock,
                )
                return
            }
        }
    }

    private fun publishAutomationHistory(
        result: com.edgeflow.core.AutomationRunLog.Snapshot,
        durationMillis: Long,
    ) {
        val ctx = context ?: return
        runCatching {
            ctx.sendBroadcast(
                Intent(Ipc.AUTOMATION_HISTORY)
                    .setClassName("com.edgeflow", "com.edgeflow.service.AutomationHistoryReceiver")
                    .putExtra(Ipc.EXTRA_HISTORY_END_TIME, System.currentTimeMillis())
                    .putExtra(Ipc.EXTRA_HISTORY_DURATION, durationMillis)
                    .putExtra(Ipc.EXTRA_AUTOMATION_STATE, result.state)
                    .putExtra(Ipc.EXTRA_AUTOMATION_ORIGIN, result.origin)
                    .putExtra(Ipc.EXTRA_AUTOMATION_STEPS, result.dispatchedSteps)
                    .putExtra(Ipc.EXTRA_AUTOMATION_FAILURES, result.failedSteps)
                    .putExtra(Ipc.EXTRA_AUTOMATION_RECENT, result.recentSteps),
            )
        }.onFailure { XLog.w("could not persist automation run metadata", it) }
    }

    private fun replayGesture(action: Action.ReplayGesture) {
        val record = repository.config.recordedGestures.firstOrNull { it.id == action.recordingId }
        if (record == null || !GestureTracePolicy.valid(record)) {
            reportUnsupported(ActionId.INJECT_GESTURE, "saved gesture is missing or invalid")
            return
        }
        if (nativeBridge == null) {
            reportUnsupported(ActionId.INJECT_GESTURE, "native input injection is not connected")
            return
        }
        if (action.speedPercent !in GesturePlaybackPlan.MIN_SPEED..GesturePlaybackPlan.MAX_SPEED) {
            reportUnsupported(ActionId.INJECT_GESTURE, "gesture playback speed is invalid")
            return
        }
        val rotation = context?.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: 0
        val delta = if (record.sourceRotation == -1) 0 else
            Math.floorMod(rotation - record.sourceRotation, 4)
        val frames = runCatching {
            GesturePlaybackPlan.create(
                record, action.speedPercent, action.placement, delta,
                action.anchorXPercent, action.anchorYPercent,
            )
        }.getOrElse {
            reportUnsupported(ActionId.INJECT_GESTURE, "cannot plan gesture playback")
            return
        }
        val geometry = context?.let {
            com.edgeflow.hook.gesture.EdgeGeometryFactory.fromDisplay(it, repository.config.settings)
        } ?: com.edgeflow.hook.gesture.EdgeGeometryFactory.fromSystem(repository.config.settings)
        val start = SystemClock.uptimeMillis()
        var strokeDownTime = start
        var active: GesturePlaybackEvent? = null
        var interrupted = false
        try {
            for (frame in frames) {
                if (!actionRunActive()) { interrupted = true; break }
                val due = start + frame.elapsedMs
                while (actionRunActive()) {
                    val remaining = due - SystemClock.uptimeMillis()
                    if (remaining <= 0) break
                    SystemClock.sleep(remaining.coerceAtMost(16L))
                }
                if (!actionRunActive()) { interrupted = true; break }
                if (frame.phase == GestureEventPhase.DOWN) {
                    strokeDownTime = SystemClock.uptimeMillis()
                }
                val event = SyntheticEvents.touchFrame(
                    frame, strokeDownTime, SystemClock.uptimeMillis(),
                    geometry.screenWidth, geometry.screenHeight,
                )
                if (!injectInputEvent(event)) {
                    interrupted = true
                    break
                }
                active = when (frame.phase) {
                    GestureEventPhase.UP -> null
                    GestureEventPhase.POINTER_UP -> frame.copy(
                        phase = GestureEventPhase.MOVE,
                        pointers = frame.pointers.filterIndexed { index, _ -> index != frame.changedIndex },
                    )
                    else -> frame
                }
            }
        } finally {
            // On Stop All, rejection or exception, release ALL live pointers.
            // Never leave the dispatcher believing a synthetic finger is held.
            active?.takeIf { it.pointers.isNotEmpty() }?.let { last ->
                injectInputEvent(SyntheticEvents.touchFrame(
                    last, strokeDownTime, SystemClock.uptimeMillis(),
                    geometry.screenWidth, geometry.screenHeight, cancel = true,
                ))
            }
        }
        if (interrupted && actionRunActive()) {
            reportUnsupported(ActionId.INJECT_GESTURE, "gesture replay stopped before completion")
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gesturePointerPublisher = GesturePointerPublisher { context }

    private val cancelled = AtomicBoolean(false)
    private val runGeneration = AtomicLong(0)
    /** Reject repeated triggers instead of growing a potentially unbounded queue. */
    private val runInFlight = AtomicBoolean(false)
    private data class PendingResume(val remaining: Action, val expiresAtElapsed: Long, val origin: String)
    @Volatile private var pendingResume: PendingResume? = null
    /** Only used on the serialized worker thread. */
    private var deferredAfterUnlock: Action? = null
    private var continuationUnsafe = false
    private val activeShell = AtomicReference<Process?>(null)
    /** Worker-thread confinement: block only the current task after an unlock request. */
    private var awaitingUnlock = false
    private var abortCurrent = false
    /** Only accessed by the serialized worker thread. */
    private var runBudget: ActionExecutionBudget? = null

    /** Give an observable acknowledgment that a bound edge gesture matched. */
    fun onGestureRecognized() {
        if (repository.config.settings.vibrateOnTrigger) vibrate(35L)
    }

    private val conditions = ConditionEvaluator(repository)

    private val variables = ConcurrentHashMap<String, String>()

    @Volatile private var context: Context? = null
    private var keepScreenOnWakeLock: PowerManager.WakeLock? = null
    @Volatile private var torchEnabled = false
    @Volatile private var activeScreenFilterAlpha: Int? = null
    private var ownsStatusPolicy = false
    private var ownsNavigationPolicy = false

    /**
     * Runs [action] off the input thread.
     *
     * @param origin human-readable source, used for logging and for the
     *               "stop all" feature to know what to interrupt
     */
    /**
     * True means the worker accepted the request, NOT that the target app
     * performed the requested effect. Busy/invalid tasks are rejected.
     */
    fun run(action: Action, origin: String): Boolean {
        if (action.id == ActionId.NONE) return false
        if (action is Action.StopAll) {
            stopAll()
            return true
        }
        if (!runInFlight.compareAndSet(false, true)) {
            XLog.w("automation trigger ignored: another run is queued or active")
            return false
        }
        val generation = runGeneration.get()
        try {
          worker.execute {
            var startedAtElapsed = 0L
            try {
                // A Stop All request invalidates work queued before it.
                if (generation != runGeneration.get()) return@execute
                startedAtElapsed = SystemClock.elapsedRealtime()
                awaitingUnlock = false
                abortCurrent = false
                cancelled.set(false)
                pendingResume = null
                deferredAfterUnlock = null
                continuationUnsafe = false
                runBudget = ActionExecutionBudget(SystemClock.elapsedRealtime())
                AutomationRuntimeStatus.log.begin(origin)
                // Descriptions can contain typed text or a full shell command.
                XLog.d("run [$origin] actionId=${action.id}")
                execute(action, depth = 0)
            } finally {
                if (runBudget != null) {
                    if (awaitingUnlock && !cancelled.get() && !abortCurrent &&
                        !continuationUnsafe && deferredAfterUnlock != null
                    ) {
                        pendingResume = PendingResume(
                            deferredAfterUnlock!!,
                            SystemClock.elapsedRealtime() + UNLOCK_RESUME_WINDOW_MS,
                            origin.take(72),
                        )
                    }
                    AutomationRuntimeStatus.log.finish(
                        cancelled.get() || awaitingUnlock || abortCurrent ||
                            runBudget?.canContinue(SystemClock.elapsedRealtime()) == false,
                    )
                    publishAutomationHistory(
                        AutomationRuntimeStatus.log.snapshot(),
                        (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0),
                    )
                }
                runBudget = null
                runInFlight.set(false)
            }
          }
        } catch (t: java.util.concurrent.RejectedExecutionException) {
            runInFlight.set(false)
            XLog.w("automation worker could not accept request")
            return false
        }
        return true
    }

    /** Stops active work and invalidates queued work, including long delays/repeats. */
    fun stopAll() {
        runGeneration.incrementAndGet()
        cancelled.set(true)
        pendingResume = null
        activeShell.get()?.let { process ->
            runCatching { process.destroy() }
        }
    }

    /**
     * Only an app-UID verified broadcast from the user-visible unlock screen
     * may request continuation. Never run solely because USER_PRESENT fired.
     */
    fun resumeAfterUserUnlock(): Boolean {
        val paused = pendingResume ?: return false
        if (SystemClock.elapsedRealtime() > paused.expiresAtElapsed) {
            pendingResume = null
            return false
        }
        val isUnlocked = runCatching {
            val keyguard = context?.getSystemService(KeyguardManager::class.java)
                ?: return@runCatching false
            !keyguard.isDeviceLocked && !keyguard.isKeyguardLocked
        }.getOrDefault(false)
        if (!isUnlocked || runInFlight.get()) return false
        pendingResume = null
        run(paused.remaining, "resume after unlock: ${paused.origin}")
        return true
    }

    fun setContext(context: Context) {
        this.context = context
        conditions.attachContext(context)
    }

    /** Queues a coalesced frame; broadcast IPC is performed away from the input thread. */
    fun updateGesturePointer(
        visible: Boolean,
        x: Float,
        y: Float,
        settings: AppSettings = repository.config.settings,
        immediate: Boolean = false,
    ) {
        gesturePointerPublisher.publish(visible, x, y, settings, immediate)
    }

    fun shutdownGesturePointerPublisher() {
        gesturePointerPublisher.shutdown(repository.config.settings)
    }

    fun variables(): Map<String, String> = variables.toMap()

    private fun execute(action: Action, depth: Int) {
        if (depth > MAX_DEPTH) {
            XLog.w("action nesting deeper than $MAX_DEPTH; aborting to avoid a runaway sequence")
            reportUnsupported(action.id, "maximum nesting depth exceeded")
            return
        }
        if (!actionRunActive()) return
        val lockState = runCatching {
            val ctx = context ?: error("no system context")
            val keyguard = ctx.getSystemService(KeyguardManager::class.java)
                ?: error("no keyguard service")
            keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        }.getOrElse {
            reportUnsupported(action.id, "device lock status unavailable; action blocked")
            return
        }
        if (!AutomationLockPolicy.mayDispatch(action, lockState)) {
            reportUnsupported(action.id, "device is locked; unlock using Android first")
            return
        }
        if (runBudget?.tryEnterStep(SystemClock.elapsedRealtime()) == false) {
            abortCurrent = true
            AutomationRuntimeStatus.log.fail(action.id, "execution budget exceeded")
            XLog.w("automation budget exceeded; stopped to avoid runaway actions")
            return
        }
        AutomationRuntimeStatus.log.dispatched(action.id)
        try {
            dispatch(action, depth)
        } catch (t: Throwable) {
            abortCurrent = true
            AutomationRuntimeStatus.log.fail(action.id, t.javaClass.simpleName)
            XLog.e("action ${action.id} failed: ${t.javaClass.simpleName}", t)
        }
    }

    private fun dispatch(action: Action, depth: Int) {
        when (action) {
            is Action.Simple -> dispatchSimple(action.id)
            is Action.Toggle -> dispatchToggle(action.id, action.mode)
            is Action.LaunchApp -> launchApp(action.packageName, action.root)
            is Action.LaunchActivity -> launchActivity(action)
            is Action.Shell -> runShell(action.command, action.asRoot)
            is Action.Delay -> delayWhileRunning(action.millis)
            is Action.InputText -> injectText(action.text)
            is Action.EditText -> sendTextEditCommand(action.command)
            is Action.InjectKey -> injectKey(action.keyCode, action.metaState)
            is Action.InjectTap -> injectTap(action.x, action.y)
            is Action.ReplayGesture -> replayGesture(action)
            is Action.Toast -> showToast(action.text)
            is Action.Notify -> postNotification(action.title, action.text)
            is Action.Speech -> speak(action.text)
            is Action.PlaySound -> playSound(action.uri)
            is Action.Vibrate -> vibrate(action.millis)
            is Action.RingerMode -> setRingerMode(action.mode)
            is Action.Adjust -> adjust(action.stream, action.delta)
            is Action.MediaControl -> sendMediaControl(action.command)
            is Action.SuccessiveAdjust -> applySuccessiveAdjust(action)
            is Action.SlideAdjust -> reportUnsupported(action.id, "this action requires an active bound swipe gesture")
            is Action.FloatingWidget -> showFloatingWidget(action)
            is Action.ShowPie -> showPie(action.edge)
            is Action.ShowSideBar -> showSideBar(action.edge)
            is Action.ShowPanel -> showPanel(action.panelIndex)
            is Action.ShowShortcutPanel -> showShortcutPanel(action.edge)
            is Action.SavedMultiAction -> runSaved(action.slot, depth)
            is Action.KillApp -> killApp(action.packageName)
            is Action.SetVariable -> setVariable(action.name, action.value, action.operator)
            is Action.Sequence -> runSequence(action, depth)
            is Action.Conditional -> {
                val branch = if (conditions.evaluate(action.condition, variables)) action.thenAction else action.elseAction
                execute(branch, depth + 1)
            }
            is Action.EditableAction -> execute(action.innerAction, depth + 1)
            is Action.Repeat -> repeatAction(action, depth)
            is Action.Abort -> abortCurrent = true
            is Action.StopAll -> stopAll()
            is Action.SubGestures -> {
                reportUnsupported(
                    action.id,
                    "second-stage gesture recognition is not implemented yet",
                )
                showToast("Sub gestures are not supported yet")
            }
            is Action.Special -> sendSpecial(action)
        }
    }

    /**
     * Runs the switch-style actions that need no parameters.
     *
     * Each case delegates to a dedicated private method; the `when` stays a
     * flat dispatch table so it is obvious at a glance which ids are handled.
     */
    private fun dispatchSimple(id: Int) {
        when (id) {
            ActionId.DEFAULT, ActionId.NONE -> Unit
            ActionId.BACK -> injectKey(KeyEvent.KEYCODE_BACK)
            ActionId.HOME -> injectKey(KeyEvent.KEYCODE_HOME)
            ActionId.RECENT_APPS -> injectKey(KeyEvent.KEYCODE_APP_SWITCH)
            ActionId.MENU -> injectKey(KeyEvent.KEYCODE_MENU)
            ActionId.SLEEP_WAKE -> sleepWake()
            ActionId.MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionId.MEDIA_STOP -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
            ActionId.MEDIA_PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionId.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            // Older configs stored Media Control as an unparameterized ID.
            ActionId.MEDIA_CONTROL -> sendMediaControl(MediaCommand.PLAY_PAUSE)
            ActionId.BRIGHTNESS_UP -> adjustBrightness(increase = true, actionId = ActionId.BRIGHTNESS_UP)
            ActionId.BRIGHTNESS_DOWN -> adjustBrightness(increase = false, actionId = ActionId.BRIGHTNESS_DOWN)
            ActionId.EXPAND_NOTIFICATIONS -> expandPanel(notifications = true)
            ActionId.EXPAND_QUICK_SETTINGS -> expandPanel(notifications = false)
            ActionId.SCREENSHOT_FULL -> takeScreenshot()
            ActionId.SCREENSHOT_REGION -> requestOverlay(Ipc.KIND_SCREENSHOT_REGION)
            ActionId.REBOOT -> rebootDevice(soft = false)
            ActionId.SOFT_REBOOT -> rebootDevice(soft = true)
            ActionId.SHUTDOWN -> shutdownDevice()
            ActionId.LAST_APP, ActionId.PREVIOUS_APP -> switchToPreviousApp()
            ActionId.NEXT_APP -> switchToNextApp()
            ActionId.KILL_FOREGROUND_APP -> killForegroundApp()
            ActionId.CLEAR_BACKGROUND_APPS -> clearBackgroundApps()
            ActionId.POWER_MENU -> showPowerMenu()
            ActionId.ROTATE_SCREEN -> rotateScreen()
            ActionId.DISMISS_KEYGUARD -> dismissKeyguard()
            ActionId.SOFT_KEYBOARD -> toggleSoftKeyboard()
            ActionId.GAME_MODE -> {
                val enabled = !(RuntimeToggles.gameMode ?: repository.config.settings.gameMode)
                changeGameMode(enabled)
            }
            ActionId.SPLIT_SCREEN -> startActivity("android.intent.action.MAIN", SPLIT_SCREEN_CATEGORY)
            ActionId.READ_CLIPBOARD -> readClipboard()
            ActionId.CLIPBOARD -> requestOverlay(Ipc.KIND_CLIPBOARD_HISTORY)
            ActionId.UNIVERSAL_COPY -> requestOverlay(Ipc.KIND_UNIVERSAL_COPY)
            ActionId.FAST_SCROLL -> fastScroll()
            ActionId.APPS_DRAWER -> requestOverlay(Ipc.KIND_APPS)
            ActionId.RUNNING_APPS_DRAWER -> showRunningApps()
            ActionId.FREEZER_DRAWER -> showFreezerDrawer()
            ActionId.SHORTCUTS_PANEL -> requestOverlay(Ipc.KIND_SHORTCUT_PANEL, edge = 0)
            ActionId.APP_OPTION_MENU -> showAppOptionMenu()
            ActionId.REFREEZE -> refreezeApps()
            ActionId.FINISH_ACTIVITY -> finishCurrentActivity()
            ActionId.PEN -> requestOverlay(Ipc.KIND_PEN)
            ActionId.EDIT_TEXT -> reportUnsupported(id, "legacy edit action has no selected operation")
            // Legacy id-only entries contain no nested action to dispatch.
            ActionId.EDITABLE_ACTION -> reportUnsupported(id, "legacy editable action has no wrapped action data")
            ActionId.FLOATING_WIDGET -> reportUnsupported(id, "the legacy action has no selected app widget")
            ActionId.GESTURE_POINTER -> reportUnsupported(id, "this action requires an active edge-gesture event")
            ActionId.ADJUST, ActionId.SLIDE_ADJUST, ActionId.SUCCESSIVE_ADJUST ->
                reportUnsupported(id, "this action has no runtime handler yet")
            else -> XLog.w("action $id has no handler; ignoring")
        }
    }

    private fun dispatchToggle(id: Int, mode: ToggleMode) {
        val requested = when (mode) {
            ToggleMode.ON -> true
            ToggleMode.OFF -> false
            ToggleMode.TOGGLE -> null
        }
        val ctx = context ?: run {
            reportUnsupported(id, "system context is unavailable")
            return
        }
        XLog.d("toggle ${com.edgeflow.core.ActionCatalog.title(id)} mode=$mode")

        when (id) {
            ActionId.WIFI -> setWifiEnabled(ctx, requested)
            ActionId.BLUETOOTH -> setBluetoothEnabled(ctx, requested)
            ActionId.NFC -> setNfcEnabled(ctx, requested)
            ActionId.MOBILE_DATA -> setMobileDataEnabled(ctx, requested)
            ActionId.WIFI_AP -> setHotspotEnabled(ctx, requested)
            ActionId.SYNC -> setSyncEnabled(requested)
            ActionId.AUTO_ROTATION -> setSystemSetting("accelerometer_rotation", requested)
            ActionId.AUTO_BRIGHTNESS -> setSystemSetting("screen_brightness_mode", requested)
            ActionId.AIRPLANE_MODE -> setAirplaneMode(ctx, requested)
            ActionId.LOCATION -> setLocationMode(requested)
            ActionId.TORCH -> setTorchEnabled(ctx, requested)
            ActionId.KEEP_SCREEN_ON -> setKeepScreenOn(requested)
            ActionId.GESTURE_CONTROL -> {
                val enabled = resolveToggle(
                    requested,
                    RuntimeToggles.gesturesEnabled ?: repository.config.settings.gestureControlEnabled,
                )
                RuntimeToggles.gesturesEnabled = enabled
                if (!enabled) {
                    updateGesturePointer(visible = false, x = 0f, y = 0f, immediate = true)
                }
            }
            ActionId.KEY_CONTROL -> RuntimeToggles.keysEnabled = resolveToggle(
                requested,
                RuntimeToggles.keysEnabled ?: repository.config.settings.keyControlEnabled,
            )
            ActionId.SCREEN_FILTER -> setScreenFilterEnabled(requested)
            ActionId.GAME_MODE -> {
                val previous = RuntimeToggles.gameMode ?: repository.config.settings.gameMode
                changeGameMode(com.edgeflow.core.GameModePolicy.nextEnabled(requested, previous))
            }
            ActionId.GESTURE_RECORD -> requestOverlay(
                if (requested == false) Ipc.KIND_GESTURE_RECORD_STOP else Ipc.KIND_GESTURE_RECORD,
            )
            ActionId.IMMERSIVE_STATUS_BAR -> setLegacyImmersive(ctx, ImmersivePolicy.Bar.STATUS, requested)
            ActionId.IMMERSIVE_NAV_BAR -> setLegacyImmersive(ctx, ImmersivePolicy.Bar.NAVIGATION, requested)
            else -> reportUnsupported(id, "toggle is not registered")
        }
    }

    private fun resolveToggle(requested: Boolean?, current: Boolean): Boolean = requested ?: !current

    /**
     * Immediately update input guarding, then request the app persist the same
     * value. The app config Flow drives the floating bar and settings UI. A
     * config reload clears the temporary input-process override.
     */
    private fun changeGameMode(enabled: Boolean) {
        val ctx = context ?: run {
            reportUnsupported(ActionId.GAME_MODE, "system context is unavailable")
            return
        }
        RuntimeToggles.gameMode = enabled
        runCatching {
            ctx.sendBroadcast(
                Intent(Ipc.GAME_MODE_CHANGED)
                    .setClassName("com.edgeflow", "com.edgeflow.service.GameModeSyncReceiver")
                    .putExtra(Ipc.EXTRA_GAME_MODE_ENABLED, enabled),
            )
        }.onFailure {
            reportUnsupported(ActionId.GAME_MODE, "unable to synchronize Game Mode with the app")
        }
    }

    private fun setLegacyImmersive(ctx: Context, bar: ImmersivePolicy.Bar, requested: Boolean?) {
        if (android.os.Build.VERSION.SDK_INT !in 28..29) {
            reportUnsupported(
                if (bar == ImmersivePolicy.Bar.STATUS) ActionId.IMMERSIVE_STATUS_BAR
                else ActionId.IMMERSIVE_NAV_BAR,
                "Global policy_control only works on Android 9/10. Android 11+ requires a separately validated InsetsPolicy implementation.",
            )
            return
        }
        val resolver = ctx.contentResolver
        val marker = when (bar) {
            ImmersivePolicy.Bar.STATUS -> "edgeflow_immersive_status_owned"
            ImmersivePolicy.Bar.NAVIGATION -> "edgeflow_immersive_nav_owned"
        }
        // This marker survives system_server/module recreation. Without it a
        // previously hidden navigation bar could never be restored by OFF.
        val persistentOwnership = runCatching {
            android.provider.Settings.Global.getInt(resolver, marker, 0) == 1
        }.getOrDefault(false)
        val owned = persistentOwnership ||
            if (bar == ImmersivePolicy.Bar.STATUS) ownsStatusPolicy else ownsNavigationPolicy
        val enabled = resolveToggle(requested, owned)
        if (enabled == owned) return
        val current = android.provider.Settings.Global.getString(resolver, "policy_control")
        val next = ImmersivePolicy.update(current, bar, enabled)
        if (next == null) {
            XLog.w("Refusing to replace an existing immersive policy")
            return
        }
        val wrote = runCatching {
            android.provider.Settings.Global.putString(resolver, "policy_control", next)
        }.getOrDefault(false)
        if (!wrote) {
            XLog.w("Android rejected immersive policy update")
            return
        }
        val marked = runCatching {
            android.provider.Settings.Global.putInt(resolver, marker, if (enabled) 1 else 0)
        }.getOrDefault(false)
        if (!marked) {
            // Never apply a persistent system policy without also keeping the
            // information required to undo it after a system_server restart.
            runCatching { android.provider.Settings.Global.putString(resolver, "policy_control", current) }
            XLog.w("Could not persist immersive ownership; restoring previous policy")
            return
        }
        if (bar == ImmersivePolicy.Bar.STATUS) ownsStatusPolicy = enabled
        else ownsNavigationPolicy = enabled
    }

    private fun setScreenFilterEnabled(requested: Boolean?) {
        val active = activeScreenFilterAlpha
        val enabled = requested ?: (active == null || active == 0)
        val configuredAlpha = repository.config.settings.screenFilterAlpha
        val alpha = if (enabled) {
            active?.takeIf { it > 0 } ?: configuredAlpha.takeIf { it > 0 } ?: DEFAULT_SCREEN_FILTER_ALPHA
        } else {
            0
        }
        updateScreenFilterAlpha(alpha)
    }

    private fun adjustScreenFilterAlpha(increase: Boolean) {
        val current = activeScreenFilterAlpha ?: repository.config.settings.screenFilterAlpha
        updateScreenFilterAlpha((current + if (increase) 1 else -1).coerceIn(0, MAX_SCREEN_FILTER_ALPHA))
    }

    private fun updateScreenFilterAlpha(alpha: Int) {
        val value = alpha.coerceIn(0, MAX_SAFE_SCREEN_FILTER_ALPHA)
        activeScreenFilterAlpha = value
        requestOverlay(Ipc.KIND_SCREEN_FILTER, filterAlpha = value)
    }

    private fun adjustBrightness(increase: Boolean, actionId: Int) {
        val ctx = context ?: run {
            reportUnsupported(actionId, "system context is unavailable")
            return
        }
        val resolver = ctx.contentResolver
        runCatching {
            val minimumResource = ctx.resources.getIdentifier(
                "config_screenBrightnessSettingMinimum",
                "integer",
                "android",
            )
            val fallbackMinimum = if (android.os.Build.VERSION.SDK_INT >= 24) 1 else 10
            val minimum = (if (minimumResource == 0) fallbackMinimum else ctx.resources.getInteger(minimumResource))
                .coerceIn(0, MAX_BRIGHTNESS - 1)
            val current = android.provider.Settings.System.getInt(
                resolver,
                "screen_brightness",
                (minimum + MAX_BRIGHTNESS) / 2,
            ).coerceIn(minimum, MAX_BRIGHTNESS)
            val next = BrightnessSteps.nextValue(current, minimum, MAX_BRIGHTNESS, increase) ?: return
            android.provider.Settings.System.putInt(resolver, "screen_brightness_mode", 0)
            if (!android.provider.Settings.System.putInt(resolver, "screen_brightness", next)) {
                reportUnsupported(actionId, "the system rejected the brightness change")
            }
        }.onFailure { XLog.w("brightness adjustment failed: ${it.message}") }
    }

    private fun applySuccessiveAdjust(action: Action.SuccessiveAdjust) {
        when (action.target) {
            SuccessiveAdjustTarget.SCREEN_FILTER_ALPHA -> adjustScreenFilterAlpha(action.increase)
            SuccessiveAdjustTarget.BRIGHTNESS -> adjustBrightness(action.increase, action.id)
            SuccessiveAdjustTarget.VOLUME -> adjust(action.stream, if (action.increase) 1 else -1)
            SuccessiveAdjustTarget.SCROLL -> scrollOnce(action.increase)
            SuccessiveAdjustTarget.CARET -> injectKey(
                if (action.increase) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT,
            )
        }
    }

    fun startSlideAdjust(action: Action.SlideAdjust, progress: Float) {
        worker.execute {
            requestOverlay(
                Ipc.KIND_SLIDE_ADJUST,
                slideProgress = progress,
                slideLabel = com.edgeflow.core.ActionCatalog.describe(action),
            )
        }
    }

    fun updateSlideAdjust(action: Action.SlideAdjust, update: SlideAdjustUpdate) {
        if (update.deltaSteps == 0) return
        worker.execute {
            val increase = update.deltaSteps > 0
            repeat(update.deltaSteps.absoluteValue.coerceAtMost(MAX_SLIDE_ADJUST_STEPS * 2)) {
                applySlideAdjustStep(action, increase)
            }
            requestOverlay(
                Ipc.KIND_SLIDE_ADJUST,
                slideProgress = update.progress,
                slideLabel = com.edgeflow.core.ActionCatalog.describe(action),
            )
        }
    }

    fun endSlideAdjust() {
        worker.execute {
            requestOverlay(Ipc.KIND_SLIDE_ADJUST, slideProgress = SLIDE_ADJUST_CLOSED)
        }
    }

    private fun applySlideAdjustStep(action: Action.SlideAdjust, increase: Boolean) {
        when (action.target) {
            SlideAdjustTarget.SCREEN_FILTER_ALPHA -> adjustScreenFilterAlpha(increase)
            SlideAdjustTarget.BRIGHTNESS -> adjustBrightness(increase, action.id)
            SlideAdjustTarget.VOLUME -> adjust(action.stream, if (increase) 1 else -1)
            SlideAdjustTarget.CARET -> injectKey(
                if (increase) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT,
            )
        }
    }

    private fun scrollOnce(scrollUp: Boolean) {
        val ctx = context ?: return
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val speed = repository.config.settings.scrollSpeedPercent.coerceIn(1, MAX_SCROLL_SPEED_PERCENT) / 100f
        val axis = (if (scrollUp) 3f else -3f) * speed
        injectInputEvent(SyntheticEvents.scroll(metrics.widthPixels / 2f, metrics.heightPixels / 2f, axis))
    }

    private fun setWifiEnabled(ctx: Context, requested: Boolean?) {
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
        val enabled = resolveToggle(requested, wifi.isWifiEnabled)
        if (wifi.isWifiEnabled != enabled && !wifi.setWifiEnabled(enabled)) {
            reportUnsupported(ActionId.WIFI, "the Wi-Fi service rejected the change")
        }
    }

    private fun setBluetoothEnabled(ctx: Context, requested: Boolean?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            reportUnsupported(ActionId.BLUETOOTH, "Bluetooth permission is missing")
            return
        }
        val manager = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)
        val adapter = manager?.adapter ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            reportUnsupported(ActionId.BLUETOOTH, "Bluetooth is not available")
            return
        }
        try {
            val enabled = resolveToggle(requested, adapter.isEnabled)
            val accepted = if (enabled) adapter.enable() else adapter.disable()
            if (!accepted) reportUnsupported(ActionId.BLUETOOTH, "the Bluetooth service rejected the change")
        } catch (t: SecurityException) {
            reportUnsupported(ActionId.BLUETOOTH, "the system denied Bluetooth access")
        }
    }

    private fun setNfcEnabled(ctx: Context, requested: Boolean?) {
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(ctx)
        if (adapter == null) {
            reportUnsupported(ActionId.NFC, "NFC is not available")
            return
        }
        val enabled = resolveToggle(requested, adapter.isEnabled)
        val result = Reflect.callMethod(adapter, if (enabled) "enable" else "disable")
        if (result != true) reportUnsupported(ActionId.NFC, "the NFC service rejected the change")
    }

    // The executor exists only inside the privileged system_server process.
    // Permission is explicitly checked at runtime; an ordinary APK UID cannot
    // be granted MODIFY_PHONE_STATE and must never take this path.
    @SuppressLint("MissingPermission")
    private fun setMobileDataEnabled(ctx: Context, requested: Boolean?) {
        val telephony = ctx.getSystemService(android.telephony.TelephonyManager::class.java) ?: return
        // Only system_server with platform-granted MODIFY_PHONE_STATE may use
        // this path; never bypass the platform permission check as an app.
        if (ctx.checkSelfPermission("android.permission.MODIFY_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) {
            reportUnsupported(ActionId.MOBILE_DATA, "mobile data control requires privileged permission")
            return
        }
        try {
            val enabled = resolveToggle(requested, telephony.isDataEnabled)
            telephony.setDataEnabled(enabled)
        } catch (t: SecurityException) {
            reportUnsupported(ActionId.MOBILE_DATA, "mobile data permission denied")
        } catch (t: RuntimeException) {
            XLog.w("mobile data toggle failed: ${t.message}")
        }
    }

    private fun setHotspotEnabled(ctx: Context, requested: Boolean?) {
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
        val current = Reflect.callMethod(wifi, "isWifiApEnabled") as? Boolean ?: false
        val enabled = resolveToggle(requested, current)
        val method = if (enabled) {
            Reflect.methodByArity(wifi.javaClass, "startTethering", 4)
                ?: Reflect.methodByArity(wifi.javaClass, "startTethering", 3)
        } else {
            Reflect.methodByArity(wifi.javaClass, "stopTethering", 1)
        }
        if (method == null) {
            reportUnsupported(ActionId.WIFI_AP, "the hotspot service API is unavailable on this release")
            return
        }
        runCatching {
            when (method.parameterCount) {
                4 -> method.invoke(wifi, 0, false, null, mainHandler)
                3 -> method.invoke(wifi, 0, false, null)
                1 -> method.invoke(wifi, 0)
            }
        }.onFailure { XLog.w("hotspot toggle failed: ${it.message}") }
    }

    private fun setSyncEnabled(requested: Boolean?) {
        val current = android.content.ContentResolver.getMasterSyncAutomatically()
        android.content.ContentResolver.setMasterSyncAutomatically(resolveToggle(requested, current))
    }

    private fun setAirplaneMode(ctx: Context, requested: Boolean?) {
        val current = android.provider.Settings.Global.getInt(ctx.contentResolver, "airplane_mode_on", 0) != 0
        val enabled = resolveToggle(requested, current)
        android.provider.Settings.Global.putInt(ctx.contentResolver, "airplane_mode_on", if (enabled) 1 else 0)
        ctx.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled))
    }

    private fun setTorchEnabled(ctx: Context, requested: Boolean?) {
        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (cameraId == null) {
            reportUnsupported(ActionId.TORCH, "no camera with a flash was found")
            return
        }
        val current = torchEnabled
        val enabled = resolveToggle(requested, current)
        runCatching { manager.setTorchMode(cameraId, enabled) }
            .onFailure { XLog.w("torch toggle failed: ${it.message}") }
        torchEnabled = enabled
    }

    private fun setKeepScreenOn(requested: Boolean?) {
        val power = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val enabled = resolveToggle(requested, keepScreenOnWakeLock?.isHeld == true)
        if (enabled) {
            val lock = keepScreenOnWakeLock ?: power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeFlow:KeepScreenOn")
                .also { keepScreenOnWakeLock = it }
            if (!lock.isHeld) lock.acquire()
        } else {
            keepScreenOnWakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun repeatAction(action: Action.Repeat, depth: Int) {
        if (action.actions.isEmpty()) {
            XLog.w("ignored empty repeat to prevent busy loop")
            return
        }
        if (action.times == 0) {
            while (actionRunActive()) {
                action.actions.forEach { if (actionRunActive()) execute(it, depth + 1) }
                if (awaitingUnlock) {
                    continuationUnsafe = true
                    return
                }
            }
        } else {
            repeat(action.times) {
                if (!actionRunActive()) return
                action.actions.forEach { if (actionRunActive()) execute(it, depth + 1) }
                if (awaitingUnlock) {
                    continuationUnsafe = true
                    return
                }
            }
        }
    }

    private fun actionRunActive(): Boolean =
        !cancelled.get() && !awaitingUnlock && !abortCurrent &&
            (runBudget?.canContinue(SystemClock.elapsedRealtime()) != false)

    /** Maximum stop latency for a delay is one 50 ms slice. */
    private fun delayWhileRunning(millis: Long) {
        val started = SystemClock.elapsedRealtime()
        while (actionRunActive()) {
            val remaining = millis - (SystemClock.elapsedRealtime() - started)
            if (remaining <= 0L) break
            val budgetRemaining = runBudget?.remainingMillis(SystemClock.elapsedRealtime()) ?: remaining
            if (budgetRemaining <= 0L) break
            SystemClock.sleep(minOf(remaining, budgetRemaining, 50L))
        }
    }

    private fun runSaved(slot: Int, depth: Int) {
        val saved = repository.config.savedMultiActions.getOrNull(slot)
            ?.takeUnless { it.deleted || it.action.id == ActionId.NONE } ?: run {
            XLog.w("saved action $slot does not exist")
            return
        }
        execute(saved.action, depth + 1)
    }

    private fun setVariable(name: String, value: String, operator: VariableOperator) {
        if (name.isBlank()) return
        val updated = VariableOperations.apply(variables[name], value, operator) ?: run {
            XLog.w("could not apply $operator to variable '$name'; values must be numeric and divisor must be nonzero")
            return
        }
        variables[name] = updated
    }

    // ---------------------------------------------------------------- helpers

    private fun sendTextEditCommand(command: EditTextCommand) {
        val stroke = TextEditKeyMapping.forCommand(command)
        injectKey(stroke.keyCode, stroke.metaState)
    }

    private fun injectKey(keyCode: Int, metaState: Int = 0, doubleTap: Boolean = false) {
        sendKeyStroke(keyCode, metaState)
        if (doubleTap) {
            SystemClock.sleep(SyntheticEvents.TAP_HOLD_MILLIS)
            sendKeyStroke(keyCode, metaState)
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        sendKeyStroke(keyCode, 0)
    }

    private fun sendMediaControl(command: MediaCommand) {
        val keyCode = when (command) {
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
        }
        sendMediaKey(keyCode)
    }

    /** Keep DOWN and UP in one coherent key stream, including identical downTime. */
    private fun sendKeyStroke(keyCode: Int, metaState: Int) {
        val downTime = SystemClock.uptimeMillis()
        if (!sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, metaState, downTime)) return
        SystemClock.sleep(20L)
        sendKeyEvent(KeyEvent.ACTION_UP, keyCode, metaState, downTime)
    }

    private fun sendKeyEvent(action: Int, keyCode: Int, metaState: Int, downTime: Long): Boolean =
        injectInputEvent(SyntheticEvents.key(action, keyCode, metaState, downTime))

    private fun injectTap(x: Int, y: Int) {
        val geometry = context?.let {
            com.edgeflow.hook.gesture.EdgeGeometryFactory.fromDisplay(it, repository.config.settings)
        } ?: com.edgeflow.hook.gesture.EdgeGeometryFactory.fromSystem(repository.config.settings)
        if (x !in 0 until geometry.screenWidth || y !in 0 until geometry.screenHeight) {
            reportUnsupported(ActionId.INJECT_GESTURE, "tap coordinates are outside the primary display")
            return
        }
        val down = SystemClock.uptimeMillis()
        val accepted = injectInputEvent(
            SyntheticEvents.touch(down, down, android.view.MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat()),
        )
        if (!accepted) return
        // A real tap has a held phase. Never send an UP with an eventTime in
        // the future or emit an UP after a rejected DOWN.
        SystemClock.sleep(SyntheticEvents.TAP_HOLD_MILLIS)
        injectInputEvent(
            SyntheticEvents.touch(
                down,
                SystemClock.uptimeMillis(),
                if (actionRunActive()) android.view.MotionEvent.ACTION_UP
                else android.view.MotionEvent.ACTION_CANCEL,
                x.toFloat(),
                y.toFloat(),
            ),
        )
    }

    private fun readClipboard() {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }
            .getOrNull()
        if (text.isNullOrBlank()) {
            XLog.d("clipboard is empty or does not contain text")
            return
        }
        showToast(text)
    }

    private fun fastScroll() {
        val ctx = context ?: return
        val display = ctx.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.defaultDisplay.getRealMetrics(metrics)
        val x = metrics.widthPixels / 2f
        val y = metrics.heightPixels / 2f
        repeat(8) {
            injectInputEvent(SyntheticEvents.scroll(x, y, verticalAxis = -3f))
        }
    }

    private fun injectText(text: String) {
        if (text.isEmpty()) return
        val characters = SyntheticEvents.textEvents(text) ?: run {
            reportUnsupported(ActionId.INJECT_TEXT, "text is not representable on the current keyboard layout")
            return
        }
        characters.forEach { event ->
            if (!actionRunActive()) return
            if (!injectInputEvent(SyntheticEvents.restamp(event))) return
        }
    }

    private fun injectInputEvent(event: android.view.InputEvent): Boolean {
        val actionId = if (event is KeyEvent) ActionId.INJECT_KEY_EVENT else ActionId.INJECT_GESTURE
        try {
            val bridge = nativeBridge ?: run {
                InputRuntimeStatus.injectionResult(1)
                reportUnsupported(actionId, "native input bridge is unavailable")
                return false
            }
            val result = bridge.inject(event, INJECT_MODE_ASYNC, 0)
            if (result != NativeInputBridge.INJECT_SUCCEEDED) {
                reportUnsupported(actionId, "native input dispatcher rejected event (code $result)")
            }
            return result == NativeInputBridge.INJECT_SUCCEEDED
        } finally {
            // Synthetic MotionEvent.obtain() allocates native resources.
            (event as? android.view.MotionEvent)?.recycle()
        }
    }

    private var nativeBridge: NativeInputBridge? = null

    fun attachNativeBridge(bridge: NativeInputBridge?) {
        nativeBridge = bridge
    }

    private fun rebootDevice(soft: Boolean) {
        val power = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val reason = if (soft) "userspace" else null
        runCatching { Reflect.callMethod(power, "reboot", reason) }
            .onFailure { XLog.w("${if (soft) "soft " else ""}reboot failed: ${it.message}") }
    }

    private fun shutdownDevice() {
        val power = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val shutdown = Reflect.methodByArity(power.javaClass, "shutdown", 3)
        runCatching {
            if (shutdown != null) shutdown.invoke(power, false, "userrequested", false)
            else Reflect.callMethod(power, "reboot", "shutdown")
        }.onFailure { XLog.w("shutdown failed: ${it.message}") }
    }

    private fun sleepWake() {
        val power = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (power.isInteractive) {
            Reflect.callMethod(power, "goToSleep", SystemClock.uptimeMillis())
        } else {
            Reflect.callMethod(power, "wakeUp", SystemClock.uptimeMillis())
        }
    }

    private fun adjust(stream: AdjustStream, delta: Int) {
        val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val audioStream = when (stream) {
            AdjustStream.VOICE_CALL -> AudioManager.STREAM_VOICE_CALL
            AdjustStream.SYSTEM -> AudioManager.STREAM_SYSTEM
            AdjustStream.RING -> AudioManager.STREAM_RING
            AdjustStream.MUSIC -> AudioManager.STREAM_MUSIC
            AdjustStream.ALARM -> AudioManager.STREAM_ALARM
            AdjustStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            AdjustStream.BLUETOOTH_SCO -> STREAM_BLUETOOTH_SCO
            AdjustStream.DTMF -> AudioManager.STREAM_DTMF
            AdjustStream.TTS -> AudioManager.STREAM_MUSIC
            AdjustStream.DEFAULT, AdjustStream.SYSTEM_ENFORCED -> AudioManager.STREAM_MUSIC
        }
        if (delta == 0) return
        val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val requestedSteps = delta.toLong().absoluteValue
        val steps = requestedSteps.coerceAtMost(MAX_ADJUST_STEPS.toLong()).toInt()
        if (steps.toLong() != requestedSteps) {
            XLog.w("volume adjustment capped at $MAX_ADJUST_STEPS steps")
        }
        repeat(steps) {
            audio.adjustStreamVolume(audioStream, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun setRingerMode(mode: RingerModeValue) {
        val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val target = when (mode) {
            RingerModeValue.SILENT -> AudioManager.RINGER_MODE_SILENT
            RingerModeValue.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerModeValue.NORMAL -> AudioManager.RINGER_MODE_NORMAL
        }
        audio.ringerMode = target
    }

    private fun expandPanel(notifications: Boolean) {
        val ctx = context ?: return
        val service = ctx.getSystemService("statusbar") ?: return
        val method = if (notifications) "expandNotificationsPanel" else "expandSettingsPanel"
        Reflect.callMethod(service, method)
    }

    private fun takeScreenshot() {
        val ctx = context ?: return
        // The platform's own screenshot entry point; requires the
        // STATUS_BAR_SERVICE binder, which system_server has.
        val service = ctx.getSystemService("statusbar") ?: run {
            XLog.w("status bar service unavailable; screenshot not taken")
            return
        }
        Reflect.callMethod(service, "takeScreenshot")
    }

    private fun showPowerMenu() {
        val ctx = context ?: return
        val service = ctx.getSystemService("statusbar") ?: return
        Reflect.callMethod(service, "showGlobalActions")
    }

    private fun rotateScreen() {
        val current = setSystemSettingRead("user_rotation", 0)
        setSystemSetting("user_rotation", (current + 1) % 4)
    }

    private fun dismissKeyguard() {
        val ctx = context ?: run {
            awaitingUnlock = true
            XLog.w("unlock request cannot proceed: system context unavailable")
            return
        }
        val power = ctx.getSystemService(PowerManager::class.java)
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        if (power == null || keyguard == null) {
            awaitingUnlock = true
            XLog.w("unlock request cannot proceed: platform keyguard/power service unavailable")
            return
        }
        fun snapshot() = UnlockSnapshot(
            interactive = power.isInteractive,
            keyguardLocked = keyguard.isKeyguardLocked,
            deviceLocked = keyguard.isDeviceLocked,
            secure = keyguard.isKeyguardSecure,
        )
        val before = snapshot()
        if (UnlockPolicy.classify(before) == UnlockDecision.WAKE_AND_RECHECK) {
            runCatching { Reflect.callMethod(power, "wakeUp", SystemClock.uptimeMillis()) }
                .onFailure { XLog.w("could not wake screen for unlock: ${it.message}") }
        }
        // Waking does not mean unlocking. Block the remainder of the current
        // automation sequence until a future, explicit user action starts it.
        if (!UnlockPolicy.confirmedUnlocked(snapshot())) {
            awaitingUnlock = true
            XLog.i("unlock needs Android keyguard dismissal or user authentication; stopping current action sequence")
            requestOverlay(Ipc.KIND_UNLOCK_REQUEST)
        }
    }

    private fun toggleSoftKeyboard() {
        val ctx = context ?: return
        val service = ctx.getSystemService("input_method") ?: return
        Reflect.callMethod(service, "toggleSoftInputFromWindow", null, 0, 0)
    }

    private fun switchToPreviousApp() {
        val packageName = ForegroundApp.previous ?: run {
            reportUnsupported(ActionId.PREVIOUS_APP, "no previous foreground app has been observed")
            return
        }
        launchApp(packageName)
    }

    private fun switchToNextApp() {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val tasks = runCatching { manager.getRunningTasks(MAX_RECENT_TASKS) }
            .onFailure { XLog.w("could not read recent tasks: ${it.message}") }
            .getOrNull()
            .orEmpty()
        val current = tasks.firstOrNull() ?: run {
            reportUnsupported(ActionId.NEXT_APP, "there are no recent tasks to switch between")
            return
        }
        val candidates = tasks.mapNotNull { task ->
            val packageName = task.baseActivity?.packageName ?: task.topActivity?.packageName
            packageName?.let { RecentTaskCandidate(task.id, it) }
        }
        val currentPackage = current.baseActivity?.packageName ?: current.topActivity?.packageName
        val nextTaskId = RecentTaskNavigator.nextTaskId(
            tasks = candidates,
            currentTaskId = current.id,
            currentPackageName = currentPackage,
            excludedPackages = setOf(ctx.packageName, Ipc.SYSTEM_PACKAGE, "android", "com.android.systemui"),
        ) ?: run {
            reportUnsupported(ActionId.NEXT_APP, "no other eligible recent task is available")
            return
        }
        runCatching { manager.moveTaskToFront(nextTaskId, ActivityManager.MOVE_TASK_WITH_HOME) }
            .onFailure { XLog.w("could not switch to recent task $nextTaskId: ${it.message}") }
    }

    private fun launchApp(packageName: String, asRoot: Boolean = false) {
        val ctx = context ?: return
        if (asRoot) {
            if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
                XLog.w("refusing root launch for invalid package name")
                return
            }
            runShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1", asRoot = true)
            return
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            XLog.w("no launch intent for $packageName")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startIntent(intent)
    }

    private fun launchActivity(action: Action.LaunchActivity) {
        val intent = Intent(Intent.ACTION_MAIN)
        val className = action.className
        if (className != null) {
            intent.setClassName(action.packageName, className)
        } else {
            intent.setPackage(action.packageName)
        }
        action.data?.let { intent.data = android.net.Uri.parse(it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startIntent(intent)
    }

    private fun startActivity(action: String, category: String? = null) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        category?.let { intent.addCategory(it) }
        startIntent(intent)
    }

    /**
     * Starts an intent as the current user.
     *
     * `Context.startActivityAsUser` is hidden, so the user-qualified variant is
     * reached through the activity manager. Falling back to a plain
     * `startActivity` keeps launching apps working on releases where that
     * internal entry point moved.
     */
    private fun startIntent(intent: Intent) {
        val ctx = context ?: return
        try {
            val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE)
            val started = activityManager?.let {
                Reflect.callMethod(it, "startActivityAsUser", ctx, intent, null, -2 /* UserHandle.CURRENT */)
            }
            if (started == null) {
                ctx.startActivity(intent)
            }
        } catch (t: Throwable) {
            XLog.w("could not start ${intent.component ?: intent.action}: ${t.message}")
        }
    }

    private fun stopApp(packageName: String) {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val forceStop = Reflect.method(ActivityManager::class.java, "forceStopPackage", String::class.java)
            ?: run {
                reportUnsupported(ActionId.APP_OPTION_MENU, "force-stop API is unavailable")
                return
            }
        try {
            forceStop.invoke(manager, packageName)
        } catch (t: Throwable) {
            reportUnsupported(ActionId.APP_OPTION_MENU, "force-stop failed: ${t.message}")
        }
    }

    private fun killApp(packageName: String) {
        val ctx = context ?: return
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        try {
            am.killBackgroundProcesses(packageName)
        } catch (t: Throwable) {
            XLog.w("kill $packageName failed: ${t.message}")
        }
    }

    private fun killForegroundApp() {
        // Resolved by the state observer, which tracks the focused package.
        val foreground = ForegroundApp.current ?: return
        killApp(foreground)
    }

    private fun finishCurrentActivity() {
        val token = ForegroundActivity.token
        val taskManager = activityTaskManagerService()
        if (token != null && taskManager != null && ActivityFinishInvoker.finish(taskManager, token, Intent())) {
            return
        }

        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: run {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "activity service is unavailable")
            return
        }
        val currentTask = runCatching { manager.getRunningTasks(1).firstOrNull() }
            .onFailure { XLog.w("could not inspect the current task: ${it.message}") }
            .getOrNull() ?: run {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "there is no current task to finish")
            return
        }
        val taskPackage = currentTask.topActivity?.packageName ?: currentTask.baseActivity?.packageName
        val isProtectedTask = taskPackage == null || taskPackage == ctx.packageName ||
            taskPackage == Ipc.SYSTEM_PACKAGE || runCatching {
                ctx.packageManager.getApplicationInfo(taskPackage, 0).flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            }.getOrDefault(true)
        if (isProtectedTask) {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "the current task is a system, module, or unknown task")
            return
        }
        if (currentTask.numActivities != 1) {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "the current activity token is unavailable; multi-activity task left untouched")
            return
        }
        val activityService = activityManagerService() ?: taskManager ?: run {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "activity task service is unavailable")
            return
        }
        if (!ActivityFinishInvoker.removeSingleActivityTask(activityService, currentTask.id)) {
            reportUnsupported(ActionId.FINISH_ACTIVITY, "the system could not remove the single-activity task")
        }
    }

    private fun activityTaskManagerService(): Any? =
        staticServiceGetter("android.app.ActivityTaskManager") ?: activityManagerService()

    private fun activityManagerService(): Any? = staticServiceGetter("android.app.ActivityManager")

    private fun staticServiceGetter(className: String): Any? {
        val serviceClass = runCatching { Class.forName(className) }.getOrNull() ?: return null
        val getter = serviceClass.declaredMethods.firstOrNull { method ->
            method.name == "getService" && method.parameterCount == 0 &&
                java.lang.reflect.Modifier.isStatic(method.modifiers)
        } ?: return null
        return runCatching {
            getter.isAccessible = true
            getter.invoke(null)
        }.getOrNull()
    }

    private fun clearBackgroundApps() {
        val ctx = context ?: return
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        try {
            am.killBackgroundProcesses("")
        } catch (t: Throwable) {
            XLog.w("clear background apps failed: ${t.message}")
        }
    }

    private fun runShell(command: String, asRoot: Boolean) {
        if (command.isBlank()) return
        val executable = if (asRoot) "su" else "sh"
        var process: Process? = null
        try {
            process = ProcessBuilder(executable, "-c", command)
                .redirectErrorStream(true)
                .start()
            val running = process
            activeShell.set(running)
            // Drain output away from the automation worker without retaining
            // potentially sensitive shell stdout in system logs.
            val drain = Thread({
                runCatching {
                    running.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(2048)
                        while (reader.read(buffer) != -1) {
                            // Drain continuously with bounded memory; no logs of commands.
                        }
                    }
                }
            }, "EdgeFlow-shell-output").apply { isDaemon = true; start() }
            val started = SystemClock.elapsedRealtime()
            while (actionRunActive()) {
                if (running.waitFor(50, TimeUnit.MILLISECONDS)) break
                if (SystemClock.elapsedRealtime() - started >= SHELL_TIMEOUT_MILLIS) {
                    reportUnsupported(ActionId.SHELL, "shell command timed out")
                    break
                }
            }
            if (running.isAlive) {
                running.destroy()
                if (!running.waitFor(500, TimeUnit.MILLISECONDS)) running.destroyForcibly()
                abortCurrent = true
            } else if (running.exitValue() != 0) {
                reportUnsupported(ActionId.SHELL, "shell command returned nonzero exit code")
            }
            drain.join(100L)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            reportUnsupported(ActionId.SHELL, "shell command interrupted")
            XLog.w("shell command interrupted")
        } catch (t: Throwable) {
            XLog.w("shell command failed: ${t.message}")
            reportUnsupported(ActionId.SHELL, "shell execution unavailable")
        } finally {
            process?.let { running ->
                activeShell.compareAndSet(running, null)
                if (running.isAlive) runCatching { running.destroyForcibly() }
                runCatching { running.inputStream.close() }
                runCatching { running.outputStream.close() }
                runCatching { running.errorStream.close() }
            }
        }
    }

    private fun showToast(text: String) {
        if (text.isBlank()) return
        mainHandler.post {
            val ctx = context ?: return@post
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun postNotification(title: String, text: String) {
        val ctx = context ?: return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        try {
            // Channels are package-scoped; create it through the same context that posts the notification.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    android.app.NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        android.app.NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
            val notification = android.app.Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (t: Throwable) {
            XLog.w("notification failed: ${t.message}")
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        XLog.d("speech: $text")
        // Text-to-speech needs a Context and a synthesis engine; the app side
        // performs the actual utterance so the hook does not hold an engine.
        broadcastToApp(
            Ipc.PERFORM_ACTION,
            Intent().setPackage(Ipc.APP_PACKAGE).putExtra("speak", text),
        )
    }

    private fun playSound(uri: String?) {
        mainHandler.post {
            val ctx = context ?: return@post
            try {
                val tone = if (uri != null) android.net.Uri.parse(uri) else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                android.media.RingtoneManager.getRingtone(ctx, tone)?.play()
            } catch (t: Throwable) {
                XLog.w("play sound failed: ${t.message}")
            }
        }
    }

    private fun vibrate(millis: Long) {
        mainHandler.post {
            val ctx = context ?: return@post
            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return@post
            try {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(millis, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (t: Throwable) {
                XLog.w("vibrate failed: ${t.message}")
            }
        }
    }

    private fun showAppOptionMenu() {
        val packageName = ForegroundApp.current ?: run {
            reportUnsupported(ActionId.APP_OPTION_MENU, "the foreground application is not known")
            return
        }
        if (packageName == context?.packageName || packageName == Ipc.SYSTEM_PACKAGE ||
            packageName == "com.android.systemui"
        ) {
            reportUnsupported(ActionId.APP_OPTION_MENU, "the foreground package is not an application target")
            return
        }
        requestOverlay(Ipc.KIND_APP_OPTION_MENU, packageName = packageName)
    }

    fun handleAppOptionMenuSelection(command: Int, packageName: String?) {
        val target = packageName?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (target == ctx.packageName || target == Ipc.SYSTEM_PACKAGE || target == "com.android.systemui") {
            reportUnsupported(ActionId.APP_OPTION_MENU, "rejected a protected package target")
            return
        }
        val installed = runCatching { ctx.packageManager.getApplicationInfo(target, 0) }.getOrNull()
        if (installed == null) {
            reportUnsupported(ActionId.APP_OPTION_MENU, "the selected application is no longer installed")
            return
        }

        when (command) {
            Ipc.APP_OPTION_DISMISS -> Unit
            Ipc.APP_OPTION_OPEN -> launchApp(target)
            Ipc.APP_OPTION_FINISH_ACTIVITY -> {
                if (ForegroundApp.current != target) {
                    reportUnsupported(ActionId.APP_OPTION_MENU, "the selected application is no longer foreground")
                } else {
                    finishCurrentActivity()
                }
            }
            Ipc.APP_OPTION_STOP -> stopApp(target)
            Ipc.APP_OPTION_FREEZE -> freezeApp(target, requireConfigured = false)
            Ipc.APP_OPTION_ACTIVITIES -> requestOverlay(Ipc.KIND_APP_ACTIVITIES, packageName = target)
            Ipc.APP_OPTION_APP_INFO -> {
                val intent = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$target"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startIntent(intent)
            }
            Ipc.APP_OPTION_PLAY_STORE -> {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=$target"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startIntent(intent)
            }
            else -> reportUnsupported(ActionId.APP_OPTION_MENU, "unknown app option $command")
        }
    }

    fun handleAppActivitySelection(packageName: String?, className: String?) {
        val target = packageName?.takeIf(String::isNotBlank) ?: return
        val activityClass = className?.takeIf(String::isNotBlank) ?: return
        val ctx = context ?: return
        if (target == ctx.packageName || target == Ipc.SYSTEM_PACKAGE || target == "com.android.systemui") {
            reportUnsupported(ActionId.APP_OPTION_MENU, "rejected a protected activity target")
            return
        }
        val activityInfo = runCatching {
            ctx.packageManager.getActivityInfo(android.content.ComponentName(target, activityClass), 0)
        }.getOrNull()
        if (activityInfo == null || !activityInfo.exported || activityInfo.packageName != target) {
            reportUnsupported(ActionId.APP_OPTION_MENU, "activity is unavailable or not exported")
            return
        }
        launchActivity(Action.LaunchActivity(target, activityClass))
    }

    private fun showRunningApps() {
        val ctx = context ?: return
        val packages = runCatching {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.getRunningTasks(100)
                .orEmpty()
                .mapNotNull { it.topActivity?.packageName }
                .filter { it != "android" && it != ctx.packageName }
                .distinct()
        }.getOrElse {
            XLog.w("could not read running tasks: ${it.message}")
            emptyList()
        }
        requestOverlay(Ipc.KIND_RUNNING_APPS, packages = packages)
    }

    private fun showFreezerDrawer() {
        requestOverlay(Ipc.KIND_FREEZER, packages = repository.config.freezer.frozen.sorted())
    }

    fun freezeApp(packageName: String) = freezeApp(packageName, requireConfigured = true)

    private fun freezeApp(packageName: String, requireConfigured: Boolean) {
        val ctx = context ?: return
        if (packageName == ctx.packageName || packageName == Ipc.SYSTEM_PACKAGE || packageName == "com.android.systemui") return
        if (requireConfigured && packageName !in repository.config.freezer.frozen) {
            XLog.w("rejected freeze request for unconfigured package $packageName")
            return
        }
        runCatching {
            ctx.packageManager.getApplicationInfo(packageName, 0)
            stopApp(packageName)
            ctx.packageManager.setApplicationEnabledSetting(
                packageName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }.onFailure { XLog.w("could not freeze $packageName: ${it.message}") }
    }

    fun unfreezeApp(packageName: String) {
        val ctx = context ?: return
        if (packageName == ctx.packageName || packageName == Ipc.SYSTEM_PACKAGE) return
        runCatching {
            ctx.packageManager.setApplicationEnabledSetting(
                packageName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }.onSuccess {
            try {
                ctx.sendBroadcast(
                    Intent(Ipc.UNFREEZE_APP_SUCCEEDED)
                        .setPackage(Ipc.APP_PACKAGE)
                        .putExtra(Ipc.EXTRA_PACKAGE, packageName),
                )
            } catch (t: Throwable) {
                XLog.w(
                    "unfroze $packageName but could not notify EdgeFlow to update the freezer list",
                    t,
                )
            }
        }.onFailure {
            XLog.w(
                "could not unfreeze $packageName; it remains in the freezer list: " +
                    (it.message ?: it.javaClass.simpleName),
                it,
            )
            showToast("Could not unfreeze $packageName. It remains in the freezer list.")
        }
    }

    private fun refreezeApps() {
        val packages = repository.config.freezer.frozen
        if (packages.isEmpty()) {
            XLog.d("no apps are configured for refreezing")
            return
        }
        packages.forEach(::freezeApp)
    }

    private fun showFloatingWidget(action: Action.FloatingWidget) {
        if (action.widgetId <= 0) {
            reportUnsupported(ActionId.FLOATING_WIDGET, "the selected app widget ID is invalid")
            return
        }
        requestOverlay(Ipc.KIND_FLOATING_WIDGET, widgetId = action.widgetId, widgetAction = action)
    }

    private fun showPie(edge: Int) = requestOverlay(Ipc.KIND_PIE, edge = edge)

    private fun showSideBar(edge: Int) = requestOverlay(Ipc.KIND_SIDE_BAR, edge = edge)

    private fun showPanel(index: Int) = requestOverlay(Ipc.KIND_PANEL, panelIndex = index)

    private fun showShortcutPanel(edge: Int) = requestOverlay(Ipc.KIND_SHORTCUT_PANEL, edge = edge)

    private fun requestOverlay(
        kind: String,
        edge: Int = 0,
        panelIndex: Int = 0,
        packages: List<String> = emptyList(),
        packageName: String? = null,
        widgetId: Int? = null,
        widgetAction: Action.FloatingWidget? = null,
        filterAlpha: Int? = null,
        slideProgress: Float? = null,
        slideLabel: String? = null,
    ) {
        val ctx = context ?: run {
            XLog.w("cannot show $kind overlay: system context is unavailable")
            return
        }
        try {
            val request = Intent(Ipc.SHOW_OVERLAY)
                .setPackage(Ipc.APP_PACKAGE)
                .putExtra(Ipc.EXTRA_OVERLAY_KIND, kind)
                .putExtra(Ipc.EXTRA_EDGE, edge)
                .putExtra(Ipc.EXTRA_PANEL, panelIndex)
            if (packages.isNotEmpty()) request.putStringArrayListExtra(Ipc.EXTRA_PACKAGES, ArrayList(packages))
            packageName?.let { request.putExtra(Ipc.EXTRA_PACKAGE, it) }
            widgetId?.let { request.putExtra(Ipc.EXTRA_WIDGET_ID, it) }
            widgetAction?.let {
                request.putExtra(Ipc.EXTRA_WIDGET_WIDTH_DP, it.widthDp)
                request.putExtra(Ipc.EXTRA_WIDGET_HEIGHT_DP, it.heightDp)
                request.putExtra(Ipc.EXTRA_WIDGET_X_PERCENT, it.initialXPercent)
                request.putExtra(Ipc.EXTRA_WIDGET_Y_PERCENT, it.initialYPercent)
                request.putExtra(Ipc.EXTRA_WIDGET_TIMEOUT_SECONDS, it.timeoutSeconds)
                request.putExtra(Ipc.EXTRA_WIDGET_REMEMBER_POSITION, it.rememberLastPosition)
            }
            filterAlpha?.let { request.putExtra(Ipc.EXTRA_FILTER_ALPHA, it) }
            slideProgress?.let { request.putExtra(Ipc.EXTRA_SLIDE_PROGRESS, it) }
            slideLabel?.let { request.putExtra(Ipc.EXTRA_SLIDE_LABEL, it) }
            ctx.sendBroadcast(request)
        } catch (t: Throwable) {
            XLog.w("could not request $kind overlay: ${t.message}")
        }
    }

    private fun reportUnsupported(id: Int, reason: String) {
        XLog.w("action ${com.edgeflow.core.ActionCatalog.title(id)} is unavailable: $reason")
        AutomationRuntimeStatus.log.fail(id, reason)
        abortCurrent = true
    }

    private fun sendSpecial(action: Action.Special) {
        val intent = Intent(action.action ?: return)
        action.packageName?.let { intent.setPackage(it) }
        action.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startIntent(intent)
    }

    private fun broadcastToApp(action: String, intent: Intent) {
        val ctx = context ?: return
        intent.setAction(action)
        ctx.sendBroadcast(intent)
    }

    private fun setSystemSetting(key: String, enabled: Boolean?) {
        val ctx = context ?: return
        val resolver = ctx.contentResolver
        val current = android.provider.Settings.System.getInt(resolver, key, 0)
        val target = enabled ?: (current == 0)
        android.provider.Settings.System.putInt(resolver, key, if (target) 1 else 0)
    }

    private fun setSystemSetting(key: String, value: Int) {
        val ctx = context ?: return
        android.provider.Settings.System.putInt(ctx.contentResolver, key, value)
    }

    private fun setSystemSettingRead(key: String, fallback: Int): Int {
        val ctx = context ?: return fallback
        return android.provider.Settings.System.getInt(ctx.contentResolver, key, fallback)
    }

    private fun setGlobalSetting(key: String, enabled: Boolean?) {
        val ctx = context ?: return
        val resolver = ctx.contentResolver
        val current = android.provider.Settings.Global.getInt(resolver, key, 0)
        val target = enabled ?: (current == 0)
        android.provider.Settings.Global.putInt(resolver, key, if (target) 1 else 0)
    }

    private fun setLocationMode(enabled: Boolean?) {
        val ctx = context ?: return
        val resolver = ctx.contentResolver
        val current = android.provider.Settings.Secure.getInt(resolver, "location_mode", 0)
        val target = enabled ?: (current == 0)
        android.provider.Settings.Secure.putInt(resolver, "location_mode", if (target) 3 else 0)
    }

    private companion object {
        const val UNLOCK_RESUME_WINDOW_MS = 120_000L
        const val SHELL_TIMEOUT_MILLIS = 30_000L
        const val MAX_DEPTH = 32
        const val MAX_ADJUST_STEPS = 25
        const val MAX_RECENT_TASKS = 100
        const val MAX_BRIGHTNESS = 255
        const val MAX_SCREEN_FILTER_ALPHA = 100
        const val MAX_SAFE_SCREEN_FILTER_ALPHA = 75
        const val DEFAULT_SCREEN_FILTER_ALPHA = 35
        const val MAX_SCROLL_SPEED_PERCENT = 500
        const val MAX_SLIDE_ADJUST_STEPS = 16
        const val SLIDE_ADJUST_CLOSED = -1f
        const val MOVE_TASK_WITH_HOME = 2
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        const val CHANNEL_ID = "edgeflow-actions"
        const val CHANNEL_NAME = "EdgeFlow actions"
        const val INJECT_MODE_ASYNC = 0
        const val SPLIT_SCREEN_CATEGORY = "android.intent.category.MULTIWINDOW_LAUNCHER"

        /**
         * `AudioManager.STREAM_BLUETOOTH_SCO` is hidden from the public SDK, so
         * the constant is spelled out. The value has been 6 since API 11.
         */
        const val STREAM_BLUETOOTH_SCO = 6
    }
}

/** Tracks which package currently has focus, so actions can target it. */
internal object ForegroundApp {
    @Volatile
    var current: String? = null

    @Volatile
    var previous: String? = null
}

/** Holds the binder token for the most recently resumed ActivityRecord. */
internal object ForegroundActivity {
    @Volatile
    var token: Any? = null
}
