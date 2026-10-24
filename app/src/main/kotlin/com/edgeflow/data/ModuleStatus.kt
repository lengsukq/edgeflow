package com.edgeflow.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.edgeflow.core.Ipc
import com.edgeflow.service.ModuleStatusReceiver
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Whether the module is actually running.
 *
 * Only a fresh reply from system_server proves the module was actually
 * injected there. Ordinary App processes are NOT expected to have the
 * compileOnly libxposed API in their classloader.
 */
data class ModuleStatus(
    val frameworkDetected: Boolean,
    val frameworkName: String?,
    val apiVersion: Int?,
    val scopeConfigured: Boolean,
    val androidRelease: String,
    val androidSdk: Int,
    val frameworkServiceConnected: Boolean = false,
    val frameworkVersion: String? = null,
    val activeScope: Set<String>? = null,
    val inputHookInstalled: Boolean = false,
    val nativeInputBridgeBound: Boolean = false,
    val nativeInputGateRequested: Boolean = false,
    val inputConfigVersion: Int = 0,
    val inputEnabledZones: Int = 0,
    val inputBoundActions: Int = 0,
    val inputTouchCount: Long = 0,
    val inputTriggeredCount: Long = 0,
    /** Native dispatcher acceptance, not proof that the target app responded. */
    val inputInjectionAttempts: Long = 0,
    val inputInjectionAccepted: Long = 0,
    val inputInjectionRejected: Long = 0,
    val lastInjectionResult: Int = -1,
    val automationState: String = "IDLE",
    val automationOrigin: String = "",
    val automationSteps: Int = 0,
    val automationFailures: Int = 0,
    val automationRecent: String = "",
    val systemServerStage: String? = null,
    /** APK was updated after this boot; hook-side classes cannot hot reload. */
    val rebootNeededForCurrentApk: Boolean = false,
    /** NPatch/LSPatch-style app patcher, which cannot inject into system_server. */
    val npatchInstalled: Boolean = false,
) {
    /** What the user should do next, given the observed state. */
    val guidance: Guidance
        get() = when {
            !scopeConfigured -> Guidance.MODULE_PACKAGE_INVALID
            !frameworkDetected -> Guidance.SYSTEM_HOOK_UNCONFIRMED
            else -> Guidance.READY
        }
}

enum class Guidance {
    /** The APK does not contain the expected modern system_server entry. */
    MODULE_PACKAGE_INVALID,
    /** Installed package exists, but system_server did not confirm its hooks. */
    SYSTEM_HOOK_UNCONFIRMED,
    /** A recent, authenticated response came from the system server. */
    READY,
}

/** Probes the live system-server module via a read-only broadcast handshake. */
class ModuleStatusProbe(private val context: Context) {

