package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegionSelectionTest {
    @Test fun mapsPreviewPixelsToScreenshotBounds() {
        assertEquals(
            PixelRect(500, 1000, 1500, 2000),
            RegionSelection.toBitmapRect(250f, 500f, 750f, 1000f, 1000f, 1500f, 2000, 3000),
        )
    }
    @Test fun normalizesAndClampsCoordinates() {
        assertEquals(
            PixelRect(0, 0, 100, 100),
            RegionSelection.toBitmapRect(200f, 200f, -50f, -50f, 100f, 100f, 100, 100),
        )
        assertNull(RegionSelection.toBitmapRect(1f, 1f, 1f, 1f, 100f, 100f, 200, 200))
        assertNull(RegionSelection.toBitmapRect(1f, 1f, 30f, 30f, 0f, 100f, 200, 200))
        assertNull(RegionSelection.toBitmapRect(Float.NaN, 1f, 30f, 30f, 100f, 100f, 200, 200))
    }
}
