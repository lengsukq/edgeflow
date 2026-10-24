package com.edgeflow.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.edgeflow.core.model.NamedAction
import com.edgeflow.core.model.AppSettings
import com.edgeflow.ui.i18n.UiTranslations
import kotlin.math.abs

/**
 * Independent persistent overlay, deliberately not using OverlayHostService's
 * transient activeView (Pie, panels, recorder, etc. must not dismiss the bar).
 */
internal class FloatingAutomationBar(
    private val context: Context,
    private val manager: WindowManager,
    private val trigger: (Int) -> Unit,
) {
    private val prefs = context.getSharedPreferences("automation_bar_position", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private fun tr(value: String) = UiTranslations.forContext(context, value)
    private var sizePercent = 100
    private fun dp(value: Int) = (value * density * sizePercent / 100f).toInt()
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0xEF1E293B.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1).coerceAtLeast(1), 0x55FFFFFF)
        }
    }

    fun resetPosition() {
        y = screenHeight / 3
        onRight = true
        prefs.edit().remove("y").remove("right").apply()
        expanded = false
        redraw()
    }
    private val params = WindowManager.LayoutParams(
        dp(52), dp(52),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private var attached = false
    private var expanded = false
    private var entries = emptyList<NamedAction>()
    private var onRight = prefs.getBoolean("right", true)
    private var y = prefs.getInt("y", -1)

    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val estimatedHeight get() = dp(52 + if (expanded) 46 * maxOf(1, entries.size) else 0)

    fun show(actions: List<NamedAction>, settings: AppSettings) {
        sizePercent = settings.floatingAutomationBarSizePercent.coerceIn(70, 150)
        root.alpha = settings.floatingAutomationBarOpacityPercent.coerceIn(40, 100) / 100f
        entries = actions.take(5)
        if (y < 0) y = screenHeight / 3
        redraw()
        if (!attached) {
            try {
                manager.addView(root, params)
                attached = true
            } catch (t: Throwable) {
                android.util.Log.w("EdgeFlowBar", "Cannot attach floating bar", t)
            }
        } else updatePosition()
    }

    fun close() {
        if (attached) runCatching { manager.removeView(root) }
        attached = false
    }

    private fun redraw() {
        root.removeAllViews()
        val handle = item(if (expanded) "‹  EdgeFlow" else "EF", 52).apply {
            contentDescription = tr(if (expanded) "Collapse automation shortcuts" else "Expand automation shortcuts")
            setOnClickListener { expanded = !expanded; redraw() }
        }
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var initialY = 0
            private var dragged = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        initialY = y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - startX) > dp(7) || abs(event.rawY - startY) > dp(7)) {
                            dragged = true
                        }
                        if (dragged) {
                            y = (initialY + (event.rawY - startY).toInt())
                                .coerceIn(0, (screenHeight - estimatedHeight).coerceAtLeast(0))
                            onRight = event.rawX >= screenWidth / 2
                            updatePosition()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragged) {
                            prefs.edit().putInt("y", y).putBoolean("right", onRight).apply()
                        } else v.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        root.addView(handle)
        if (expanded) {
            if (entries.isEmpty()) {
                root.addView(item(tr("No tasks selected"), 48))
            } else {
                entries.forEach { action ->
                    root.addView(item(action.name.ifBlank { tr("Action ${action.id}") }.take(34), 46).apply {
                        contentDescription = tr("Run ${action.name}")
                        setOnClickListener {
                            trigger(action.id)
                            expanded = false
                            redraw()
                        }
                    })
                }
            }
        }
        params.width = if (expanded) dp(215) else dp(52)
        params.height = if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else dp(52)
        updatePosition()
    }

    private fun item(text: String, heightDp: Int): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(Color.WHITE)
        isClickable = true
        minHeight = dp(heightDp)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun updatePosition() {
        params.x = if (onRight) (screenWidth - params.width).coerceAtLeast(0) else 0
        params.y = y.coerceIn(0, (screenHeight - estimatedHeight).coerceAtLeast(0))
        if (attached) runCatching { manager.updateViewLayout(root, params) }
    }
}
