package com.edgeflow.hook

import com.edgeflow.core.AutomationRunLog

/** Latest run summary queried using the existing authenticated system status probe. */
internal object AutomationRuntimeStatus {
    val log = AutomationRunLog()
}
