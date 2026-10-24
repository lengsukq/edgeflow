package com.edgeflow.core

import com.edgeflow.core.model.Action

/**
 * Resume only actions that have not been dispatched. Replaying the original
 * workflow could duplicate payments, button clicks or other side effects.
 */
object AutomationContinuation {
    fun remainingAfter(
        steps: List<Action>,
        completedIndex: Int,
        innerContinuation: Action?,
    ): Action? {
        require(completedIndex in steps.indices)
        val remaining = buildList {
            if (innerContinuation != null && innerContinuation.id != ActionId.NONE) add(innerContinuation)
            addAll(steps.drop(completedIndex + 1))
        }
        return remaining.takeIf { it.isNotEmpty() }?.let(Action::Sequence)
    }
}
