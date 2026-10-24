package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.Condition
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionEditorDefaultsTest {
    @Test fun `reopening condition preserves branches and condition`() {
        val saved = Action.Conditional(
            condition = Condition.Not(Condition.ScreenOn),
            thenAction = Action.Delay(500),
            elseAction = Action.InjectTap(15, 30),
        )
        assertEquals(saved.condition, ActionEditorDefaults.condition(saved))
        assertEquals(saved.thenAction, ActionEditorDefaults.thenBranch(saved))
        assertEquals(saved.elseAction, ActionEditorDefaults.elseBranch(saved))
    }

    @Test fun `reopening sub gestures preserves all five bindings and order`() {
        val saved = Action.SubGestures(
            hold = Action.Delay(500),
            swipeLeft = Action.Simple(ActionId.BACK),
            swipeRight = Action.Simple(ActionId.HOME),
            swipeUp = Action.InjectTap(50, 30),
            swipeDown = Action.InputText("test"),
        )
        assertEquals(
            listOf(saved.hold, saved.swipeLeft, saved.swipeRight, saved.swipeUp, saved.swipeDown),
            ActionEditorDefaults.secondaryGestures(saved),
        )
    }

    @Test fun `new nested editor starts empty without overwriting existing actions`() {
        assertEquals(Action.None, ActionEditorDefaults.thenBranch(null))
        assertEquals(Action.None, ActionEditorDefaults.elseBranch(null))
        assertEquals(List(5) { Action.None }, ActionEditorDefaults.secondaryGestures(null))
    }
}
