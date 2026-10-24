package com.edgeflow.hook

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Invokes the framework finish-activity method without linking hidden APIs at compile time. */
internal object ActivityFinishInvoker {

    fun finish(service: Any, activityToken: Any, resultData: Any): Boolean {
        val candidates = allMethods(service.javaClass)
            .filter { it.name == "finishActivity" && it.parameterCount in 3..4 }
            .sortedByDescending(Method::getParameterCount)

        for (method in candidates) {
            val arguments = argumentsFor(method, activityToken, resultData) ?: continue
            val invoked = runCatching {
                method.isAccessible = true
                method.invoke(service, *arguments)
            }
            if (invoked.isSuccess) return true
        }
        return false
    }

    fun removeSingleActivityTask(service: Any, taskId: Int): Boolean {
        val method = allMethods(service.javaClass).firstOrNull { candidate ->
            candidate.name == "removeTask" && candidate.parameterCount == 1 && isInt(candidate.parameterTypes[0])
        } ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(service, taskId)
        }.let { result -> result.isSuccess && result.getOrNull() != false }
    }

    private fun argumentsFor(method: Method, activityToken: Any, resultData: Any): Array<Any?>? {
        val parameters = method.parameterTypes
        val token = tokenForParameter(activityToken, parameters[0]) ?: return null
        if (!isInt(parameters[1]) || !parameters[2].isInstance(resultData)) return null

        return when (parameters.size) {
            3 -> arrayOf(token, 0, resultData)
            4 -> when (parameters[3]) {
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> arrayOf(token, 0, resultData, 0)
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> arrayOf(token, 0, resultData, false)
                else -> null
            }
            else -> null
        }
    }

    private fun tokenForParameter(token: Any, parameterType: Class<*>): Any? {
        if (parameterType.isInstance(token)) return token
        val binder = runCatching {
            token.javaClass.methods.firstOrNull {
                it.name == "asBinder" && it.parameterCount == 0 && Modifier.isPublic(it.modifiers)
            }?.apply { isAccessible = true }?.invoke(token)
        }.getOrNull() ?: return null
        return binder.takeIf(parameterType::isInstance)
    }

    private fun isInt(type: Class<*>): Boolean =
        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType

    private fun allMethods(type: Class<*>): Sequence<Method> = sequence {
        yieldAll(type.methods.asSequence())
        var current: Class<*>? = type
        while (current != null) {
            yieldAll(current.declaredMethods.asSequence())
            current = current.superclass
        }
    }.distinctBy { method ->
        "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }})"
    }
}
