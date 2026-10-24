package com.edgeflow.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GesturePlacement {
    /** Preserve original relative screen coordinates. */
    ORIGINAL,
    /** Move the recording's bounding-box centre to the centre of the current screen. */
    CENTER,
    /** Anchor the recording's bounding-box centre to a user selected screen percentage. */
    CUSTOM,
}
