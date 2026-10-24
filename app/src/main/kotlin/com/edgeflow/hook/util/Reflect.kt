package com.edgeflow.hook.util

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflection helpers for reaching framework internals.
 *
 * Every lookup tolerates absence: a missing member on a future Android release
 * must degrade to "feature unavailable", never to a crash in system_server.
 */
object Reflect {

    fun findClass(name: String, loader: ClassLoader): Class<*>? =
        try {
            Class.forName(name, false, loader)
        } catch (_: Throwable) {
            null
        }

    /** Walks the superclass chain, since several of our targets moved over time. */
    fun findClassAny(loader: ClassLoader, vararg names: String): Class<*>? {
        for (name in names) findClass(name, loader)?.let { return it }
        return null
    }

    /**
     * Finds a method by exact name and parameter types.
     *
     * Parameters are accepted as nullable because the primitive `TYPE` constants
     * (`Int::class.javaPrimitiveType` and friends) are typed `Class<T>?` by the
     * Kotlin runtime, even though they are never null for primitives.
     */
    fun method(cls: Class<*>, name: String, vararg params: Class<*>?): Method? =
        try {
            cls.getDeclaredMethod(name, *params).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }

    /**
     * Finds a method by name and parameter count. Used where the parameter
     * types are not part of the public contract and change between releases
     * (for example `updateVisibility`).
     */
    fun methodByArity(cls: Class<*>, name: String, arity: Int): Method? =
        try {
            cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == arity }
                ?.apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }

    /** Finds any method with the given name, preferring the widest match. */
    fun methodAny(cls: Class<*>, name: String): Method? =
        try {
            cls.declaredMethods.filter { it.name == name }.maxByOrNull { it.parameterCount }
                ?.apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }

    fun field(cls: Class<*>, name: String): Field? =
        try {
            cls.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }

    fun fieldAny(cls: Class<*>, vararg names: String): Field? {
        for (name in names) field(cls, name)?.let { return it }
        return null
    }

    /** Walks up the hierarchy so inherited private fields are reachable. */
    fun fieldDeep(cls: Class<*>, name: String): Field? {
        var current: Class<*>? = cls
        while (current != null) {
            field(current, name)?.let { return it }
            current = current.superclass
        }
        return null
    }

    fun readField(target: Any, name: String): Any? =
        try {
            fieldDeep(target.javaClass, name)?.get(target)
        } catch (_: Throwable) {
            null
        }

    fun readIntField(target: Any, name: String, fallback: Int = 0): Int =
        (readField(target, name) as? Number)?.toInt() ?: fallback

    fun readBooleanField(target: Any, name: String, fallback: Boolean = false): Boolean =
        readField(target, name) as? Boolean ?: fallback

    fun writeField(target: Any, name: String, value: Any?): Boolean {
        val field = fieldDeep(target.javaClass, name) ?: return false
        return try {
            field.set(target, value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun callMethod(target: Any, name: String, vararg args: Any?): Any? {
        val method = methodAny(target.javaClass, name) ?: return null
        return try {
            method.invoke(target, *args)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Finds a nested class whose name ends with [suffix]. Several framework
     * classes we need are nested with unstable prefixes.
     */
    fun nestedClass(owner: Class<*>, suffix: String): Class<*>? =
        try {
            owner.declaredClasses.firstOrNull { it.simpleName == suffix || it.simpleName.endsWith(suffix) }
        } catch (_: Throwable) {
            null
        }

    /** Resolves the static `INSTANCE` field of a system-server service. */
    fun singletonInstance(cls: Class<*>): Any? =
        try {
            cls.getDeclaredField("mInstance").apply { isAccessible = true }.get(null)
        } catch (_: Throwable) {
            null
        }
}

/**
 * Reads a system property, returning [fallback] when it is unset. Used for
 * build- and ROM-specific capability probing.
 */
object SystemProps {
    fun get(key: String, fallback: String = ""): String =
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, fallback) as? String) ?: fallback
        } catch (_: Throwable) {
            fallback
        }

    fun getBoolean(key: String, fallback: Boolean = false): Boolean =
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            (get.invoke(null, key, fallback) as? Boolean) ?: fallback
        } catch (_: Throwable) {
            fallback
        }

    fun getInt(key: String, fallback: Int = 0): Int =
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            (get.invoke(null, key, fallback) as? Int) ?: fallback
        } catch (_: Throwable) {
            fallback
        }
}
