package com.edgeflow.hook

/** Process-local overrides changed by toggle actions without rewriting user configuration. */
internal object RuntimeToggles {
    @Volatile
    var gesturesEnabled: Boolean? = null

    @Volatile
    var keysEnabled: Boolean? = null

    /** Runtime override for original Edge-style game touch/key guard. */
    @Volatile
    var gameMode: Boolean? = null
}
