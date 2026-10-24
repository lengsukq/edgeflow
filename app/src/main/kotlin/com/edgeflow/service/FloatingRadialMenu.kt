package com.edgeflow.service

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import com.edgeflow.core.RadialMenuGeometry
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.NamedAction
import com.edgeflow.ui.i18n.UiTranslations
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Alternative rendering of the existing floating shortcut bar.
 * Only the collapsed bubble is touchable while idle; its window grows on demand.
 * All slots dispatch stable named-action IDs through the existing system bridge.
 */
internal class FloatingRadialMenu(
    private val context: Context,
    private val manager: WindowManager,
    private val trigger: (Int) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("automation_bar_position", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(value: Float) = value * density * sizePercent / 100f
    private val handleSize get() = dp(52f).toInt().coerceAtLeast(40)
    private var sizePercent = 100
    private var opacity = 0.92f
    private var haptics = true
    private var rings = 2
    private var slots: List<NamedAction?> = emptyList()
    private var onRight = prefs.getBoolean("right", true)
    private var handleY = prefs.getInt("y", -1)
    private var expanded = false
    private var closing = false
    private var attached = false
    private var opening = 1f
    private var selected = -1
    private var pointerDown = false
    private var selectionGesture = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var originalY = 0
    private var animation: ValueAnimator? = null
    private var holdCallback: Runnable? = null
    private val view = RadialView(context)

    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val expandedSize get() = min(dp(320f).toInt(), min(screenWidth, screenHeight) - dp(16f).toInt())
        .coerceAtLeast(handleSize)

    private val params = WindowManager.LayoutParams(
        handleSize, handleSize,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show(actions: List<NamedAction?>, settings: AppSettings) {
        slots = actions
        rings = settings.floatingAutomationBarRingCount.coerceIn(2, 3)
        sizePercent = settings.floatingAutomationBarSizePercent.coerceIn(70, 150)
        opacity = settings.floatingAutomationBarOpacityPercent.coerceIn(40, 100) / 100f
        haptics = settings.floatingAutomationBarRingHaptics
        if (handleY < 0) handleY = screenHeight / 3
        updatePosition()
        view.invalidate()
        if (!attached) {
            runCatching {
                manager.addView(view, params)
                attached = true
            }.onFailure { android.util.Log.w("EdgeFlowRadial", "Could not attach menu", it) }
        }
    }

    fun resetPosition() {
        handleY = screenHeight / 3
        onRight = true
        prefs.edit().remove("y").remove("right").apply()
        collapse(animate = false)
        updatePosition()
    }

    fun close() {
        cancelHold()
        animation?.cancel()
        animation = null
        pointerDown = false
        if (attached) runCatching { manager.removeView(view) }
        attached = false
        expanded = false
        closing = false
        selected = -1
    }

    private fun updatePosition() {
        val w = if (expanded) expandedSize else handleSize
        params.width = w
        params.height = w
        params.x = if (onRight) (screenWidth - w).coerceAtLeast(0) else 0
        // The bubble stays vertically aligned with its previous center when opening.
        params.y = if (expanded) {
            (handleY + handleSize / 2 - w / 2).coerceIn(0, (screenHeight - w).coerceAtLeast(0))
        } else handleY.coerceIn(0, (screenHeight - handleSize).coerceAtLeast(0))
        if (attached) runCatching { manager.updateViewLayout(view, params) }
            .onFailure {
                android.util.Log.w("EdgeFlowRadial", "Overlay window update failed", it)
                runCatching { manager.removeView(view) }
                attached = false
            }
    }

    private fun open() {
        if (expanded) return
        cancelHold()
        closing = false
        expanded = true
        selected = -1
        opening = 0f
        updatePosition()
        view.contentDescription = UiTranslations.forContext(context, "Radial shortcuts. Slide and release to run.")
        animation?.cancel()
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { opening = it.animatedValue as Float; view.invalidate() }
            start()
        }
        view.invalidate()
    }

    private fun collapse(animate: Boolean = true) {
        cancelHold()
        animation?.cancel()
        animation = null
        if (expanded && animate && attached) {
            closing = true
            selected = -1
            animation = ValueAnimator.ofFloat(opening.coerceIn(0f, 1f), 0f).apply {
                duration = 170L
                interpolator = DecelerateInterpolator()
                addUpdateListener { opening = it.animatedValue as Float; view.invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (closing) finishCollapse()
                    }
                })
                start()
            }
            return
        }
        finishCollapse()
    }

    private fun finishCollapse() {
        closing = false
        expanded = false
        selected = -1
        opening = 1f
        view.contentDescription = UiTranslations.forContext(context, "Expand radial shortcuts")
        updatePosition()
        view.invalidate()
    }

    private fun cancelHold() {
        holdCallback?.let(handler::removeCallbacks)
        holdCallback = null
    }

    private fun highlighted(rawX: Float, rawY: Float): Int {
        val localX = rawX - params.x
        val localY = rawY - params.y
        val radius = expandedSize / 2f * 0.96f
        return RadialMenuGeometry.slotAt(localX, localY, params.width / 2f, params.height / 2f, radius, rings)
    }

    private fun selectSlot(rawX: Float, rawY: Float) {
        val next = highlighted(rawX, rawY)
        if (selected == next) return
        selected = next
        if (haptics && next >= 0 && slots.getOrNull(next) != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        view.invalidate()
    }

    private fun onTouch(event: MotionEvent): Boolean {
        if (closing) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (expanded) collapse()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                pointerDown = false
                selectionGesture = false
                cancelHold()
                if (expanded) collapse()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                pointerDown = true
                selectionGesture = expanded
                dragging = false
                downX = event.rawX
                downY = event.rawY
                originalY = handleY
                if (expanded) {
                    selectSlot(event.rawX, event.rawY)
                } else {
                    cancelHold()
                    val longPress = Runnable {
                        if (pointerDown && !dragging && !expanded) {
                            selectionGesture = true
                            open()
                        }
                    }
                    holdCallback = longPress
                    handler.postDelayed(longPress, 360L)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pointerDown) return true
                if (expanded) {
                    selectSlot(event.rawX, event.rawY)
                } else if (abs(event.rawX - downX) > dp(8f) || abs(event.rawY - downY) > dp(8f)) {
                    dragging = true
                    cancelHold()
                    handleY = (originalY + (event.rawY - downY).toInt())
                        .coerceIn(0, (screenHeight - handleSize).coerceAtLeast(0))
                    onRight = event.rawX >= screenWidth / 2f
                    updatePosition()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!pointerDown) return true
                pointerDown = false
                cancelHold()
                if (expanded) {
                    if (selectionGesture) {
                        val target = highlighted(event.rawX, event.rawY)
                        val taskId = slots.getOrNull(target)?.id
                        collapse()
                        if (taskId != null) trigger(taskId)
                    } else collapse()
                } else if (dragging) {
                    prefs.edit().putInt("y", handleY).putBoolean("right", onRight).apply()
                } else {
                    view.performClick()
                    open()
                }
                dragging = false
                selectionGesture = false
                return true
            }
        }
        return true
    }

    private inner class RadialView(context: Context) : View(context) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        private val bounds = RectF()
        init {
            isClickable = true
            contentDescription = UiTranslations.forContext(context, "Expand radial shortcuts")
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        override fun onTouchEvent(event: MotionEvent): Boolean = onTouch(event)
        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            // Keep the bubble visually attached to its original edge position while
            // the full-size radial window opens, rather than jumping to its center.
            val motion = if (expanded) (1f - opening.coerceIn(0f, 1f)) else 0f
            val anchorX = (if (onRight) screenWidth - handleSize / 2f else handleSize / 2f) - params.x
            val anchorY = handleY + handleSize / 2f - params.y
            val bubbleX = cx + (anchorX - cx) * motion
            val bubbleY = cy + (anchorY - cy) * motion
            if (expanded) {
                val progress = opening.coerceIn(0f, 1.12f)
                val radius = min(width, height) / 2f * 0.96f
                canvas.save()
                canvas.translate(bubbleX - cx, bubbleY - cy)
                canvas.scale(0.78f + 0.22f * progress, 0.78f + 0.22f * progress, cx, cy)
                bg.shader = null
                bg.color = Color.argb((opacity * 174 * progress.coerceIn(0f, 1f)).toInt(), 18, 26, 44)
                canvas.drawCircle(cx, cy, radius, bg)
                val labelSize = min(dp(11f), radius / 11f)
                label.textSize = labelSize
                for (ring in 0 until rings) {
                    val reveal = ((progress - ring * 0.12f) / (1f - ring * 0.12f)).coerceIn(0f, 1f)
                    val band = RadialMenuGeometry.band(ring, rings)
                    val mid = radius * (band.start + band.endInclusive) / 2f
                    arc.strokeWidth = radius * (band.endInclusive - band.start)
                    bounds.set(cx - mid, cy - mid, cx + mid, cy + mid)
                    for (sector in 0 until 6) {
                        val slot = ring * 6 + sector
                        val active = slot == selected
                        val available = slots.getOrNull(slot) != null
                        arc.color = when {
                            active -> Color.argb((255 * opacity * reveal).toInt(), 113, 137, 255)
                            available -> Color.argb((220 * opacity * reveal).toInt(), 59, 81, 125)
                            else -> Color.argb((155 * opacity * reveal).toInt(), 49, 60, 82)
                        }
                        canvas.drawArc(bounds,
                            (RadialMenuGeometry.START_ANGLE + sector * 60.0 + 2.0).toFloat(), 56f, false, arc)
                        val angle = RadialMenuGeometry.centerAngle(slot)
                        val x = cx + cos(angle).toFloat() * mid
                        val y = cy + sin(angle).toFloat() * mid
                        val name = slots.getOrNull(slot)?.name?.take(5)
                            ?.takeIf { it.isNotBlank() } ?: UiTranslations.forContext(context, "Empty")
                        label.color = if (available) Color.argb((255 * reveal).toInt(), 255, 255, 255)
                            else Color.argb((200 * reveal).toInt(), 170, 180, 199)
                        canvas.drawText(name, x, y + labelSize * 0.35f, label)
                    }
                }
                canvas.restore()
            }
            val bubbleRadius = min(dp(26f), min(width, height) / 2f)
            bg.color = 0xFF5479DC.toInt()
            bg.shader = RadialGradient(bubbleX - bubbleRadius * 0.33f, bubbleY - bubbleRadius * 0.4f,
                bubbleRadius * 2.4f, intArrayOf(0xFF98B6FF.toInt(), 0xFF526BDC.toInt(), 0xFF664ABF.toInt()),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(bubbleX, bubbleY, bubbleRadius, bg)
            bg.shader = null
            label.textSize = dp(14f)
            label.color = Color.WHITE
            canvas.drawText(if (expanded) "×" else "EF",
                bubbleX, bubbleY + label.textSize * 0.35f, label)
        }
    }
}
