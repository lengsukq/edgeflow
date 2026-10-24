package com.edgeflow.hook

import android.os.Handler
import android.os.HandlerThread
import android.content.Context
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.edgeflow.core.ActionId
import com.edgeflow.core.GameModePolicy
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.KeyGesture
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.SlideAdjustProgressTracker
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.SlideAdjustUpdate
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.AppSettings
import com.edgeflow.hook.gesture.BindingResolver
import com.edgeflow.hook.gesture.Cancellable
import com.edgeflow.hook.gesture.EdgeGeometryFactory
import com.edgeflow.hook.gesture.FiredGesture
import com.edgeflow.hook.gesture.GestureEngine
import com.edgeflow.hook.gesture.GestureScheduler
import com.edgeflow.hook.gesture.SubGestureRecognizer
import com.edgeflow.hook.input.NativeInputBridge
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule

/**
 * Intercepts every touch event before it reaches the focused application.
 *
 * ## How this works on Android 9 through 17
 *
 * `InputManagerService` declares a package-private
 * `filterInputEvent(InputEvent, int)` that the native input dispatcher calls for
 * every key and motion event. Its contract is inverted relative to what you
 * would expect: the Java method returns *whether dispatch should continue*, and
 * returning `false` means the filter consumed the event.
 *
 * The method body is useless to us — it forwards to a Binder `IInputFilter` that
 * we do not have. Hooking it and answering from the hook means the original body
 * never runs, so the absent `mInputFilter` field is irrelevant.
 *
 * Two further hooks are needed for the dispatcher to call us at all:
 *
 *  * `NativeInputManagerService$NativeImpl.setInputFilterEnabled(boolean)` is
 *    forced to `true`, because the dispatcher short-circuits before calling the
 *    Java filter when the native gate is closed.
 *  * `InputManagerService.setInputFilter(IInputFilter)` is watched so that
 *    anything clearing the filter re-opens the native gate.
 *
 * These signatures are unchanged on the `android16-release` and
 * `android17-release` branches of `frameworks/base`; each is still resolved
 * defensively so a future rename disables one feature instead of the module.
 */
