package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.GesturePlacement
import com.edgeflow.core.model.GestureStroke
import com.edgeflow.core.model.GestureTracePoint
import com.edgeflow.core.model.RecordedGesture
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GesturePlaybackPlanTest {
    private fun stroke(
        id: Int, start: Int, end: Int, firstX: Int = 1000, lastX: Int = 7000,
    ) = GestureStroke(
        pointerId = id, startMs = start,
        points = listOf(
            GestureTracePoint(firstX, 2000, 0),
            GestureTracePoint(lastX, 8000, end),
        ),
    )
    private fun record(vararg strokes: GestureStroke) = RecordedGesture(
        id = "sample",
        name = "sample",
        points = strokes.first().points,
        strokes = strokes.toList(),
        sourceWidth = 1080,
        sourceHeight = 2400,
        sourceRotation = 0,
    )

    @Test fun longRecordingKeepsFirstAndLastSampleAfterCompaction() {
        var samples = emptyList<GestureTracePoint>()
        for (i in 0..999) {
            samples = GestureTracePolicy.addSample(samples, i.toFloat(), 100f,
                1000, 1000, i.toLong() * 15)
        }
        assertEquals(GestureTracePolicy.MAX_POINTS, samples.size)
        assertEquals(0, samples.first().elapsedMs)
        assertEquals(999 * 15, samples.last().elapsedMs)
        assertEquals(9990, samples.last().x)
        assertTrue(GestureTracePolicy.valid(RecordedGesture("1", "long", samples)))
    }

    @Test fun strokeEditorPreservesRecordingIdAndRejectsInvalidEdits() {
        val original = record(stroke(0, 0, 300), stroke(1, 100, 220))
        val moved = GestureTracePolicy.moveStroke(original, 1, 200, -200)
        assertEquals(original.id, moved.id)
        assertEquals(original.strokes.first().points, moved.strokes.first().points)
        assertEquals(1200, moved.strokes[1].points.first().x)
        assertTrue(GestureTracePolicy.valid(moved))
        assertEquals(original, GestureTracePolicy.moveStroke(original, 1, 5000, 0))
        val removed = GestureTracePolicy.withoutStroke(moved, 0)
        assertEquals(original.id, removed.id)
        assertEquals(1, removed.strokes.size)
        assertEquals(0, removed.strokes.single().startMs)
        assertTrue(GestureTracePolicy.valid(removed))
        assertEquals(removed, GestureTracePolicy.withoutStroke(removed, 0))
    }

    @Test fun legacyRecordingJsonStillDecodesAndPlays() {
        val old = """{"type":"replay_gesture","recordingId":"legacy","id":44}"""
        val replay = ConfigCodec.json.decodeFromString<Action>(old) as Action.ReplayGesture
        assertEquals(100, replay.speedPercent)
        assertEquals(GesturePlacement.ORIGINAL, replay.placement)
        val legacy = RecordedGesture("legacy", "Old", stroke(0, 0, 300).points)
        assertTrue(GestureTracePolicy.valid(legacy))
        val events = GesturePlaybackPlan.create(legacy)
        assertEquals(GestureEventPhase.DOWN, events.first().phase)
        assertEquals(GestureEventPhase.UP, events.last().phase)
        assertTrue(events.any { it.phase == GestureEventPhase.MOVE && it.elapsedMs in 1..299 })
    }

    @Test fun twoFingerStrokeHasCorrectPointerTransitions() {
        val gesture = record(stroke(0, 0, 600), stroke(1, 150, 300))
        assertTrue(GestureTracePolicy.valid(gesture))
        val events = GesturePlaybackPlan.create(gesture)
        val down = events.single { it.phase == GestureEventPhase.POINTER_DOWN }
        assertEquals(2, down.pointers.size)
        assertEquals(1, down.changedIndex)
        val up = events.single { it.phase == GestureEventPhase.POINTER_UP }
        assertEquals(2, up.pointers.size)
        assertEquals(1, up.changedIndex)
        assertEquals(GestureEventPhase.UP, events.last().phase)
    }

    @Test fun sequentialStrokesReleaseAndRestartTheTouchStream() {
        val gesture = record(stroke(0, 0, 120), stroke(0, 300, 120))
        val events = GesturePlaybackPlan.create(gesture)
        assertEquals(2, events.count { it.phase == GestureEventPhase.DOWN })
        assertEquals(2, events.count { it.phase == GestureEventPhase.UP })
        assertTrue(events.indexOfFirst { it.phase == GestureEventPhase.UP } <
            events.indexOfLast { it.phase == GestureEventPhase.DOWN })
    }

    @Test fun speedAndScreenCenterAnchorAreApplied() {
        val gesture = record(stroke(0, 0, 400, 0, 2000))
        val twice = GesturePlaybackPlan.create(gesture, 200)
        assertEquals(200, twice.last().elapsedMs)
        val slow = GesturePlaybackPlan.create(gesture, 50)
        assertEquals(800, slow.last().elapsedMs)
        val centered = GesturePlaybackPlan.create(gesture, 100, GesturePlacement.CENTER)
        assertEquals(4000, centered.first().pointers.first().x)
        val rotated = GesturePlaybackPlan.create(gesture, rotationDelta = 1)
        assertEquals(7999, rotated.first().pointers.first().x)
        val custom = GesturePlaybackPlan.create(
            gesture, placement = GesturePlacement.CUSTOM,
            anchorXPercent = 70, anchorYPercent = 20,
        )
        assertEquals(6000, custom.first().pointers.first().x)
        assertEquals(0, custom.first().pointers.first().y)
        assertFailsWith<IllegalArgumentException> {
            GesturePlaybackPlan.create(gesture, placement = GesturePlacement.CUSTOM, anchorXPercent = 101)
        }
        assertFailsWith<IllegalArgumentException> { GesturePlaybackPlan.create(gesture, 10) }
    }

    @Test fun schemaKeepsAllStrokeDataAndPlaybackControls() {
        val original = record(stroke(0, 0, 500), stroke(1, 110, 300))
        val encoded = ConfigCodec.json.encodeToString(original)
        assertEquals(original, ConfigCodec.json.decodeFromString<RecordedGesture>(encoded))
        val action: Action = Action.ReplayGesture("sample", speedPercent = 75,
            placement = GesturePlacement.CUSTOM, anchorXPercent = 70, anchorYPercent = 25)
        assertEquals(action, ConfigCodec.json.decodeFromString<Action>(
            ConfigCodec.json.encodeToString<Action>(action)))
    }

    @Test fun overlappingThreeFingersAndInvalidTimedStrokesAreRejected() {
        assertFalse(GestureTracePolicy.valid(record(
            stroke(0, 0, 500), stroke(1, 10, 500), stroke(2, 20, 500),
        )))
        assertFalse(GestureTracePolicy.valid(record(
            stroke(0, 0, 500), stroke(0, 200, 500),
        )))
        assertFalse(GestureTracePolicy.valid(record(stroke(0, 0, 21000))))
        assertFalse(GestureTracePolicy.valid(record(
            stroke(0, 0, 8000), stroke(0, 9000, 8000), stroke(0, 18000, 5000),
        )))
    }
}
