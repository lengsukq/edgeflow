package com.edgeflow.hook

import android.view.KeyEvent
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.GameModePolicy
import com.edgeflow.core.KeyGesture
import com.edgeflow.hook.gesture.Cancellable
import com.edgeflow.hook.gesture.GestureScheduler
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule

/**
 * Remaps hardware keys to actions.
 *
 * ## Hook choice
 *
 * `PhoneWindowManager.interceptKeyBeforeDispatching` is the last point at which
 * a key can be consumed before it reaches the application, and it is the only
 * point where swallowing a key does not also suppress it from the system UI.
 * Hooking earlier (`interceptKeyBeforeQueueing`) would break the power and
 * volume handling that the platform performs itself.
 *
 * ## Signature drift
 *
 * The method's signature changed between Android 16 and 17:
 *
 *  * API 36 — `long interceptKeyBeforeDispatching(IBinder, KeyEvent, int)`
 *  * API 37 — `boolean interceptKeyBeforeDispatching(IBinder, KeyEvent)`
 *
 * The return type changed meaning too: `long` used `-1` to mean "consumed",
 * `boolean` uses `true`. Rather than branch on the version, the hook resolves
 * whichever signature exists and derives the "consumed" value from the declared
 * return type. That keeps working if a future release changes it again.
 */
internal class KeyInterceptor(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val repository: ConfigRepository,
    private val executor: ActionExecutor,
) : ManagedComponent {

    override val name: String = "keys"

    private val engine = HardwareKeyEngine(repository, executor)
    private val scheduler: GestureScheduler = HandlerGestureScheduler("EdgeFlow-keys")

    /** Volume keys can be dispatched by the system before PWM's late callback. */
    fun onEarlyVolumeKeyEvent(event: KeyEvent): Boolean {
        val settings = repository.config.settings
        val inGameMode = RuntimeToggles.gameMode ?: settings.gameMode
        if (inGameMode && GameModePolicy.interceptKey(event.keyCode, settings.gameInterceptKeysMask)) {
            return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
        }
        if (!(RuntimeToggles.keysEnabled ?: settings.keyControlEnabled)) return false
        return engine.onKeyEvent(event, scheduler)
    }

    override fun install(hooks: HookRegistrar) {
        val policyClass = Reflect.findClassAny(
            classLoader,
            PHONE_WINDOW_MANAGER,
            LEGACY_PHONE_WINDOW_MANAGER,
        )
        if (policyClass == null) {
            XLog.w("phone window manager not found; key remapping unavailable")
            return
        }

        val target = Reflect.methodAny(policyClass, "interceptKeyBeforeDispatching")
        if (target == null) {
            XLog.w("interceptKeyBeforeDispatching not found; key remapping unavailable")
            return
        }

        val consumedValue = consumedValueFor(target.returnType)
        XLog.d(
            "key hook signature: ${target.parameterTypes.joinToString { it.simpleName }} -> " +
                "${target.returnType.simpleName} (consumed = $consumedValue)",
        )

        hooks.install("PhoneWindowManager.interceptKeyBeforeDispatching", target) { chain ->
            val event = chain.args.filterIsInstance<KeyEvent>().firstOrNull()
            if (event == null) {
                return@install chain.proceed()
            }
            // Module-generated keys are marked FILTERED in the native bridge.
            val gameSettings = repository.config.settings
            val inGameMode = RuntimeToggles.gameMode ?: gameSettings.gameMode
            if (inGameMode && GameModePolicy.interceptKey(event.keyCode, gameSettings.gameInterceptKeysMask)) {
                // Only explicitly selected keys, never power, are swallowed.
                if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                    return@install consumedValue
                }
            }
            if (!(RuntimeToggles.keysEnabled ?: repository.config.settings.keyControlEnabled)) {
                return@install chain.proceed()
            }

            val handled = engine.onKeyEvent(event, scheduler)
            if (handled) consumedValue else chain.proceed()
        }
    }

    override fun uninstall() {
        engine.reset()
    }

    /**
     * Derives the sentinel that means "the key was consumed" from the declared
     * return type, so the hook does not need a per-version branch.
     */
    private fun consumedValueFor(returnType: Class<*>): Any = when (returnType) {
        java.lang.Boolean.TYPE -> true
        java.lang.Long.TYPE -> -1L
        java.lang.Integer.TYPE -> -1
        else -> true
    }

    private companion object {
        const val PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager"
        const val LEGACY_PHONE_WINDOW_MANAGER = "com.android.internal.policy.impl.PhoneWindowManager"
    }
}

/**
 * Click / double-click / long-press recognition for the remappable keys.
 *
 * The recogniser is shared by every key, so pressing volume-up while a
 * double-click of power is pending does not corrupt either sequence.
 */
