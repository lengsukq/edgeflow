package com.edgeflow.hook.input

import android.view.InputEvent
import com.edgeflow.hook.InputInjectionPolicy
import com.edgeflow.hook.InputRuntimeStatus
import com.edgeflow.hook.util.Reflect
import com.edgeflow.hook.util.XLog
import java.lang.reflect.Method

/**
 * The only place that knows how to talk to the native input manager.
 *
 * ## Why reflection is unavoidable
 *
 * `NativeInputManagerService` is a hidden interface, and its implementation
 * (`NativeInputManagerService$NativeImpl`) is a nested class of it. Both are
 * reachable by name, but the *instance* only exists as a field
 * (`InputManagerService.mNative`) of a live service, so the bridge has to be
 * bound to an instance after the fact.
 *
 * ## Failure policy
 *
 * Every operation degrades to a no-op when a member is missing, and reports
 * once. A future Android release that renames `injectInputEvent` should disable
 * gesture injection and leave the rest of the module working, not crash the
 * system server on every touch.
 */
internal class NativeInputBridge private constructor(
    private val native: Any,
    private val injectMethod: Method?,
    private val setFilterEnabledMethod: Method?,
) {

    /**
     * Turns delivery of input events to the filter on or off.
     *
     * The dispatcher short-circuits before calling the Java filter when the
     * native gate is closed, so this must be `true` for gestures to work at all.
     */
    fun setFilterEnabled(enabled: Boolean) {
        val method = setFilterEnabledMethod ?: return
        try {
            method.invoke(native, enabled)
        } catch (t: Throwable) {
            XLog.w("could not set the native input filter state: ${t.message}")
        }
    }

    /**
     * Injects an event through the platform's own path.
     *
     * @param syncMode `INJECT_INPUT_EVENT_MODE_ASYNC`, so injection never blocks
     *                 the caller waiting on the dispatcher
     * @param policyFlags extra policy flags; `FLAG_FILTERED` is added by the
     *                    framework when the event came from a filter
     * @return the raw result code, where [INJECT_SUCCEEDED] means success
     */
    fun inject(event: InputEvent, syncMode: Int, policyFlags: Int): Int {
        val method = injectMethod
        val result = if (method == null) INJECT_FAILED else try {
            (method.invoke(
                native,
                event,
                false,              // injectIntoUid
                -1,                 // uid
                syncMode,
                INJECT_TIMEOUT_MILLIS,
                InputInjectionPolicy.markFiltered(policyFlags),
            ) as? Int) ?: INJECT_FAILED
        } catch (t: Throwable) {
            XLog.w("input injection failed: ${t.message}")
            INJECT_FAILED
        }
        InputRuntimeStatus.injectionResult(result)
        return result
    }

    companion object {
        const val INJECT_SUCCEEDED = 0
        private const val INJECT_FAILED = 1
        private const val INJECT_TIMEOUT_MILLIS = 5000

        /** Signature of `NativeInputManagerService.injectInputEvent`. */
        private val INJECT_PARAMETERS: Array<Class<*>?> = arrayOf(
            InputEvent::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )

        /**
         * Resolves the native manager from a live `InputManagerService`.
         *
         * @return null when the field or the methods are absent on this release
         */
        fun fromInputManager(instance: Any): NativeInputBridge? {
            val native = Reflect.readField(instance, "mNative") ?: run {
                XLog.w("InputManagerService.mNative not reachable; gesture injection and the input filter are unavailable")
                return null
            }
            return fromNativeManager(native)
        }

        /** Called during native input setup, before the very first touch. */
        fun fromNativeManager(native: Any): NativeInputBridge {

            val inject = Reflect.method(native.javaClass, "injectInputEvent", *INJECT_PARAMETERS)
            val setFilter = Reflect.method(
                native.javaClass,
                "setInputFilterEnabled",
                Boolean::class.javaPrimitiveType,
            )

            if (inject == null) {
                XLog.w("injectInputEvent not found on ${native.javaClass.simpleName}; gesture injection disabled")
            }
            if (setFilter == null) {
                XLog.w("setInputFilterEnabled not found on ${native.javaClass.simpleName}; the input filter cannot be enabled")
            }

            return NativeInputBridge(native, inject, setFilter)
        }
    }
}
