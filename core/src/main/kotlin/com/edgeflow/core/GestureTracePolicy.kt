package com.edgeflow.core

import com.edgeflow.core.model.GestureTracePoint
import com.edgeflow.core.model.RecordedGesture
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.core.model.GestureStroke
import kotlin.math.abs

/** Hard limits protect configuration storage and system_server playback. */
object GestureTracePolicy {
    const val MAX_POINTS = 256
    const val MAX_LENGTH_MS = 20000
    const val MAX_RECORDS = 20
    const val MAX_STROKES = 12
    const val MAX_SIMULTANEOUS_POINTERS = 2

    /**
     * Never evict old recordings when adding a new one: existing actions may
     * reference their IDs, and silently dropping a bound recording corrupts
     * the user's automation. Ask the user to delete an unbound stroke instead.
     */
    fun append(config: EdgeConfig, record: RecordedGesture): EdgeConfig {
        require(valid(record)) { "Recorded gesture is invalid" }
        require(config.recordedGestures.none { it.id == record.id }) { "Recording ID already exists" }
        check(config.recordedGestures.size < MAX_RECORDS) {
            "Gesture recording limit reached ($MAX_RECORDS). Remove an unused recording first."
        }
        return config.copy(recordedGestures = config.recordedGestures + record)
    }

    fun addSample(
        points: List<GestureTracePoint>,
        x: Float, y: Float, width: Int, height: Int, elapsedMs: Long,
    ): List<GestureTracePoint> {
        if (!x.isFinite() || !y.isFinite() || width <= 0 || height <= 0 ||
            elapsedMs < 0L || elapsedMs > MAX_LENGTH_MS
        ) return points
        val point = GestureTracePoint(
            (x / width * 10000).toInt().coerceIn(0, 10000),
            (y / height * 10000).toInt().coerceIn(0, 10000),
            elapsedMs.toInt(),
        )
        if (points.lastOrNull() == point) return points
        if (points.size < MAX_POINTS) return points + point
        // Keep beginning, newest end and salient bends. Do not silently truncate
        // a stroke once sampling reaches the cap.
        val mutable = (points + point).toMutableList()
        var indexToDrop = 1
        var cheapest = Float.POSITIVE_INFINITY
        for (i in 1 until mutable.lastIndex) {
            val a = mutable[i - 1]
            val b = mutable[i]
            val c = mutable[i + 1]
            val span = (c.elapsedMs - a.elapsedMs).coerceAtLeast(1)
            val t = (b.elapsedMs - a.elapsedMs).toFloat() / span
            val px = a.x + (c.x - a.x) * t
            val py = a.y + (c.y - a.y) * t
            val distance = abs(b.x - px) + abs(b.y - py)
            // Favor points around pauses even when the finger barely moves.
            val penalty = (minOf(b.elapsedMs - a.elapsedMs, c.elapsedMs - b.elapsedMs) / 25f)
            val cost = distance + penalty
            if (cost < cheapest) {
                cheapest = cost
                indexToDrop = i
            }
        }
        mutable.removeAt(indexToDrop)
        return mutable
    }

    /** Older recordings had only [RecordedGesture.points]; do not require a migration. */
    fun strokes(record: RecordedGesture): List<GestureStroke> =
        record.strokes.ifEmpty { listOf(GestureStroke(0, 0, record.points)) }

    fun duration(record: RecordedGesture): Int =
        strokes(record).maxOfOrNull { it.startMs + (it.points.lastOrNull()?.elapsedMs ?: 0) } ?: 0

    /** Non-destructive editing: all actions keep their stable recording ID. */
    fun withoutStroke(record: RecordedGesture, index: Int): RecordedGesture {
        val existing = strokes(record)
        if (index !in existing.indices || existing.size == 1) return record
        val remaining = existing.filterIndexed { i, _ -> i != index }
        val first = remaining.minOf { it.startMs }
        val rebased = remaining.map { it.copy(startMs = it.startMs - first) }
        val edited = record.copy(points = rebased.first().points, strokes = rebased)
        return edited.takeIf(::valid) ?: record
    }

    fun moveStroke(record: RecordedGesture, index: Int, deltaX: Int, deltaY: Int): RecordedGesture {
        val existing = strokes(record)
        if (index !in existing.indices || abs(deltaX) > 1000 || abs(deltaY) > 1000) return record
        val moved = existing.mapIndexed { i, stroke ->
            if (i != index) stroke else stroke.copy(points = stroke.points.map { point ->
                point.copy(
                    x = (point.x + deltaX).coerceIn(0, 10000),
                    y = (point.y + deltaY).coerceIn(0, 10000),
                )
            })
        }
        val edited = record.copy(points = moved.first().points, strokes = moved)
        return edited.takeIf(::valid) ?: record
    }

    fun valid(record: RecordedGesture): Boolean {
        if (record.id.isBlank() || record.id.length > 80 ||
            record.name.length > 80 || record.sourceWidth < 0 || record.sourceHeight < 0 ||
            record.sourceRotation !in -1..3
        ) return false
        val all = strokes(record)
        if (all.size !in 1..MAX_STROKES || duration(record) > MAX_LENGTH_MS) return false
        if (all.any { stroke ->
            stroke.pointerId !in 0..31 || stroke.startMs !in 0..MAX_LENGTH_MS ||
                stroke.points.size !in 2..MAX_POINTS ||
                stroke.points.first().elapsedMs != 0 ||
                stroke.points.any { it.x !in 0..10000 || it.y !in 0..10000 ||
                    it.elapsedMs !in 0..MAX_LENGTH_MS } ||
                stroke.points.zipWithNext().any { (a, b) -> a.elapsedMs > b.elapsedMs }
        }) return false
        // Reject ambiguous concurrent/re-used pointer IDs, and >2 concurrent contacts.
        val timeline = all.flatMap { stroke ->
            listOf(stroke.startMs to 1, (stroke.startMs + maxOf(1, stroke.points.last().elapsedMs)) to -1)
        }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        var active = 0
        for ((_, delta) in timeline) {
            active += delta
            if (active > MAX_SIMULTANEOUS_POINTERS) return false
        }
        return all.groupBy { it.pointerId }.all { (_, sameId) ->
            sameId.sortedBy { it.startMs }.zipWithNext().all { (a, b) ->
                a.startMs + a.points.last().elapsedMs <= b.startMs
            }
        }
    }

    fun px(normalized: Int, dimension: Int): Float =
        (normalized.coerceIn(0, 10000) / 10000f) * dimension.coerceAtLeast(1)
}