internal class InputInterceptor(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val repository: ConfigRepository,
    private val executor: ActionExecutor,
) : ManagedComponent {

    override val name: String = "input"

    private val watchdog = InputThreadWatchdog()

    /** Owns the timer callbacks the gesture engine schedules. */
    private val timerThread = HandlerThread("EdgeFlow-gestures").apply { start() }
    private val timerHandler = Handler(timerThread.looper)

    private val resolver = ConfigBindingResolver(repository, executor, ::dispatchRecognizedGesture)
    private val engine = GestureEngine(
        geometry = EdgeGeometryFactory.fromSystem(repository.config.settings),
        bindings = resolver,
        scheduler = HandlerScheduler(timerHandler),
    )

    @Volatile
    private var filterInstalled = true
    private var filterHookActive = false
    /** The framework may already have an accessibility/system IInputFilter. */
    @Volatile private var systemFilterEnabled = false
    /** Distinguish our own native gate updates from system-issued updates. */
    private val internalGateCall = ThreadLocal.withInitial { false }
    private var nativeBridge: NativeInputBridge? = null
    @Volatile private var displayContext: Context? = null
    @Volatile private var currentGeometry = EdgeGeometryFactory.fromSystem(repository.config.settings)
    @Volatile private var earlyVolumeKeyHandler: ((KeyEvent) -> Boolean)? = null
    private val slideSessionLock = Any()
    private var slideSession: SlideSession? = null

    private val subGestureLock = Any()
    private var subGesture: SubGestureRecognizer? = null
    /** Claim the entire remainder of a stream once the first stage was matched. */
    private var subStreamClaimed = false
    private var subHoldCallback: Runnable? = null
    private var gameModeBypassStream = false

    private data class SlideSession(
        val action: Action.SlideAdjust,
        val pointerId: Int,
        val tracker: SlideAdjustProgressTracker,
    )

    @Volatile private var pointerTouchActive = false
    @Volatile private var pointerOverlayVisible = false
    @Volatile private var pointerActionVisible = false
    @Volatile private var pointerActionDurationMillis = 300L
    @Volatile private var lastPointerX = 0f
    @Volatile private var lastPointerY = 0f
    private val pointerActionLock = Any()
    private var pointerActionHide: Runnable? = null
    private val removePointerConfigListener = repository.addConfigListener { config ->
        // Preferences become authoritative after any app-side change. This
        // clears a stale transient override (including user UI changes).
        RuntimeToggles.gameMode = null
        onPointerConfigChanged(config)
        InputRuntimeStatus.configUpdated(config)
        updateGestureGeometry(config.settings)
        updateNativeGate()
    }

    /** Shared recogniser: do not independently process the same key in two hooks. */
    fun setEarlyVolumeKeyHandler(handler: (KeyEvent) -> Boolean) {
        earlyVolumeKeyHandler = handler
    }

    fun onSystemContextReady(context: Context) {
        displayContext = context
        updateGestureGeometry(repository.config.settings)
        updateNativeGate()
    }

    private fun updateGestureGeometry(settings: AppSettings) {
        val geometry = displayContext?.let { EdgeGeometryFactory.fromDisplay(it, settings) }
            ?: EdgeGeometryFactory.fromSystem(settings)
        currentGeometry = geometry
        engine.updateGeometry(geometry)
        InputRuntimeStatus.displayWidth = geometry.screenWidth
        InputRuntimeStatus.displayHeight = geometry.screenHeight
    }

    override fun install(hooks: HookRegistrar) {
        val inputManagerService = Reflect.findClass(INPUT_MANAGER_SERVICE, classLoader)
        if (inputManagerService == null) {
            XLog.e("$INPUT_MANAGER_SERVICE not found; edge gestures unavailable")
            return
        }

        InputRuntimeStatus.configUpdated(repository.config)
        updateGestureGeometry(repository.config.settings)
        installFilterHook(hooks, inputManagerService)
        installNativeFilterHook(hooks, inputManagerService)
        installFilterResetHook(hooks, inputManagerService)
        // A disabled native filter never invokes filterInputEvent(), so waiting
        // for its first invocation to acquire mNative is a deadlock. Acquire
        // the bridge during construction or native setup instead.
        inputManagerService.declaredConstructors.forEachIndexed { index, constructor ->
            hooks.install("InputManagerService.constructor[$index]", constructor) { chain ->
                val result = chain.proceed()
                captureNativeBridgeOnce(chain.thisObject)
                result
            }
        }
        bindFromLocalServices()
    }

    /** Fallback for ROMs that construct InputManagerService before module hook install. */
    fun bindFromLocalServices() {
        if (nativeBridge != null || !filterInstalled || !filterHookActive) return
        runCatching {
            val services = Reflect.findClass("com.android.server.LocalServices", classLoader)
                ?: return@runCatching
            val inputInternal = Reflect.findClass("android.hardware.input.InputManagerInternal", classLoader)
                ?: return@runCatching
            val service = Reflect.method(services, "getService", Class::class.java)
                ?.invoke(null, inputInternal) ?: return@runCatching
            val owner = Reflect.readField(service, "this\$0")
                ?: Reflect.readField(service, "mService") ?: return@runCatching
            captureNativeBridgeOnce(owner)
        }.onFailure { XLog.w("LocalServices input manager bridge lookup failed: ${it.javaClass.simpleName}") }
    }

    private fun beginSubGesture(action: Action.SubGestures, gesture: FiredGesture) {
        // Single taps and double taps finish after UP; there is no live pointer
        // for a second stage. This feature is for swipes or a held first stage.
        if (gesture.pointerId < 0 || (gesture.gesture !in SWIPE_GESTURES && gesture.gesture != Gesture.LONG_PRESS)) {
            XLog.w("Sub gestures require an active swipe or long press as their first stage")
            return
        }
        engine.disableForCurrentStream(forceConsume = true)
        synchronized(subGestureLock) {
            subHoldCallback?.let(timerHandler::removeCallbacks)
            subGesture = SubGestureRecognizer(
                config = action,
                pointerId = gesture.pointerId,
                originX = gesture.currentX,
                originY = gesture.currentY,
                thresholdPx = gesture.stepDistance,
            )
            subStreamClaimed = true
            if (action.hold.id != ActionId.NONE) {
                val current = subGesture
                val callback = Runnable {
                    val selected = synchronized(subGestureLock) {
                        if (current == null || subGesture !== current) null
                        else current.hold().also {
                            subGesture = null
                            subHoldCallback = null
                        }
                    }
                    selected?.let { executor.run(it, "sub gesture hold") }
                }
                subHoldCallback = callback
                timerHandler.postDelayed(callback, repository.config.settings.longPressTimeoutMillis.coerceAtLeast(1L))
            }
        }
    }

    /** Returns true when this event is part of the already claimed sub-stream. */
    private fun handleSubGestureEvent(event: MotionEvent, enabled: Boolean): Boolean {
        var selected: Action? = null
        var consumed: Boolean
        synchronized(subGestureLock) {
            consumed = subStreamClaimed
            if (!consumed) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Defensive recovery if an UP/CANCEL was dropped.
                    clearSubGestureLocked(clearClaim = true)
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (enabled && event.pointerCount == 1) {
                        selected = subGesture?.move(event.getPointerId(0), event.rawX, event.rawY)
                        if (subGesture?.isResolved() == true) clearSubGestureLocked(clearClaim = false)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    // Multi-touch invalidates recognition, but never forwards
                    // the remainder of a claimed stream to the foreground app.
                    clearSubGestureLocked(clearClaim = false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> clearSubGestureLocked(clearClaim = true)
            }
        }
        selected?.let { executor.run(it, "sub gesture swipe") }
        // The sub-stage bypassed the primary engine's UP/CANCEL handling.
        // Release its claimed state at the terminal event, or every later
        // gesture would be swallowed by the old CONSUMED phase.
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            engine.reset()
        }
        return consumed
    }

    private fun cancelSubGesture(clearClaim: Boolean) = synchronized(subGestureLock) {
        clearSubGestureLocked(clearClaim)
    }

    private fun clearSubGestureLocked(clearClaim: Boolean) {
        subHoldCallback?.let(timerHandler::removeCallbacks)
        subHoldCallback = null
        subGesture = null
        if (clearClaim) subStreamClaimed = false
    }

    override fun uninstall() {
        filterInstalled = false
        InputRuntimeStatus.nativeGateRequested = false
        InputRuntimeStatus.hookInstalled = false
        pointerTouchActive = false
        cancelGesturePointerAction()
        pointerOverlayVisible = false
        removePointerConfigListener()
        executor.shutdownGesturePointerPublisher()
        cancelSlideAdjustSession()
        cancelSubGesture(clearClaim = true)
        gameModeBypassStream = false
        engine.reset()
        nativeBridge?.setFilterEnabled(false)
        earlyVolumeKeyHandler = null
        displayContext = null
        timerHandler.removeCallbacksAndMessages(null)
        timerThread.quitSafely()
    }

    /**
     * Hooks `filterInputEvent` itself.
     *
     * The parameter types are stable across the releases we target, so an exact
     * lookup is preferred; the arity-based fallback keeps a future release
     * working rather than silently disabling gestures.
     */
    private fun installFilterHook(hooks: HookRegistrar, owner: Class<*>) {
        val target = Reflect.method(owner, "filterInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            ?: Reflect.methodByArity(owner, "filterInputEvent", 2)

        val installed = hooks.install("InputManagerService.filterInputEvent", target) { chain ->
            val event = chain.getArg(0) as? InputEvent ?: return@install chain.proceed()
            captureNativeBridgeOnce(chain.thisObject)
            if (event is MotionEvent) InputRuntimeStatus.observedTouch()
            val pass = watchdog.measure("filterInputEvent") { handleEvent(event) } as? Boolean ?: true
            if (pass) {
                // Preserve Android's own input filter and event recycling.
                chain.proceed()
            } else {
                // Android's original method recycles the event only when its
                // own filter is absent. We consumed it; we must recycle it.
                (event as? MotionEvent)?.recycle()
                false
            }
        }
        filterHookActive = installed != null
        InputRuntimeStatus.hookInstalled = filterHookActive
    }

    /**
     * Forces the native input filter on.
     *
     * `NativeImpl.setInputFilterEnabled` is where the dispatcher's gate is
     * actually flipped, so intercepting it covers the platform's own calls as
     * well as ours.
     */
    private fun installNativeFilterHook(hooks: HookRegistrar, owner: Class<*>) {
        val nativeImpl = Reflect.nestedClass(
            Reflect.readField(owner, "mNative")?.javaClass ?: owner,
            "NativeImpl",
        ) ?: Reflect.findClass(NATIVE_INPUT_MANAGER_IMPL, classLoader)

        if (nativeImpl == null) {
            XLog.w("native input manager implementation not found; the filter may stay disabled")
            return
        }

        val setEnabled = Reflect.method(nativeImpl, "setInputFilterEnabled", Boolean::class.javaPrimitiveType)
        hooks.install("NativeInputManagerService.NativeImpl.setInputFilterEnabled", setEnabled) { chain ->
            if (filterInstalled && filterHookActive && nativeBridge == null) {
                bindNativeManager(chain.thisObject)
            }
            // Never turn OFF an existing system filter. Turn ours on only if
            // at least one enabled segment has a valid action.
            val platformEnabled = chain.getArg(0) as? Boolean ?: false
            if (!internalGateCall.get()) systemFilterEnabled = platformEnabled
            val needed = shouldFilterGestures()
            val effective = needed || systemFilterEnabled
            InputRuntimeStatus.nativeGateRequested = effective
            chain.proceed(arrayOf<Any?>(effective))
        }
    }

    /**
     * Watches `setInputFilter` so clearing the filter does not leave the native
     * gate open with no consumer.
     */
    private fun installFilterResetHook(hooks: HookRegistrar, owner: Class<*>) {
        val setInputFilter = Reflect.methodAny(owner, "setInputFilter") ?: return

        hooks.install("InputManagerService.setInputFilter", setInputFilter) { chain ->
            val filter = chain.getArg(0)
            val result = chain.proceed()
            if (filter == null) updateNativeGate()
            result
        }
    }

    /**
     * Binds the native bridge the first time a live `InputManagerService` is
     * seen, which is the only moment the instance is reachable.
     */
    private fun captureNativeBridgeOnce(thisObject: Any?) {
        if (!filterInstalled || nativeBridge != null || thisObject == null) return
        val bridge = NativeInputBridge.fromInputManager(thisObject) ?: return
        nativeBridge = bridge
        executor.attachNativeBridge(bridge)
        InputRuntimeStatus.nativeBridgeBound = true
        updateNativeGate()
        XLog.i("native input bridge bound before touch events")
    }

    private fun bindNativeManager(instance: Any?) {
        if (nativeBridge != null || !filterInstalled || instance == null) return
        val bridge = NativeInputBridge.fromNativeManager(instance)
        nativeBridge = bridge
        executor.attachNativeBridge(bridge)
        InputRuntimeStatus.nativeBridgeBound = true
        XLog.i("native input bridge acquired from setInputFilterEnabled")
    }

    private fun shouldFilterGestures(): Boolean {
        val config = repository.config
        return filterInstalled && filterHookActive &&
            (((RuntimeToggles.gesturesEnabled ?: config.settings.gestureControlEnabled) &&
                InputRuntimeStatus.boundEnabledActionCount > 0) ||
                ((RuntimeToggles.keysEnabled ?: config.settings.keyControlEnabled) &&
                    listOf(HardwareKey.VOLUME_UP, HardwareKey.VOLUME_DOWN).any { key ->
                        config.keys.isKeyEnabled(key) && KeyGesture.entries.any { gesture ->
                            config.keys.resolve(key, gesture).id != ActionId.NONE
                        }
                    }))
    }

    private fun updateNativeGate() {
        val needed = shouldFilterGestures()
        val effective = needed || systemFilterEnabled
        InputRuntimeStatus.nativeGateRequested = effective
        internalGateCall.set(true)
        try {
            nativeBridge?.setFilterEnabled(effective)
        } finally {
            internalGateCall.remove()
        }
    }

    /**
     * Routes one input event to the gesture engine and translates its verdict
     * into the dispatcher's `continueDispatch` contract.
     */
    private fun handleEvent(event: InputEvent): Any {
        if (event is KeyEvent) {
            if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return true
            InputRuntimeStatus.volumeKeyEvents.incrementAndGet()
            val handled = earlyVolumeKeyHandler?.invoke(event) == true
            if (handled) InputRuntimeStatus.volumeKeysConsumed.incrementAndGet()
            return !handled
        }
        if (event !is MotionEvent) return true

        // NativeInputBridge marks our own injected events POLICY_FLAG_FILTERED.
        // A package-name call-stack check would also match real physical touches.

        // Only the default display is interpreted. Secondary displays have their
        // own coordinate space and no edge semantics.
        if (!isPrimaryDisplay(event)) return true

        val settings = repository.config.settings
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val edgeZone = currentGeometry.hitTest(event.rawX, event.rawY)
            if (edgeZone != null) InputRuntimeStatus.edgeDownEvents.incrementAndGet()
            // Never consume a game touch in the original input filter. Ignored
            // edges belong entirely to the foreground game until UP/CANCEL.
            val inGameMode = RuntimeToggles.gameMode ?: settings.gameMode
            val zone = if (inGameMode) EdgeGeometryFactory.fromSystem(settings)
                .hitTest(event.rawX, event.rawY) else null
            gameModeBypassStream = zone != null &&
                GameModePolicy.ignoreEdge(zone.id, settings.gameIgnoreEdgesMask)
            if (gameModeBypassStream) {
                cancelSlideAdjustSession()
                cancelSubGesture(clearClaim = true)
                engine.reset()
            }
        }
        if (gameModeBypassStream) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) gameModeBypassStream = false
            return true
        }
        val gesturesEnabled = RuntimeToggles.gesturesEnabled ?: settings.gestureControlEnabled
        if (!gesturesEnabled) cancelGesturePointerAction()
        updateGesturePointer(event, enabled = gesturesEnabled && settings.showGesturePointer, settings = settings)
        if (!gesturesEnabled) cancelSubGesture(clearClaim = false)
        // A parent SubGestures binding already claimed this touch stream. Keep
        // consuming even when the configured secondary direction is empty, since
        // dispatching MOVE/UP without the original DOWN would break the app.
        if (handleSubGestureEvent(event, gesturesEnabled)) return false
        if (!gesturesEnabled) {
            val hadSlideSession = hasSlideAdjustSession()
            cancelSlideAdjustSession()
            val wasClaimed = engine.disableForCurrentStream(forceConsume = hadSlideSession)
            if (!wasClaimed) return true
            val outcome = engine.onTouch(
                actionMasked = event.actionMasked,
                rawX = event.rawX,
                rawY = event.rawY,
                pointerId = event.getPointerId(0),
                eventTime = event.eventTime,
            )
            return !outcome.consumed
        }

        val slideConsumed = updateSlideAdjustSession(event)

        val outcome = engine.onTouch(
            actionMasked = event.actionMasked,
            rawX = event.rawX,
            rawY = event.rawY,
            pointerId = event.getPointerId(0),
            eventTime = event.eventTime,
        )
        outcome.fired?.let(::dispatchRecognizedGesture)
        return !(outcome.consumed || slideConsumed)
    }

    private fun dispatchRecognizedGesture(gesture: FiredGesture) {
        val action = resolver.actionFor(gesture)
        if (action.id == ActionId.NONE) return
        InputRuntimeStatus.triggeredGestureCount.incrementAndGet()
        executor.onGestureRecognized()
        if (action is Action.SubGestures) {
            beginSubGesture(action, gesture)
            return
        }
        if (action.id == ActionId.GESTURE_POINTER) {
            startGesturePointerAction(gesture)
            return
        }
        if (action is Action.SlideAdjust && gesture.gesture in SWIPE_GESTURES && gesture.pointerId >= 0) {
            val tracker = SlideAdjustProgressTracker(
                gesture = gesture.gesture,
                startX = gesture.startX,
                startY = gesture.startY,
                stepDistance = gesture.stepDistance,
            )
            val initialUpdate = tracker.update(gesture.currentX, gesture.currentY)
            val previous = synchronized(slideSessionLock) {
                slideSession.also {
                    slideSession = SlideSession(action, gesture.pointerId, tracker)
                }
            }
            if (previous != null) executor.endSlideAdjust()
            executor.startSlideAdjust(action, initialUpdate.progress)
            executor.updateSlideAdjust(action, initialUpdate)
        } else {
            executor.run(action, "gesture ${gesture.zone.label}/${gesture.gesture.label}")
        }
    }

    private fun updateSlideAdjustSession(event: MotionEvent): Boolean {
        var update: Pair<Action.SlideAdjust, SlideAdjustUpdate>? = null
        var ended = false
        var consumed = false
        synchronized(slideSessionLock) {
            val session = slideSession ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if (event.pointerCount > 0 && event.getPointerId(0) == session.pointerId) {
                        update = session.action to session.tracker.update(event.rawX, event.rawY)
                        consumed = true
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            slideSession = null
                            ended = true
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    slideSession = null
                    ended = true
                    consumed = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    slideSession = null
                    ended = true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == session.pointerId) {
                        slideSession = null
                        ended = true
                        consumed = true
                    }
                }
            }
        }
        update?.let { (action, value) -> executor.updateSlideAdjust(action, value) }
        if (ended) executor.endSlideAdjust()
        return consumed
    }

    private fun hasSlideAdjustSession(): Boolean = synchronized(slideSessionLock) { slideSession != null }

    private fun cancelSlideAdjustSession() {
        val hadSession = synchronized(slideSessionLock) {
            (slideSession != null).also { slideSession = null }
        }
        if (hadSession) executor.endSlideAdjust()
    }

    /**
     * Whether this event belongs to the default display.
     *
     * `MotionEvent.getDisplayId()` is hidden, so it is reached reflectively and
     * cached. Failing open (treating the event as primary) is the right default:
     * a device with a single display — which is nearly all of them — would
     * otherwise lose gestures entirely if the lookup ever failed.
     */
    private fun isPrimaryDisplay(event: MotionEvent): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return true
        val method = displayIdMethod ?: return true
        return try {
            (method.invoke(event) as? Int)?.let { it == 0 } ?: true
        } catch (_: Throwable) {
            true
        }
    }

    private val displayIdMethod: java.lang.reflect.Method? by lazy {
        Reflect.method(MotionEvent::class.java, "getDisplayId")
    }

    private fun updateGesturePointer(
        event: MotionEvent,
        enabled: Boolean,
        settings: AppSettings,
    ) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerTouchActive = true
                lastPointerX = event.rawX
                lastPointerY = event.rawY
                if (enabled || pointerActionVisible) {
                    showGesturePointer(event.rawX, event.rawY, settings, immediate = true)
                } else {
                    hideGesturePointer()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pointerTouchActive) return
                lastPointerX = event.rawX
                lastPointerY = event.rawY
                if (enabled || pointerActionVisible) {
                    showGesturePointer(event.rawX, event.rawY, settings, immediate = !pointerOverlayVisible)
                } else {
                    hideGesturePointer()
                }
            }
            MotionEvent.ACTION_UP -> {
                pointerTouchActive = false
                lastPointerX = event.rawX
                lastPointerY = event.rawY
                if (pointerActionVisible) {
                    showGesturePointer(event.rawX, event.rawY, settings, immediate = true)
                    scheduleGesturePointerActionHide(pointerActionDurationMillis)
                } else {
                    hideGesturePointer()
                }
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP,
            -> {
                pointerTouchActive = false
                lastPointerX = event.rawX
                lastPointerY = event.rawY
                cancelGesturePointerAction()
                hideGesturePointer(force = true)
            }
        }
    }

    private fun showGesturePointer(
        x: Float,
        y: Float,
        settings: AppSettings,
        immediate: Boolean,
    ) {
        pointerOverlayVisible = true
        executor.updateGesturePointer(visible = true, x = x, y = y, settings = settings, immediate = immediate)
    }

    private fun hideGesturePointer(force: Boolean = false) {
        if (force) cancelGesturePointerAction()
        if (pointerActionVisible && !force) {
            showGesturePointer(
                lastPointerX,
                lastPointerY,
                repository.config.settings,
                immediate = true,
            )
            return
        }
        val wasVisible = pointerOverlayVisible
        pointerOverlayVisible = false
        if (wasVisible || force) {
            executor.updateGesturePointer(
                visible = false,
                x = lastPointerX,
                y = lastPointerY,
                immediate = true,
            )
        }
    }

    private fun startGesturePointerAction(gesture: FiredGesture) {
        val settings = repository.config.settings
        pointerActionDurationMillis = gesturePointerDurationMillis(gesture.gesture)
        pointerActionVisible = true
        synchronized(pointerActionLock) {
            pointerActionHide?.let(timerHandler::removeCallbacks)
            pointerActionHide = null
        }
        showGesturePointer(gesture.currentX, gesture.currentY, settings, immediate = true)
        if (!pointerTouchActive) scheduleGesturePointerActionHide(pointerActionDurationMillis)
    }

    private fun scheduleGesturePointerActionHide(delayMillis: Long) {
        lateinit var hide: Runnable
        hide = Runnable {
            val shouldHide = synchronized(pointerActionLock) {
                if (pointerActionHide !== hide) {
                    false
                } else {
                    pointerActionHide = null
                    pointerActionVisible = false
                    true
                }
            }
            if (shouldHide) {
                val settings = repository.config.settings
                val enabled = settings.showGesturePointer &&
                    (RuntimeToggles.gesturesEnabled ?: settings.gestureControlEnabled)
                if (pointerTouchActive && enabled) {
                    showGesturePointer(lastPointerX, lastPointerY, settings, immediate = true)
                } else {
                    hideGesturePointer()
                }
            }
        }
        synchronized(pointerActionLock) {
            pointerActionHide?.let(timerHandler::removeCallbacks)
            pointerActionHide = hide
            timerHandler.postDelayed(hide, delayMillis.coerceAtLeast(0L))
        }
    }

    private fun cancelGesturePointerAction() {
        synchronized(pointerActionLock) {
            pointerActionHide?.let(timerHandler::removeCallbacks)
            pointerActionHide = null
            pointerActionVisible = false
        }
    }

    private fun onPointerConfigChanged(config: EdgeConfig) {
        val settings = config.settings
        val gesturesEnabled = RuntimeToggles.gesturesEnabled ?: settings.gestureControlEnabled
        if (!gesturesEnabled) {
            cancelGesturePointerAction()
            hideGesturePointer(force = true)
        } else if ((settings.showGesturePointer && pointerTouchActive) || pointerActionVisible) {
            showGesturePointer(lastPointerX, lastPointerY, settings, immediate = true)
        } else {
            hideGesturePointer()
        }
    }

    private fun gesturePointerDurationMillis(gesture: Gesture): Long = when (gesture) {
        Gesture.TAP -> 300L
        Gesture.LONG_PRESS -> 600L
        Gesture.DOUBLE_TAP -> 450L
        Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT, Gesture.SWIPE_UP, Gesture.SWIPE_DOWN -> 3_000L
    }

    /** Called by [SystemStateObserver] when the display size or rotation changes. */
    fun onDisplayChanged() {
        cancelSubGesture(clearClaim = false)
        updateGestureGeometry(repository.config.settings)
    }

    /** Called when the screen turns off, to drop any half-finished gesture. */
    fun onScreenOff() {
        cancelSubGesture(clearClaim = true)
        pointerTouchActive = false
        hideGesturePointer(force = true)
        cancelSlideAdjustSession()
        engine.reset()
    }

    private class HandlerScheduler(private val handler: Handler) : GestureScheduler {
        override fun schedule(delayMillis: Long, action: () -> Unit): Cancellable {
            val runnable = Runnable { action() }
            handler.postDelayed(runnable, delayMillis)
            return Cancellable { handler.removeCallbacks(runnable) }
        }
    }

    private companion object {
        const val INPUT_MANAGER_SERVICE = "com.android.server.input.InputManagerService"
        const val NATIVE_INPUT_MANAGER_IMPL = "com.android.server.input.NativeInputManagerService\$NativeImpl"
        val SWIPE_GESTURES = setOf(Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT, Gesture.SWIPE_UP, Gesture.SWIPE_DOWN)
    }
}

