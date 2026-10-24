package com.edgeflow.core

import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.NamedAction

object FloatingAutomationBarPolicy {
    const val MAX_SHORTCUTS = 5

    fun selected(config: EdgeConfig): List<NamedAction> {
        val valid = config.savedMultiActions
            .asSequence().filter { !it.deleted && it.action.id != ActionId.NONE }
            .associateBy { it.id }
        return config.settings.floatingAutomationBarActionIds
            .distinct()
            .take(MAX_SHORTCUTS)
            .mapNotNull(valid::get)
    }

    fun toggle(ids: List<Int>, id: Int, enabled: Boolean): List<Int> {
        if (id <= 0) return ids
        val current = ids.filter { it > 0 }.distinct().take(MAX_SHORTCUTS)
        return if (enabled) (current + id).distinct().take(MAX_SHORTCUTS)
            else current.filterNot { it == id }
    }
}
