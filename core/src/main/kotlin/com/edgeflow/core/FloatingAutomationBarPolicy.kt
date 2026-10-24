package com.edgeflow.core

import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.NamedAction

object FloatingAutomationBarPolicy {
    const val MAX_SHORTCUTS = 5
    const val SECTORS_PER_RING = 6

    /** Missing ring assignments inherit the original five pinned actions, without mutating them. */
    fun ringSlots(config: EdgeConfig): List<NamedAction?> {
        val valid = config.savedMultiActions.asSequence()
            .filter { !it.deleted && it.action.id != ActionId.NONE }
            .associateBy { it.id }
        val ringCount = config.settings.floatingAutomationBarRingCount.coerceIn(2, 3)
        val legacy = config.settings.floatingAutomationBarActionIds
        return List(ringCount * SECTORS_PER_RING) { index ->
            val assigned = config.settings.floatingAutomationBarRingActionIds[index]
                ?: legacy.getOrNull(index)
            assigned?.let(valid::get)
        }
    }

    fun assignRingSlot(ids: Map<Int, Int>, slot: Int, taskId: Int?): Map<Int, Int> {
        if (slot !in 0 until 3 * SECTORS_PER_RING) return ids
        // 0 explicitly clears a legacy-inherited pin; absent key still inherits it.
        return ids + (slot to (taskId?.takeIf { it > 0 } ?: 0))
    }

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