internal class HardwareKeyEngine(
    private val repository: ConfigRepository,
    private val executor: ActionExecutor,
) {

    private val pending = HashMap<HardwareKey, PendingPress>()

    /** Maps an Android key code to a remappable key, or `null` if we ignore it. */
    private fun remappableKey(keyCode: Int): HardwareKey? = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> HardwareKey.BACK
        KeyEvent.KEYCODE_HOME -> HardwareKey.HOME
        KeyEvent.KEYCODE_APP_SWITCH -> HardwareKey.RECENT_APPS
        KeyEvent.KEYCODE_MENU -> HardwareKey.MENU
        KeyEvent.KEYCODE_VOLUME_UP -> HardwareKey.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> HardwareKey.VOLUME_DOWN
        KeyEvent.KEYCODE_HEADSETHOOK -> HardwareKey.HEADSET_HOOK
        else -> null
    }

    /**
     * Feeds one key event in.
     *
     * @return `true` when the key was consumed and must not reach the app
     */
    fun onKeyEvent(event: KeyEvent, scheduler: GestureScheduler): Boolean {
        val key = remappableKey(event.keyCode) ?: return false
        if (!repository.config.keys.isKeyEnabled(key)) return false

        val table = repository.config.keys
        val clickBound = table.resolve(key, KeyGesture.CLICK).id != com.edgeflow.core.ActionId.NONE
        val doubleBound = table.resolve(key, KeyGesture.DOUBLE_CLICK).id != com.edgeflow.core.ActionId.NONE
        val longBound = table.resolve(key, KeyGesture.LONG_PRESS).id != com.edgeflow.core.ActionId.NONE

        // Nothing bound to this key at all: leave it entirely alone, including
        // the platform's own volume and power handling.
        if (!clickBound && !doubleBound && !longBound) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) {
                // Auto-repeat: a long press has already been scheduled or fired.
                return pending[key]?.consumed ?: false
            }
            return onKeyDown(key, clickBound, doubleBound, longBound, scheduler)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            return onKeyUp(key, clickBound, doubleBound)
        }
        return false
    }

    private fun onKeyDown(
        key: HardwareKey,
        clickBound: Boolean,
        doubleBound: Boolean,
        longBound: Boolean,
        scheduler: GestureScheduler,
    ): Boolean {
        val existing = pending[key]
        if (existing != null) {
            existing.longPressToken?.cancel()
            existing.longPressToken = null
            // A second press inside the double-click window is a double click.
            val now = android.os.SystemClock.uptimeMillis()
            if (doubleBound && now - existing.downAt <= repository.config.settings.keyDoubleClickTimeoutMillis) {
                pending.remove(key)
                if (existing.clickToken != null) {
                    existing.clickToken?.cancel()
                    existing.clickToken = null
                }
                executor.run(
                    repository.config.keys.resolve(key, KeyGesture.DOUBLE_CLICK),
                    "key ${key.label} double click",
                )
                return true
            }
            // Too late for a double click; treat the previous press as finished.
            existing.clickToken?.cancel()
            pending.remove(key)
        }

        val entry = PendingPress(downAt = android.os.SystemClock.uptimeMillis())
        pending[key] = entry

        if (longBound) {
            entry.longPressToken = scheduler.schedule(repository.config.settings.keyLongClickTimeoutMillis) {
                entry.longPressToken = null
                entry.longPressFired = true
                executor.run(
                    repository.config.keys.resolve(key, KeyGesture.LONG_PRESS),
                    "key ${key.label} long press",
                )
            }
        }

        // The key press is held back until we know whether it is a double click.
        // That is the only way to support double-click without also firing a
        // single click, and it is why the press itself reports as consumed.
        entry.consumed = clickBound || doubleBound
        return entry.consumed
    }

    private fun onKeyUp(key: HardwareKey, clickBound: Boolean, doubleBound: Boolean): Boolean {
        val entry = pending.remove(key) ?: return false
        entry.longPressToken?.cancel()
        entry.longPressToken = null

        if (entry.longPressFired) return true

        if (!doubleBound) {
            // Without a double-click binding the press can fire immediately.
            if (clickBound) {
                executor.run(repository.config.keys.resolve(key, KeyGesture.CLICK), "key ${key.label} click")
                return true
            }
            return false
        }

        // Wait out the double-click window before committing to a single click.
        entry.clickToken = HandlerGestureScheduler.shared.schedule(
            repository.config.settings.keyDoubleClickTimeoutMillis,
        ) {
            if (clickBound) {
                executor.run(repository.config.keys.resolve(key, KeyGesture.CLICK), "key ${key.label} click")
            }
        }
        return true
    }

    fun reset() {
        pending.values.forEach { it.longPressToken?.cancel(); it.clickToken?.cancel() }
        pending.clear()
    }

    private class PendingPress(val downAt: Long) {
        var longPressToken: Cancellable? = null
        var clickToken: Cancellable? = null
        var longPressFired: Boolean = false
        var consumed: Boolean = false
    }
}

/** Scheduler backed by a dedicated handler thread. */
internal class HandlerGestureScheduler(name: String) : GestureScheduler {

    private val thread = android.os.HandlerThread(name).apply { start() }
    private val handler = android.os.Handler(thread.looper)

    override fun schedule(delayMillis: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMillis)
        return Cancellable { handler.removeCallbacks(runnable) }
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    companion object {
        /** Shared instance for callbacks that are not tied to one component's lifetime. */
        val shared: HandlerGestureScheduler by lazy { HandlerGestureScheduler("EdgeFlow-scheduler") }
    }
}
