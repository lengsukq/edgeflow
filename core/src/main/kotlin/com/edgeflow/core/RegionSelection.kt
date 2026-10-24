package com.edgeflow.core

import kotlin.math.ceil
import kotlin.math.floor

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left
    fun height() = bottom - top
}

/** Maps a user rectangle on a letterboxed preview back to screenshot pixels. */
object RegionSelection {
    fun toBitmapRect(
        left: Float, top: Float, right: Float, bottom: Float,
        previewWidth: Float, previewHeight: Float,
        imageWidth: Int, imageHeight: Int,
    ): PixelRect? {
        if (!listOf(left, top, right, bottom, previewWidth, previewHeight).all(Float::isFinite) ||
            previewWidth <= 0f || previewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0
        ) return null
        val x1 = floor(minOf(left, right).coerceIn(0f, previewWidth) / previewWidth * imageWidth).toInt()
        val y1 = floor(minOf(top, bottom).coerceIn(0f, previewHeight) / previewHeight * imageHeight).toInt()
        val x2 = ceil(maxOf(left, right).coerceIn(0f, previewWidth) / previewWidth * imageWidth).toInt()
        val y2 = ceil(maxOf(top, bottom).coerceIn(0f, previewHeight) / previewHeight * imageHeight).toInt()
        return PixelRect(
            x1.coerceIn(0, imageWidth), y1.coerceIn(0, imageHeight),
            x2.coerceIn(0, imageWidth), y2.coerceIn(0, imageHeight),
        ).takeIf { it.width() >= 2 && it.height() >= 2 }
    }
}