/**
 * Resolves bindings from the configuration and hands matches to the executor.
 *
 * Kept separate from the engine so the engine stays a pure state machine while
 * this class owns all the policy questions: global switches, the lock screen, and
 * which app is in front.
 */
internal class ConfigBindingResolver(
    private val repository: ConfigRepository,
    private val executor: ActionExecutor,
    private val onRecognizedGesture: ((FiredGesture) -> Unit)? = null,
) : BindingResolver {

    override fun isZoneEnabled(zone: EdgeZone): Boolean {
        val settings = repository.config.settings
        if (!(RuntimeToggles.gesturesEnabled ?: settings.gestureControlEnabled)) return false
        if (!settings.triggerOnKeyguard) {
            KeyguardState.refreshIfShowing()
            if (KeyguardState.isShowing) {
                InputRuntimeStatus.keyguardBlockedDowns.incrementAndGet()
                return false
            }
        }
        val enabled = repository.config.gestures.isZoneEnabled(zone)
        if (enabled) InputRuntimeStatus.activeZoneDowns.incrementAndGet()
        return enabled
    }

    override fun hasAnyBinding(zone: EdgeZone): Boolean =
        Gesture.entries.any { hasBinding(zone, it) }

    override fun hasBinding(zone: EdgeZone, gesture: Gesture): Boolean =
        repository.config.gestures.resolve(zone, gesture).id != com.edgeflow.core.ActionId.NONE

    override fun onGesture(gesture: FiredGesture) {
        val action = actionFor(gesture)
        if (action.id == com.edgeflow.core.ActionId.NONE) return
        if (onRecognizedGesture != null) onRecognizedGesture.invoke(gesture)
        else executor.run(action, "gesture ${gesture.zone.label}/${gesture.gesture.label}")
    }

    fun actionFor(gesture: FiredGesture): Action =
        repository.config.gestures.resolve(gesture.zone, gesture.gesture)

    override fun longPressTimeoutMillis(): Long {
        val configured = repository.config.settings.longPressTimeoutMillis
        return configured.takeIf { it > 0 }
            ?: android.view.ViewConfiguration.getLongPressTimeout().toLong()
    }

    override fun doubleTapTimeoutMillis(): Long {
        val configured = repository.config.settings.doubleTapTimeoutMillis
        return configured.takeIf { it > 0 }
            ?: android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
    }
}

