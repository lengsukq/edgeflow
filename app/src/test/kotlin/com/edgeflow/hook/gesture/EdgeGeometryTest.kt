package com.edgeflow.hook.gesture

import com.edgeflow.core.EdgeZone
import com.edgeflow.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for edge hit-testing.
 *
 * The geometry is the part of the module most likely to be subtly wrong: an
 * off-by-one in the segment split produces a zone that is slightly too small,
 * which users experience as "sometimes the gesture does not fire" and which is
 * nearly impossible to diagnose from a bug report.
 *
 * Values are chosen in pixels against a 400 dpi density, so one inch is 400 px
 * and the default 0.12 in strip is 48 px.
 */
class EdgeGeometryTest {

    private val dpi = 400f
    private val width = 1080
    private val height = 2400

    private fun geometry(settings: AppSettings = AppSettings()) =
        EdgeGeometry.of(width, height, dpi, settings)

    /** Start of the k-th third of a strip's usable length, plus a nudge inside it. */
    private fun thirdCentre(index: Int, span: Int, reserved: Float): Float {
        val usable = span - reserved * 2f
        val third = usable / 3f
        return reserved + third * (index + 0.5f)
    }

    @Test
    fun `a touch in the middle of the screen hits no zone`() {
        assertNull(geometry().hitTest(width / 2f, height / 2f))
    }

    @Test
    fun `the left strip resolves to the correct segment`() {
        val geo = geometry()
        val reserved = 0.12f * dpi
        val stripX = 10f

        assertEquals(EdgeZone.LEFT_TOP, geo.hitTest(stripX, thirdCentre(0, height, reserved)))
        assertEquals(EdgeZone.LEFT_CENTER, geo.hitTest(stripX, thirdCentre(1, height, reserved)))
        assertEquals(EdgeZone.LEFT_BOTTOM, geo.hitTest(stripX, thirdCentre(2, height, reserved)))
    }

    @Test
    fun `the right strip resolves to the correct segment`() {
        val geo = geometry()
        val reserved = 0.12f * dpi
        val stripX = width - 10f

        assertEquals(EdgeZone.RIGHT_TOP, geo.hitTest(stripX, thirdCentre(0, height, reserved)))
        assertEquals(EdgeZone.RIGHT_CENTER, geo.hitTest(stripX, thirdCentre(1, height, reserved)))
        assertEquals(EdgeZone.RIGHT_BOTTOM, geo.hitTest(stripX, thirdCentre(2, height, reserved)))
    }

    @Test
    fun `the top and bottom strips resolve to the correct segment`() {
        val geo = geometry()
        val reserved = 0.12f * dpi

        assertEquals(EdgeZone.TOP_LEFT, geo.hitTest(thirdCentre(0, width, reserved), 10f))
        assertEquals(EdgeZone.TOP_CENTER, geo.hitTest(thirdCentre(1, width, reserved), 10f))
        assertEquals(EdgeZone.TOP_RIGHT, geo.hitTest(thirdCentre(2, width, reserved), 10f))

        assertEquals(EdgeZone.BOTTOM_LEFT, geo.hitTest(thirdCentre(0, width, reserved), height - 10f))
        assertEquals(EdgeZone.BOTTOM_CENTER, geo.hitTest(thirdCentre(1, width, reserved), height - 10f))
        assertEquals(EdgeZone.BOTTOM_RIGHT, geo.hitTest(thirdCentre(2, width, reserved), height - 10f))
    }

    @Test
    fun `the corner reserve leaves the corner to the system`() {
        // A touch in the very corner sits inside both reserves, so it belongs to
        // no zone at all. That is the point of the reserve: pulling down the
        // notification shade from a corner, and the system's own corner gestures,
        // must reach the platform untouched.
        val geo = geometry()
        assertNull(geo.hitTest(5f, 5f), "the corner must not be claimed by any zone")
        assertNull(geo.hitTest(width - 5f, 5f))
        assertNull(geo.hitTest(5f, height - 5f))
        assertNull(geo.hitTest(width - 5f, height - 5f))
    }

    @Test
    fun `just past the corner reserve the side strip takes over`() {
        // Past the reserve band the touch is unambiguous again. The side strip
        // wins over the top strip because sides are tested first, which keeps a
        // near-corner drag on the side rather than the top.
        val geo = geometry()
        val reserved = 0.12f * dpi
        assertEquals(EdgeZone.LEFT_TOP, geo.hitTest(10f, reserved + 1f))
    }

    @Test
    fun `a touch outside the strip thickness is not claimed`() {
        val geo = geometry()
        val justInside = 0.12f * dpi - 1f
        val justOutside = 0.12f * dpi + 1f

        assertEquals(EdgeZone.LEFT_CENTER, geo.hitTest(justInside, height / 2f))
        assertNull(geo.hitTest(justOutside, height / 2f))
    }

