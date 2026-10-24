package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.Condition

/**
 * Non-destructive initialization for nested editors. Updating a condition,
 * secondary gesture or single parameter must never silently drop siblings.
 */
object ActionEditorDefaults {
    fun condition(action: Action?): Condition =
        (action as? Action.Conditional)?.condition ?: Condition.ScreenOn

    fun thenBranch(action: Action?): Action =
        (action as? Action.Conditional)?.thenAction ?: Action.None

    fun elseBranch(action: Action?): Action =
        (action as? Action.Conditional)?.elseAction ?: Action.None

    fun secondaryGestures(action: Action?): List<Action> {
        val existing = action as? Action.SubGestures
        return listOf(
            existing?.hold ?: Action.None,
            existing?.swipeLeft ?: Action.None,
            existing?.swipeRight ?: Action.None,
            existing?.swipeUp ?: Action.None,
            existing?.swipeDown ?: Action.None,
        )
    }
}