/**
 * Tracks whether the keyguard is currently showing.
 *
 * A tiny shared holder rather than a hook, because several unrelated components
 * need the answer and none of them should own the observation.
 */
internal object KeyguardState {
    @Volatile
    var isShowing: Boolean = false
    @Volatile private var manager: android.app.KeyguardManager? = null
    @Volatile private var lastCheckElapsed = 0L
    @Volatile private var refreshPending = false
    private val mainHandler by lazy { Handler(android.os.Looper.getMainLooper()) }

    fun refresh(context: android.content.Context) {
        val service = context.getSystemService(android.app.KeyguardManager::class.java) ?: return
        manager = service
        isShowing = service.isKeyguardLocked
        lastCheckElapsed = android.os.SystemClock.elapsedRealtime()
    }

    /** Recover from an OEM missing ACTION_USER_PRESENT without querying on each touch. */
    fun refreshIfShowing() {
        if (!isShowing) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCheckElapsed < 500L || refreshPending) return
        synchronized(this) {
            if (!isShowing || refreshPending || now - lastCheckElapsed < 500L) return
            refreshPending = true
            mainHandler.post {
                try {
                    manager?.let { service ->
                        runCatching { service.isKeyguardLocked }
                            .onSuccess { isShowing = it }
                    }
                } finally {
                    lastCheckElapsed = android.os.SystemClock.elapsedRealtime()
                    refreshPending = false
                }
            }
        }
    }
}
