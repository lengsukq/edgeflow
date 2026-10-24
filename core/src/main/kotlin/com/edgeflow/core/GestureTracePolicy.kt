package com.edgeflow.core

import com.edgeflow.core.model.GestureTracePoint
import com.edgeflow.core.model.RecordedGesture
import com.edgeflow.core.model.EdgeConfig

/** Hard limits protect configuration storage and system_server playback. */
object GestureTracePolicy {
    const val MAX_POINTS = 256
    const val MAX_LENGTH_MS = 8000
    const val MAX_RECORDS = 20

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
            elapsedMs < 0L || elapsedMs > MAX_LENGTH_MS || points.size >= MAX_POINTS
        ) return points
        val point = GestureTracePoint(
            (x / width * 10000).toInt().coerceIn(0, 10000),
            (y / height * 10000).toInt().coerceIn(0, 10000),
            elapsedMs.toInt(),
        )
        if (points.lastOrNull() == point) return points
        return points + point
    }

    fun valid(record: RecordedGesture): Boolean =
        record.id.isNotBlank() && record.id.length <= 80 &&
            record.name.length <= 80 &&
            record.points.size in 2..MAX_POINTS &&
            record.points.first().elapsedMs == 0 &&
            record.points.last().elapsedMs <= MAX_LENGTH_MS &&
            record.points.all { it.x in 0..10000 && it.y in 0..10000 && it.elapsedMs in 0..MAX_LENGTH_MS } &&
            record.points.zipWithNext().all { (a, b) -> a.elapsedMs <= b.elapsedMs }

    fun px(normalized: Int, dimension: Int): Float =
        (normalized.coerceIn(0, 10000) / 10000f) * dimension.coerceAtLeast(1)
}
