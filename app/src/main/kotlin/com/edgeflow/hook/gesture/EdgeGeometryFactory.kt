package com.edgeflow.hook.gesture

import android.content.Context
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.graphics.Point
import android.view.Display
import com.edgeflow.core.model.AppSettings

/**
 * Builds [EdgeGeometry] from the running device's display metrics.
 *
 * Kept separate from [EdgeGeometry] so the geometry itself stays free of Android
 * dependencies and can be unit-tested on the JVM. Only this file needs a device.
 *
 * [Resources.getSystem] is used rather than a `Context`: the injected code lives
 * in system_server, which has no activity context of its own, and the system
 * resources report the same metrics for the default display.
 */
internal object EdgeGeometryFactory {

    /** Use the real display, not the system resource's potentially boot-time-sized metrics. */
    fun fromDisplay(context: Context, settings: AppSettings): EdgeGeometry {
        return runCatching {
            val display = context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return@runCatching null
            val size = Point()
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            if (size.x <= 0 || size.y <= 0) return@runCatching null
            EdgeGeometry.of(
                widthPx = size.x,
                heightPx = size.y,
                xdpi = metrics.xdpi,
                ydpi = metrics.ydpi,
                densityDpi = metrics.densityDpi,
                settings = settings,
            )
        }.getOrNull() ?: fromSystem(settings)
    }

    /**
     * @return geometry for the default display, using the current rotation's
     *         reported width and height
     */
    fun fromSystem(settings: AppSettings): EdgeGeometry {
        val metrics = Resources.getSystem().displayMetrics
        return EdgeGeometry.of(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            densityDpi = metrics.densityDpi,
            settings = settings,
        )
    }
}
