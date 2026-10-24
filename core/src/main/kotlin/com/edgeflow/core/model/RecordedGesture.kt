package com.edgeflow.core.model

import kotlinx.serialization.Serializable

/** One explicitly recorded, single-finger stroke. Coordinates in [0, 10000]. */
@Serializable
data class RecordedGesture(
    val id: String,
    val name: String,
    val points: List<GestureTracePoint>,
    /** V2 supports consecutive strokes and overlapping pointers. Empty means legacy single stroke. */
    val strokes: List<GestureStroke> = emptyList(),
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    /** Rotation of the display while recording; -1 for pre-V2 recordings. */
    val sourceRotation: Int = -1,
)

/** Each pointer path has times relative to its own down, and a start time relative
 * to the first contact. Intersecting time ranges encode simultaneous fingers. */
@Serializable
data class GestureStroke(
    val pointerId: Int,
    val startMs: Int,
    val points: List<GestureTracePoint>,
)

@Serializable
data class GestureTracePoint(
    val x: Int,
    val y: Int,
    val elapsedMs: Int,
)
