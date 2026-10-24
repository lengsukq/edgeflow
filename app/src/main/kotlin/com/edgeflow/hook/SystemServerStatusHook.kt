package com.edgeflow.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.edgeflow.core.Ipc
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import io.github.libxposed.api.XposedModule

/**
 * Lightweight system-server heartbeat, independent of input, configuration,
 * the gesture recognizer and action execution. It is installed first, before
 * constructing subsystems which might fail on an OEM Android build.
 */
internal class SystemServerStatusHook(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
) : ManagedComponent {
    override val name = "system-server-status"

    @Volatile private var systemContext: Context? = null
    private var receiver: BroadcastReceiver? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val readyListeners = mutableListOf<(Context) -> Unit>()
    @Volatile private var beforeStatusReply: (() -> Unit)? = null
    private var attempts = 0
    private val contextRetry = object : Runnable {
        override fun run() {
            if (systemContext != null || attempts++ >= 180) {
                if (systemContext == null) XLog.w("status: ActivityManager not ready after 90 seconds")
                return
            }
            tryAdoptCurrentSystemContext()
            if (systemContext == null) mainHandler.postDelayed(this, 500L)
        }
    }

    /**
     * Check fresh remote preferences immediately before replying. The module
     * may have started while the app's credential-encrypted data was locked.
     */
    fun onStatusRequest(callback: () -> Unit) {
        beforeStatusReply = callback
    }

    override fun install(hooks: HookRegistrar) {
        // Do not hook SystemServer.start* methods. A failed proceed() on a
        // critical Android service must never risk double initialization.
        // Use only bounded observation from the system main looper.
        tryAdoptCurrentSystemContext()
        if (systemContext == null) mainHandler.post(contextRetry)
    }

    fun onContextReady(listener: (Context) -> Unit) {
        synchronized(readyListeners) {
            readyListeners += listener
            systemContext?.let { ctx -> runCatching { listener(ctx) }
                .onFailure { XLog.w("status context listener failed", it) } }
        }
    }

    private fun activityManagerReady(): Boolean = runCatching {
        // ContextImpl.registerReceiverInternal() calls
        // ActivityManager.getService().registerReceiverWithFeature().
        // Before ActivityManager's binder is published this is null, even if
        // ActivityThread.currentSystemContext() returned a real Context.
        val cls = Class.forName("android.app.ActivityManager", false, classLoader)
        Reflect.method(cls, "getService")?.invoke(null) != null
    }.getOrDefault(false)

    private fun tryAdoptCurrentSystemContext() {
        if (systemContext != null || Process.myUid() != Process.SYSTEM_UID) return
        if (!activityManagerReady()) return
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread", false, classLoader)
            val direct = Reflect.method(activityThread, "currentSystemContext")
                ?.invoke(null) as? Context
            val ctx = direct ?: run {
                val thread = Reflect.method(activityThread, "currentActivityThread")
                    ?.invoke(null) ?: return@runCatching
                Reflect.method(thread.javaClass, "getSystemContext")
                    ?.invoke(thread) as? Context
            }
            if (ctx != null) registerStatusReceiver(ctx)
        }.onFailure {
            if (attempts == 0) XLog.w(
                "status: ActivityThread context discovery failed: ${it.javaClass.simpleName}"
            )
        }
    }

    // API 33+ registers with RECEIVER_EXPORTED. Only API 28-32 take the
    // backward-compatible overload without receiver flags.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver(ctx: Context) {
        if (systemContext != null) return
        try {
            val listener = object : BroadcastReceiver() {
                private var lastReply = 0L
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Ipc.MODULE_STATUS_REQUEST) return
                    val nonce = intent.getStringExtra(Ipc.EXTRA_STATUS_NONCE)
                        ?.takeIf { it.length in 16..100 } ?: run {
                            module.log(android.util.Log.WARN, "EdgeFlow",
                                "Received malformed system_server status request")
                            return
                        }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReply < 100L) return
                    lastReply = now
                    runCatching { beforeStatusReply?.invoke() }
                        .onFailure {
                            XLog.w("status: configuration refresh failed", it)
                            module.log(android.util.Log.WARN, "EdgeFlow",
                                "Cannot refresh configuration for status probe", it)
                        }
                    module.log(android.util.Log.INFO, "EdgeFlow",
                        "Received status probe in system_server")
                    val automation = AutomationRuntimeStatus.log.snapshot()
                    val response = Intent(Ipc.MODULE_STATUS_REPLY)
                        .setClassName("com.edgeflow", "com.edgeflow.service.ModuleStatusReceiver")
                        .putExtra(Ipc.EXTRA_STATUS_NONCE, nonce)
                        .putExtra(Ipc.EXTRA_STATUS_FRAMEWORK, module.getFrameworkName())
                        .putExtra(Ipc.EXTRA_STATUS_API, module.getApiVersion())
                        .putExtra(Ipc.EXTRA_INPUT_HOOK, InputRuntimeStatus.hookInstalled)
                        .putExtra(Ipc.EXTRA_INPUT_BRIDGE, InputRuntimeStatus.nativeBridgeBound)
                        .putExtra(Ipc.EXTRA_INPUT_GATE, InputRuntimeStatus.nativeGateRequested)
                        .putExtra(Ipc.EXTRA_INPUT_VERSION, InputRuntimeStatus.configVersion)
                        .putExtra(Ipc.EXTRA_INPUT_ZONES, InputRuntimeStatus.enabledZoneCount)
                        .putExtra(Ipc.EXTRA_INPUT_BINDINGS, InputRuntimeStatus.boundEnabledActionCount)
                        .putExtra(Ipc.EXTRA_INPUT_TOUCHES, InputRuntimeStatus.interceptedTouchCount.get())
                        .putExtra(Ipc.EXTRA_INPUT_FIRED, InputRuntimeStatus.triggeredGestureCount.get())
                        .putExtra(Ipc.EXTRA_INPUT_INJECTION_ATTEMPTS, InputRuntimeStatus.injectionAttempts.get())
                        .putExtra(Ipc.EXTRA_INPUT_INJECTION_ACCEPTED, InputRuntimeStatus.injectionAccepted.get())
                        .putExtra(Ipc.EXTRA_INPUT_INJECTION_REJECTED, InputRuntimeStatus.injectionRejected.get())
                        .putExtra(Ipc.EXTRA_INPUT_INJECTION_LAST_RESULT, InputRuntimeStatus.lastInjectionResult)
                        .putExtra(Ipc.EXTRA_INPUT_EDGE_DOWNS, InputRuntimeStatus.edgeDownEvents.get())
                        .putExtra(Ipc.EXTRA_INPUT_ACTIVE_DOWNS, InputRuntimeStatus.activeZoneDowns.get())
                        .putExtra(Ipc.EXTRA_INPUT_KEYGUARD_BLOCKED, InputRuntimeStatus.keyguardBlockedDowns.get())
                        .putExtra(Ipc.EXTRA_AUTOMATION_STATE, automation.state)
                        .putExtra(Ipc.EXTRA_AUTOMATION_ORIGIN, automation.origin)
                        .putExtra(Ipc.EXTRA_AUTOMATION_STEPS, automation.dispatchedSteps)
                        .putExtra(Ipc.EXTRA_AUTOMATION_FAILURES, automation.failedSteps)
                        .putExtra(Ipc.EXTRA_AUTOMATION_RECENT, automation.recentSteps)
                        .putExtra(Ipc.EXTRA_STATUS_STAGE, InputRuntimeStatus.runtimeStage)
                    runCatching {
                        ctx.sendBroadcast(response)
                        module.log(android.util.Log.INFO, "EdgeFlow",
                            "Sent system_server status reply to EdgeFlow")
                    }.onFailure {
                        XLog.w("status: system_server reply failed", it)
                        module.log(android.util.Log.ERROR, "EdgeFlow",
                            "Cannot send status reply from system_server", it)
                    }
                }
            }
            val filter = IntentFilter(Ipc.MODULE_STATUS_REQUEST)
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(listener, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(listener, filter)
            }
            receiver = listener
            systemContext = ctx
            mainHandler.removeCallbacks(contextRetry)
            synchronized(readyListeners) {
                readyListeners.forEach { callback ->
                    runCatching { callback(ctx) }
                        .onFailure { XLog.w("status context listener failed", it) }
                }
            }
            XLog.i("status: registered independent system_server handshake")
            module.log(android.util.Log.INFO, "EdgeFlow", "system_server status receiver registered")
        } catch (t: Throwable) {
            XLog.w("status: failed to register independent heartbeat", t)
            runCatching {
                module.log(android.util.Log.ERROR, "EdgeFlow", "Cannot register system_server heartbeat", t)
            }
        }
    }

    override fun uninstall() {
        mainHandler.removeCallbacks(contextRetry)
        receiver?.let { runCatching { systemContext?.unregisterReceiver(it) } }
        receiver = null
        systemContext = null
    }
}
