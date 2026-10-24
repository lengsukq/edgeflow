package com.edgeflow.core

import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.RecordedGesture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GestureTracePolicyTest {
    @Test fun `recording capacity never deletes a referenced stroke`() {
        val points = listOf(
            com.edgeflow.core.model.GestureTracePoint(100, 200, 0),
            com.edgeflow.core.model.GestureTracePoint(300, 500, 80),
        )
        val existing = (0 until GestureTracePolicy.MAX_RECORDS).map { index ->
            RecordedGesture("record-$index", "Stroke $index", points)
        }
        val full = EdgeConfig(recordedGestures = existing)
        assertFailsWith<IllegalStateException> {
            GestureTracePolicy.append(full, RecordedGesture("new-record", "New", points))
        }
        assertEquals(existing, full.recordedGestures)
    }

    @Test fun `adding a gesture appends without reordering existing ids`() {
        val points = listOf(
            com.edgeflow.core.model.GestureTracePoint(0, 0, 0),
            com.edgeflow.core.model.GestureTracePoint(10000, 10000, 100),
        )
        val first = RecordedGesture("one", "One", points)
        val second = RecordedGesture("two", "Two", points)
        val result = GestureTracePolicy.append(
            EdgeConfig(recordedGestures = listOf(first)), second,
        )
        assertEquals(listOf(first, second), result.recordedGestures)
        assertFailsWith<IllegalArgumentException> { GestureTracePolicy.append(result, second) }
    }

    @Test fun normalizeAndRejectBadSamples() {
        val first = GestureTracePolicy.addSample(emptyList(), 200f, 100f, 400, 400, 0L)
        assertEquals(5000, first.first().x)
        assertEquals(2500, first.first().y)
        assertEquals(first, GestureTracePolicy.addSample(first, Float.NaN, 2f, 400, 400, 1L))
        assertEquals(first, GestureTracePolicy.addSample(first, 2f, 2f, 400, 400, 9_000L))
        assertEquals(400f, GestureTracePolicy.px(10000, 400))
    }

    @Test fun validatesTimedStrokeAndSerializesReplay() {
        val points = GestureTracePolicy.addSample(
            GestureTracePolicy.addSample(emptyList(), 0f, 0f, 100, 100, 0),
            100f, 100f, 100, 100, 900,
        )
        val record = RecordedGesture("r1", "Swipe", points)
        assertTrue(GestureTracePolicy.valid(record))
        assertFalse(GestureTracePolicy.valid(record.copy(points = points.reversed())))
        val config = EdgeConfig(recordedGestures = listOf(record))
        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), config)
        assertEquals(config, ConfigCodec.json.decodeFromString<EdgeConfig>(encoded))
        val replay: Action = Action.ReplayGesture("r1")
        assertEquals(replay, ConfigCodec.json.decodeFromString<Action>(
            ConfigCodec.json.encodeToString(Action.serializer(), replay)))
    }
}
