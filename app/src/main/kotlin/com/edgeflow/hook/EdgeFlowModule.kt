package com.edgeflow.hook

import com.edgeflow.hook.util.XLog
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable

/**
 * Module entry point.
 *
 * The framework calls [onModuleLoaded] once per process and
 * [onSystemServerStarting] when the host is system_server. Edge gesture
 * interception only makes sense inside system_server, so every other process
 * detaches immediately: that stops further lifecycle callbacks and lets the
 * module classloader become collectible.
 */
class EdgeFlowModule : XposedModule() {

    private var runtime: SystemServerRuntime? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(Log.INFO, "EdgeFlow", "module loaded: process=${param.processName}, systemServer=${param.isSystemServer()}")
        XLog.i(
            "loaded into ${param.processName} (systemServer=${param.isSystemServer()}), " +
                "framework=${getFrameworkName()} ${getFrameworkVersion()}, api=${getApiVersion()}",
        )
        if (!param.isSystemServer()) {
            runCatching { detach() }
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log(Log.INFO, "EdgeFlow", "onSystemServerStarting invoked")
        XLog.guarded("system server startup") {
            if (runtime != null) {
                XLog.d("system server runtime already started; ignoring duplicate callback")
                return@guarded
            }
            try {
                runtime = SystemServerRuntime(this, param.classLoader).apply { start() }
                log(Log.INFO, "EdgeFlow", "SystemServerRuntime.start completed")
            } catch (t: Throwable) {
                log(Log.ERROR, "EdgeFlow", "SystemServerRuntime.start failed", t)
                throw t
            }
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // A live swap would have to unwind every hook, worker thread and overlay
        // inside system_server. Refusing is safer than a half-reloaded module.
        XLog.w("refusing hot reload: edge input hooks cannot be swapped live")
        return false
    }
}

/**
 * Owns every hook and long-lived object living inside system_server.
 *
 * The constructor is side-effect free; [start] does all the work, so a failure
 * part-way through leaves a coherent partially-working module rather than a
 * half-built object graph.
 */
internal class SystemServerRuntime(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
) {

    private val hooks = HookRegistrar(module)
    private val started = mutableListOf<ManagedComponent>()

    fun start() {
        XLog.i("starting runtime on SDK ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.DISPLAY})")

        // Critical: the health channel must come online even if loading remote
        // preferences or initializing an OEM input service fails afterwards.
        InputRuntimeStatus.runtimeStage = "installing-status"
        val health = SystemServerStatusHook(module, classLoader)
        XLog.guarded("install independent system status") {
            health.install(hooks)
            started += health
        }

        InputRuntimeStatus.runtimeStage = "loading-remote-config"
        val repository = ConfigRepository(module).also { it.reload() }
        health.onStatusRequest {
            repository.reload()
            InputRuntimeStatus.configUpdated(repository.config)
        }
        InputRuntimeStatus.runtimeStage = "constructing-input"
        val executor = ActionExecutor(module, repository)

        val inputInterceptor = InputInterceptor(module, classLoader, repository, executor)
        val keyInterceptor = KeyInterceptor(module, classLoader, repository, executor)
        inputInterceptor.setEarlyVolumeKeyHandler(keyInterceptor::onEarlyVolumeKeyEvent)
        val components = listOf(
            inputInterceptor,
            keyInterceptor,
            SystemStateObserver(module, classLoader, repository, executor, inputInterceptor),
        )

        for (component in components) {
            InputRuntimeStatus.runtimeStage = "installing-${component.name}"
            XLog.guarded("install ${component.name}") {
                component.install(hooks)
                started += component
            }
        }

        // Only initialize services once ActivityManager and normal broadcasts
        // are ready; no hooks on SystemServer.start* methods are necessary.
        health.onContextReady { ctx ->
            InputRuntimeStatus.runtimeStage = "binding-system-context"
            (components.last() as SystemStateObserver).onSystemContextReady(ctx)
            InputRuntimeStatus.runtimeStage = "running"
        }
        InputRuntimeStatus.runtimeStage = "running"
        XLog.i("runtime ready: ${hooks.size} hooks from ${started.size}/${components.size} components")
    }

    fun shutdown() {
        started.asReversed().forEach { runCatching { it.uninstall() } }
        started.clear()
        hooks.unhookAll()
    }
}

/** A subsystem that can be installed into and removed from the running module. */
internal interface ManagedComponent {
    val name: String
    fun install(hooks: HookRegistrar)
    fun uninstall() = Unit
}

/**
 * Central place where hooks are created.
 *
 * Every hook is wrapped so that a throwing body can never propagate into the
 * framework: an exception escaping a hook callback can crash system_server.
 * Failures to install are logged and skipped, because a method that moved
 * between Android releases should disable one feature, not the whole module.
 */
internal class HookRegistrar(private val module: XposedModule) {

    private val handles = mutableListOf<XposedInterface.HookHandle>()

    val size: Int get() = handles.size

    fun install(
        what: String,
        target: Executable?,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        body: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle? {
        if (target == null) {
            XLog.w("skipping $what: target method not present on this release")
            return null
        }
        return try {
            val handle = module.hook(target)
                .setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    // Never catch an exception from chain.proceed() and call
                    // chain.proceed() again. Doing so executed Android's
                    // SystemServer.startOtherServices twice, contributing to
                    // a boot-time DropBoxManagerService registration crash.
                    // PROTECTIVE mode in libxposed handles hook-only errors
                    // and propagates original-method errors correctly.
                    body(chain)
                }
            handles += handle
            XLog.d("hooked $what")
            handle
        } catch (t: Throwable) {
            XLog.w("could not hook $what: ${t.message}")
            null
        }
    }

    fun unhookAll() {
        handles.forEach { runCatching { it.unhook() } }
        handles.clear()
    }
}
