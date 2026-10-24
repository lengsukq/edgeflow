package com.edgeflow.core.model

import kotlinx.serialization.Serializable

/** One explicitly recorded, single-finger stroke. Coordinates in [0, 10000]. */
@Serializable
data class RecordedGesture(
    val id: String,
    val name: String,
    val points: List<GestureTracePoint>,
)

@Serializable
data class GestureTracePoint(
    val x: Int,
    val y: Int,
    val elapsedMs: Int,
)
