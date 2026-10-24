package com.edgeflow.core

import com.edgeflow.core.model.GesturePlacement
import com.edgeflow.core.model.GestureStroke
import com.edgeflow.core.model.GestureTracePoint
import com.edgeflow.core.model.RecordedGesture
import kotlin.math.roundToInt

enum class GestureEventPhase { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP }

data class GesturePointerPosition(val id: Int, val x: Int, val y: Int)

data class GesturePlaybackEvent(
    val elapsedMs: Int,
    val phase: GestureEventPhase,
    val pointers: List<GesturePointerPosition>,
    val changedIndex: Int = 0,
)

/** Pure Kotlin planner: interpolate dense frames and generate coherent multi-touch streams.
 * No Android object is allocated before the action worker consumes these events. */
object GesturePlaybackPlan {
    private const val FRAME_INTERVAL_MS = 16
    const val MIN_SPEED = 50
    const val MAX_SPEED = 200

    fun create(
        record: RecordedGesture,
        speedPercent: Int = 100,
        placement: GesturePlacement = GesturePlacement.ORIGINAL,
        rotationDelta: Int = 0,
        anchorXPercent: Int = 50,
        anchorYPercent: Int = 50,
    ): List<GesturePlaybackEvent> {
        require(GestureTracePolicy.valid(record)) { "Invalid gesture recording" }
        require(speedPercent in MIN_SPEED..MAX_SPEED) { "Unsupported playback speed" }
        require(anchorXPercent in 0..100 && anchorYPercent in 0..100) { "Invalid anchor" }
        val strokes = GestureTracePolicy.strokes(record)
        val original = strokes.flatMap { it.points }
        fun rotate(x: Int, y: Int): Pair<Int, Int> = when (Math.floorMod(rotationDelta, 4)) {
            1 -> (9999 - y) to x
            2 -> (9999 - x) to (9999 - y)
            3 -> y to (9999 - x)
            else -> x to y
        }
        val rotated = original.map { rotate(it.x, it.y) }
        val centreX = (rotated.minOf { it.first } + rotated.maxOf { it.first }) / 2
        val centreY = (rotated.minOf { it.second } + rotated.maxOf { it.second }) / 2
        val offsetX = when (placement) {
            GesturePlacement.ORIGINAL -> 0
            GesturePlacement.CENTER -> 5000 - centreX
            GesturePlacement.CUSTOM -> anchorXPercent * 100 - centreX
        }
        val offsetY = when (placement) {
            GesturePlacement.ORIGINAL -> 0
            GesturePlacement.CENTER -> 5000 - centreY
            GesturePlacement.CUSTOM -> anchorYPercent * 100 - centreY
        }
        fun scaled(ms: Int): Int = (ms.toLong() * 100L / speedPercent).toInt()
        data class TimedStroke(val stroke: GestureStroke, val start: Int, val end: Int)
        val segments = strokes.map {
            val start = scaled(it.startMs)
            TimedStroke(it, start, maxOf(start + 1, scaled(it.startMs + it.points.last().elapsedMs)))
        }
        val maxTime = segments.maxOf { it.end }
        val times = sortedSetOf<Int>()
        var t = 0
        while (t <= maxTime) { times += t; t += FRAME_INTERVAL_MS }
        segments.forEach { times += it.start; times += it.end }
        times += maxTime

        fun position(s: TimedStroke, at: Int): GesturePointerPosition {
            val points = s.stroke.points
            val local = at.coerceAtLeast(s.start) - s.start
            val originalTime = local * speedPercent / 100f
            val rightIndex = points.indexOfFirst { it.elapsedMs >= originalTime }
            val right = if (rightIndex < 0) points.last() else points[rightIndex]
            val left = if (rightIndex <= 0) right else points[rightIndex - 1]
            val portion = if (right.elapsedMs == left.elapsedMs) 0f else
                ((originalTime - left.elapsedMs) / (right.elapsedMs - left.elapsedMs)).coerceIn(0f, 1f)
            val x = (left.x + (right.x - left.x) * portion).roundToInt()
            val y = (left.y + (right.y - left.y) * portion).roundToInt()
            val (rx, ry) = rotate(x, y)
            return GesturePointerPosition(s.stroke.pointerId,
                (rx + offsetX).coerceIn(0, 9999), (ry + offsetY).coerceIn(0, 9999))
        }

        val active = linkedMapOf<Int, TimedStroke>()
        val events = ArrayList<GesturePlaybackEvent>()
        for (time in times) {
            // First emit final positions for pointers about to lift.
            val ending = active.values.filter { it.end <= time }.toList()
            if (ending.isNotEmpty()) {
                val endPositions = active.values.map { position(it, time) }
                events += GesturePlaybackEvent(time, GestureEventPhase.MOVE, endPositions)
                for (end in ending) {
                    val index = active.keys.indexOf(end.stroke.pointerId)
                    val pointers = active.values.map { position(it, time) }
                    events += GesturePlaybackEvent(
                        time,
                        if (active.size == 1) GestureEventPhase.UP else GestureEventPhase.POINTER_UP,
                        pointers, index,
                    )
                    active.remove(end.stroke.pointerId)
                }
            }
            segments.filter { it.start == time }.forEach { start ->
                require(start.stroke.pointerId !in active) { "Overlapping pointer identifiers" }
                active[start.stroke.pointerId] = start
                events += GesturePlaybackEvent(
                    time,
                    if (active.size == 1) GestureEventPhase.DOWN else GestureEventPhase.POINTER_DOWN,
                    active.values.map { position(it, time) }, active.size - 1,
                )
            }
            if (active.isNotEmpty() && time < maxTime) {
                events += GesturePlaybackEvent(time, GestureEventPhase.MOVE, active.values.map { position(it, time) })
            }
        }
        require(events.size <= 5000) { "Gesture has too many playback frames" }
        return events
    }
}
