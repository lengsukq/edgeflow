package com.edgeflow.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * Optional, user-enabled accessibility bridge. It never observes or persists
 * window text on events; it only reads it when the user invokes Universal Copy.
 */
class EdgeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    private fun readVisibleText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited++ < MAX_NODES && result.size < MAX_ITEMS) {
                val node = queue.removeFirst()
                if (node.isPassword) continue
                val text = node.text?.toString()?.trim()
                    ?: node.contentDescription?.toString()?.trim()
                if (!text.isNullOrEmpty()) result.add(text.take(MAX_CHARS))
                if (node.childCount > 0 && visited < MAX_NODES) {
                    for (i in 0 until node.childCount.coerceAtMost(MAX_CHILDREN)) {
                        node.getChild(i)?.let(queue::addLast)
                    }
                }
            }
        } finally {
            queue.clear()
        }
        return result.toList()
    }

    private fun captureScreen(onResult: (Bitmap?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null, "Android 11 or later is required for Accessibility screenshots")
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, Executor { command -> mainExecutor.execute(command) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        try {
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            onResult(software, if (software == null) "No image returned" else null)
                        } catch (e: Throwable) {
                            onResult(null, e.message ?: "Screen capture failed")
                        } finally {
                            buffer.close()
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        onResult(null, "Android screenshot error $errorCode")
                    }
                },
            )
        } catch (e: Throwable) {
            onResult(null, e.message ?: "Screenshot not permitted")
        }
    }

    companion object {
        @Volatile private var active: EdgeAccessibilityService? = null
        private const val MAX_NODES = 1200
        private const val MAX_CHILDREN = 120
        private const val MAX_ITEMS = 120
        private const val MAX_CHARS = 1000

        fun available(): Boolean = active != null

        /** Snapshot the target app before EdgeFlow opens its own UI. */
        fun captureVisibleText(): List<String>? = active?.let { service ->
            runCatching { service.readVisibleText() }.getOrNull()
        }

        fun captureScreenshot(onResult: (Bitmap?, String?) -> Unit): Boolean {
            val service = active ?: return false
            service.captureScreen(onResult)
            return true
        }
    }
}