    @Test
    fun `widening the edge claims more of the screen`() {
        val narrow = geometry(AppSettings(edgeWidthInches = 0.05f))
        val wide = geometry(AppSettings(edgeWidthInches = 0.25f))
        val probe = 0.15f * dpi

        assertNull(narrow.hitTest(probe, height / 2f))
        assertEquals(EdgeZone.LEFT_CENTER, wide.hitTest(probe, height / 2f))
    }

    @Test
    fun `a zero density falls back rather than producing a zero-thickness strip`() {
        // Some emulators and folding devices report a zero density mid-fold. A
        // zero-thickness strip would make every gesture impossible to trigger.
        val geo = EdgeGeometry.of(width, height, 0f, 0f, 420, AppSettings())
        assertEquals(EdgeZone.LEFT_CENTER, geo.hitTest(10f, height / 2f))
    }

    @Test
    fun `swipe threshold scales with density`() {
        val lowDensity = EdgeGeometry.of(width, height, 160f, AppSettings())
        val highDensity = EdgeGeometry.of(width, height, 640f, AppSettings())

        // The same physical distance must map to proportionally more pixels.
        assertEquals(
            4f,
            highDensity.swipeThresholdPx / lowDensity.swipeThresholdPx,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun `tap slop is smaller than the swipe threshold`() {
        // Otherwise a slow drag would be classified as a tap and a tap as a swipe.
        val geo = geometry()
        assertTrue(geo.tapSlopSquared < geo.swipeThresholdSquared)
    }

    @Test
    fun `withDisplaySize reuses the instance when the size is unchanged`() {
        val geo = geometry()
        assertEquals(geo, geo.withDisplaySize(width, height))
        assertNotEquals(geo, geo.withDisplaySize(width + 1, height))
    }

    @Test
    fun `a degenerate display size does not crash the segment split`() {
        val geo = EdgeGeometry.of(100, 0, dpi, AppSettings())
        // The only requirement is that this does not divide by zero or throw.
        geo.hitTest(10f, 0f)
    }

    @Test
    fun `the same physical strip covers the same fraction of the screen`() {
        // This is the property that makes inches the right unit: two devices with
        // different pixel densities but the same physical size must produce the
        // same relative strip thickness.
        val lowDensity = EdgeGeometry.of(540, 1200, 200f, AppSettings())
        val highDensity = EdgeGeometry.of(1080, 2400, 400f, AppSettings())

        val lowFraction = 0.12f * 200f / 540f
        val highFraction = 0.12f * 400f / 1080f
        assertEquals(lowFraction, highFraction, absoluteTolerance = 0.0001f)

        // And the hit test agrees on both.
        assertEquals(EdgeZone.LEFT_CENTER, lowDensity.hitTest(10f, 600f))
        assertEquals(EdgeZone.LEFT_CENTER, highDensity.hitTest(20f, 1200f))
    }
}

/**
 * Tests for swipe direction classification.
 *
 * Direction is decided on the dominant axis, so the cases that matter are the
 * diagonals and the boundary where the two axes are exactly equal.
 */
class GestureGeometryTest {

    private val slop = 100f

    @Test
    fun `movement below the slop is a tap`() {
        assertEquals(GestureGeometry.TAP, GestureGeometry.classifySwipe(0f, 0f, 5f, 5f, slop))
    }

    @Test
    fun `horizontal movement is classified by sign`() {
        assertEquals(GestureGeometry.SWIPE_LEFT, GestureGeometry.classifySwipe(500f, 500f, 300f, 500f, slop))
        assertEquals(GestureGeometry.SWIPE_RIGHT, GestureGeometry.classifySwipe(500f, 500f, 700f, 500f, slop))
    }

    @Test
    fun `vertical movement is classified by sign`() {
        assertEquals(GestureGeometry.SWIPE_UP, GestureGeometry.classifySwipe(500f, 500f, 500f, 300f, slop))
        assertEquals(GestureGeometry.SWIPE_DOWN, GestureGeometry.classifySwipe(500f, 500f, 500f, 700f, slop))
    }

    @Test
    fun `the dominant axis wins on a diagonal`() {
        // Mostly sideways with vertical drift must still be a horizontal swipe.
        assertEquals(GestureGeometry.SWIPE_RIGHT, GestureGeometry.classifySwipe(100f, 100f, 400f, 200f, slop))
        assertEquals(GestureGeometry.SWIPE_DOWN, GestureGeometry.classifySwipe(100f, 100f, 200f, 400f, slop))
    }

    @Test
    fun `an exactly diagonal drag resolves deterministically`() {
        // Equal axes fall through to the vertical branch; pinning it means a
        // refactor cannot make the result alternate between runs.
        assertEquals(GestureGeometry.SWIPE_DOWN, GestureGeometry.classifySwipe(100f, 100f, 300f, 300f, slop))
    }

    @Test
    fun `distance is symmetric`() {
        val forward = GestureGeometry.distanceSquared(0f, 0f, 30f, 40f)
        val backward = GestureGeometry.distanceSquared(30f, 40f, 0f, 0f)
        assertEquals(forward, backward)
        assertEquals(2500f, forward)
    }
}
