package com.edgeflow.hook.gesture

import com.edgeflow.core.model.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubGestureRecognizerTest {
    private val config = Action.SubGestures(
        hold = Action.Back,
        swipeLeft = Action.Home,
        swipeRight = Action.Recents,
        swipeUp = Action.MediaNext,
        swipeDown = Action.MediaPrevious,
    )

    private fun session() = SubGestureRecognizer(config, pointerId = 7, originX = 200f, originY = 300f, thresholdPx = 45f)

    @Test fun `secondary swipe resolves to its own direction and fires once`() {
        val session = session()
        assertNull(session.move(7, 190f, 300f))
        assertEquals(Action.Home, session.move(7, 100f, 310f))
        assertNull(session.move(7, 200f, 100f))
        assertNull(session.hold())
        assertTrue(session.isResolved())
    }

    @Test fun `hold fires without secondary movement and never repeats`() {
        val session = session()
        assertNull(session.move(7, 215f, 315f))
        assertEquals(Action.Back, session.hold())
        assertNull(session.hold())
        assertNull(session.move(7, 500f, 300f))
    }

    @Test fun `other pointer cannot select a secondary action`() {
        val session = session()
        assertNull(session.move(8, 900f, 300f))
        assertEquals(Action.MediaNext, session.move(7, 200f, 150f))
    }

    @Test fun `diagonal gesture uses dominant axis and unset action does nothing`() {
        val onlyUp = SubGestureRecognizer(
            Action.SubGestures(swipeUp = Action.Back), 7, 100f, 100f, 40f,
        )
        assertNull(onlyUp.move(7, 190f, 140f))
        assertTrue(onlyUp.isResolved())
        assertNull(onlyUp.hold())
    }

    @Test fun `all secondary directions map to configured children`() {
        assertEquals(Action.Recents, session().move(7, 270f, 300f))
        assertEquals(Action.MediaNext, session().move(7, 200f, 220f))
        assertEquals(Action.MediaPrevious, session().move(7, 200f, 380f))
    }
}
