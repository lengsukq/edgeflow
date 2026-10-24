package com.edgeflow.core

/** Legacy Android 9/10 policy_control adapter. API 30+ does not support it. */
object ImmersivePolicy {
    enum class Bar(val token: String) {
        STATUS("immersive.status=*"),
        NAVIGATION("immersive.navigation=*"),
    }

    /**
     * Returns null on a conflicting preexisting policy so EdgeFlow does not
     * overwrite a policy configured by the user or another root module.
     */
    fun update(existing: String?, bar: Bar, enabled: Boolean): String? {
        val tokens = existing.orEmpty().split(':')
            .map(String::trim).filter(String::isNotEmpty)
        if (tokens.any { it.startsWith("immersive.full=") }) return null
        val prefix = bar.token.substringBefore('=')
        if (tokens.any { it.startsWith("$prefix=") && it != bar.token }) return null
        if (enabled && tokens.contains(bar.token)) return null
        val next = tokens.filterNot { it == bar.token }.toMutableList()
        if (enabled) next.add(bar.token)
        return next.joinToString(":")
    }
}
