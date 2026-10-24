package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig

/** Prevents deleting an Android AppWidget binding that is still used by any action. */
object WidgetReferences {
    fun referencedIds(config: EdgeConfig): Set<Int> {
        val ids = mutableSetOf<Int>()
        fun visit(action: Action?) {
            when (action) {
                is Action.FloatingWidget -> if (action.widgetId > 0) ids += action.widgetId
                is Action.Sequence -> action.actions.forEach(::visit)
                is Action.Repeat -> action.actions.forEach(::visit)
                is Action.Conditional -> {
                    visit(action.thenAction)
                    visit(action.elseAction)
                }
                is Action.EditableAction -> visit(action.innerAction)
                is Action.SubGestures -> {
                    visit(action.hold)
                    visit(action.swipeLeft)
                    visit(action.swipeRight)
                    visit(action.swipeUp)
                    visit(action.swipeDown)
                }
                else -> Unit
            }
        }

        config.gestures.bindings.values.forEach { it.values.forEach(::visit) }
        config.keys.bindings.values.forEach { it.values.forEach(::visit) }
        config.pie.edges.values.forEach {
            it.level1.forEach(::visit)
            it.level2.forEach(::visit)
        }
        config.panels.forEach { it.cells.forEach(::visit) }
        config.shortcutPanels.edges.values.forEach { it.cells.forEach(::visit) }
        config.sideBars.left.forEach(::visit)
        config.sideBars.right.forEach(::visit)
        config.stateTriggers.values.forEach(::visit)
        config.appStateTriggers.values.forEach { it.forEach(::visit) }
        config.schedules.forEach { visit(it.action) }
        config.savedMultiActions.forEach { visit(it.action) }
        config.actionCollection.forEach { visit(it.action) }
        return ids
    }
}