    fun probe(): ModuleStatus {
        val scopeCorrect = scopeDeclared()
        if (scopeCorrect) requestRuntimeCheck()
        val prefs = context.getSharedPreferences(ModuleStatusReceiver.PREFS, Context.MODE_PRIVATE)
        val elapsed = prefs.getLong(ModuleStatusReceiver.LAST_CONFIRMED_ELAPSED, -1L)
        val age = SystemClock.elapsedRealtime() - elapsed
        val confirmed = scopeCorrect && elapsed > 0 && age in 0..ModuleStatusReceiver.CONFIRMATION_MAX_AGE_MS
        val service = FrameworkServiceState.latest()
        @Suppress("DEPRECATION")
        val lastInstallUpdate = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val rebootNeeded = ModuleRestartPolicy.needsReboot(
            lastUpdateTimeMillis = lastInstallUpdate,
            wallNowMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
        return ModuleStatus(
            frameworkDetected = confirmed,
            frameworkName = service?.frameworkName
                ?: if (confirmed) prefs.getString(ModuleStatusReceiver.LAST_FRAMEWORK, null) else null,
            apiVersion = service?.apiVersion
                ?: if (confirmed) prefs.getInt(ModuleStatusReceiver.LAST_API, 0).takeIf { it > 0 } else null,
            scopeConfigured = scopeCorrect,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            androidSdk = Build.VERSION.SDK_INT,
            frameworkServiceConnected = service != null,
            frameworkVersion = service?.frameworkVersion,
            activeScope = service?.scope,
            inputHookInstalled = if (confirmed) prefs.getBoolean(ModuleStatusReceiver.INPUT_HOOK, false) else false,
            nativeInputBridgeBound = if (confirmed) prefs.getBoolean(ModuleStatusReceiver.INPUT_BRIDGE, false) else false,
            nativeInputGateRequested = if (confirmed) prefs.getBoolean(ModuleStatusReceiver.INPUT_GATE, false) else false,
            inputConfigVersion = if (confirmed) prefs.getInt(ModuleStatusReceiver.INPUT_VERSION, 0) else 0,
            inputEnabledZones = if (confirmed) prefs.getInt(ModuleStatusReceiver.INPUT_ZONES, 0) else 0,
            inputBoundActions = if (confirmed) prefs.getInt(ModuleStatusReceiver.INPUT_BINDINGS, 0) else 0,
            inputTouchCount = if (confirmed) prefs.getLong(ModuleStatusReceiver.INPUT_TOUCHES, 0L) else 0L,
            inputTriggeredCount = if (confirmed) prefs.getLong(ModuleStatusReceiver.INPUT_FIRED, 0L) else 0L,
            inputInjectionAttempts = if (confirmed) prefs.getLong(ModuleStatusReceiver.INPUT_INJECTION_ATTEMPTS, 0L) else 0L,
            inputInjectionAccepted = if (confirmed) prefs.getLong(ModuleStatusReceiver.INPUT_INJECTION_ACCEPTED, 0L) else 0L,
            inputInjectionRejected = if (confirmed) prefs.getLong(ModuleStatusReceiver.INPUT_INJECTION_REJECTED, 0L) else 0L,
            lastInjectionResult = if (confirmed) prefs.getInt(ModuleStatusReceiver.INPUT_INJECTION_LAST_RESULT, -1) else -1,
            automationState = if (confirmed) prefs.getString(ModuleStatusReceiver.AUTOMATION_STATE, "IDLE") ?: "IDLE" else "IDLE",
            automationOrigin = if (confirmed) prefs.getString(ModuleStatusReceiver.AUTOMATION_ORIGIN, "") ?: "" else "",
            automationSteps = if (confirmed) prefs.getInt(ModuleStatusReceiver.AUTOMATION_STEPS, 0) else 0,
            automationFailures = if (confirmed) prefs.getInt(ModuleStatusReceiver.AUTOMATION_FAILURES, 0) else 0,
            automationRecent = if (confirmed) prefs.getString(ModuleStatusReceiver.AUTOMATION_RECENT, "") ?: "" else "",
            systemServerStage = if (confirmed) prefs.getString(ModuleStatusReceiver.LAST_STAGE, null) else null,
            rebootNeededForCurrentApk = rebootNeeded,
        )
    }

    @Synchronized
    private fun requestRuntimeCheck() {
        val preferences = context.getSharedPreferences(ModuleStatusReceiver.PREFS, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val existing = preferences.getString(ModuleStatusReceiver.PENDING_NONCE, null)
        val started = preferences.getLong(ModuleStatusReceiver.PENDING_STARTED_ELAPSED, -1L)
        // A second status read must not invalidate the reply to a still
        // outstanding challenge. The old implementation did precisely that:
        // the 850 ms refresh could overwrite the original nonce.
        if (existing != null && started >= 0 && now - started in 0..10_000L) return
        val nonce = UUID.randomUUID().toString()
        if (!preferences.edit()
                .putString(ModuleStatusReceiver.PENDING_NONCE, nonce)
                .putLong(ModuleStatusReceiver.PENDING_STARTED_ELAPSED, now)
                .commit()) return
        runCatching {
            context.sendBroadcast(
                // system_server registers this dynamic receiver using its
                // android package Context. Targeting android avoids OEM
                // background implicit-broadcast restrictions.
                Intent(Ipc.MODULE_STATUS_REQUEST)
                    .setPackage("android")
                    .putExtra(Ipc.EXTRA_STATUS_NONCE, nonce),
            )
        }.onFailure {
            android.util.Log.w("EdgeFlow", "Cannot request live system hook status", it)
        }
    }

    /**
     * Modern libxposed metadata lives at the APK ZIP root (not Android assets).
     * It declares intended scope only; it does NOT prove the user enabled it.
     */
    private fun scopeDeclared(): Boolean = try {
        ZipFile(context.applicationInfo.sourceDir).use { archive ->
            val scope = archive.getEntry("META-INF/xposed/scope.list") ?: return false
            val initializer = archive.getEntry("META-INF/xposed/java_init.list") ?: return false
            val scopes = archive.getInputStream(scope).bufferedReader().use { it.readLines() }
            val initializers = archive.getInputStream(initializer).bufferedReader().use { it.readLines() }
            "system" in scopes.map(String::trim) &&
                "com.edgeflow.hook.EdgeFlowModule" in initializers.map(String::trim)
        }
    } catch (_: Throwable) {
        false
    }
}

/** Android release names, for the version-compatibility row on the status card. */
object AndroidVersions {

    /**
     * Whether this module claims support for the running release.
     *
     * The supported range is Android 9 through 17, which is the range LSPosed
     * 2.2.1 itself supports. Above that the module still loads and reports, but
     * the UI warns rather than promising correctness.
     */
    fun isSupported(sdk: Int): Boolean = sdk in MIN_SDK..MAX_KNOWN_SDK

    fun describe(sdk: Int): String = when {
        sdk < MIN_SDK -> "Android ${sdkName(sdk)} is older than the supported range"
        sdk > MAX_KNOWN_SDK -> "Android ${sdkName(sdk)} is newer than the verified range"
        else -> "Android ${sdkName(sdk)}"
    }

    private fun sdkName(sdk: Int): String = when (sdk) {
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31, 32 -> "12"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> "SDK $sdk"
    }

    private const val MIN_SDK = 28
    private const val MAX_KNOWN_SDK = 37
}
