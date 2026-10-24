package com.edgeflow.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.edgeflow.EdgeFlowApp
import com.edgeflow.R
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.Edge
import com.edgeflow.core.Ipc
import com.edgeflow.core.WidgetWindowGeometry
import com.edgeflow.core.GestureTracePolicy
import com.edgeflow.core.FloatingAutomationBarPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.RecordedGesture
import com.edgeflow.core.model.GestureTracePoint
import com.edgeflow.core.model.GestureStroke
import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.PanelConfig
import com.edgeflow.hook.GesturePointerGeometry
import com.edgeflow.hook.gesture.EdgeGeometryFactory
import com.edgeflow.ui.MainActivity
import com.edgeflow.ui.TextToolsActivity
import com.edgeflow.ui.RegionCropActivity
import com.edgeflow.ui.UnlockRequestActivity
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Owns app-UID windows used by actions dispatched from system_server. */
class OverlayHostService : Service() {

    private var windowManager: WindowManager? = null
    private var activeView: View? = null
    private var activeWidgetHostView: AppWidgetHostView? = null
    private var appWidgetHost: AppWidgetHost? = null
    private var widgetHostListening = false
    private val widgetTimeoutHandler = Handler(Looper.getMainLooper())
    private var widgetTimeoutCallback: Runnable? = null
    private var screenFilterView: View? = null
    private var slideAdjustView: SlideAdjustOverlayView? = null
    private var gesturePointerView: GesturePointerOverlayView? = null
    private var gesturePointerFailed = false
    private var automationBar: FloatingAutomationBar? = null
    private val barScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val barScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAutomationBar()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        appWidgetHost = AppWidgetHost(this, Action.FloatingWidget.HOST_ID)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(barScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(barScreenReceiver, filter)
        }
        barScope.launch {
            (application as EdgeFlowApp).configRepository.config.collect { refreshAutomationBar() }
        }
        refreshAutomationBar()
    }

    private fun refreshAutomationBar() {
        val current = (application as EdgeFlowApp).configRepository.current
        val setting = current.settings
        val keyguardLocked = runCatching {
            val keyguard = getSystemService(KeyguardManager::class.java) ?: return@runCatching true
            keyguard.isKeyguardLocked || keyguard.isDeviceLocked
        }.getOrDefault(true)
        if (!setting.floatingAutomationBarEnabled || !Settings.canDrawOverlays(this) ||
            (setting.floatingAutomationBarHideOnLockscreen && keyguardLocked) ||
            (setting.floatingAutomationBarHideInGameMode && setting.gameMode)
        ) {
            automationBar?.close()
            automationBar = null
            return
        }
        val wm = windowManager ?: return
        val bar = automationBar ?: FloatingAutomationBar(this, wm) { taskId ->
            sendBroadcast(
                Intent(Ipc.PERFORM_ACTION)
                    .setPackage(Ipc.SYSTEM_PACKAGE)
                    .putExtra(Ipc.EXTRA_SLOT, taskId),
            )
        }.also { automationBar = it }
        bar.show(FloatingAutomationBarPolicy.selected(current), setting)
    }

    private fun showGestureRecorder() {
        val view = GestureRecordingOverlayView(
            this,
            onClose = ::removeOverlay,
            onSave = { draft ->
                val record = draft.copy(
                    name = com.edgeflow.ui.i18n.UiTranslations.forContext(this, "Gesture") + " " +
                        ((application as EdgeFlowApp).configRepository.current.recordedGestures.size + 1),
                )
                if (!GestureTracePolicy.valid(record)) {
                    Toast.makeText(this, com.edgeflow.ui.i18n.UiTranslations.forContext(
                        this, "Gesture must finish within 20 seconds (max 2 fingers)"),
                        Toast.LENGTH_LONG).show()
                    return@GestureRecordingOverlayView
                }
                runCatching {
                    (application as EdgeFlowApp).configRepository.update { config ->
                        GestureTracePolicy.append(config, record)
                    }
                }.onSuccess {
                    Toast.makeText(this, com.edgeflow.ui.i18n.UiTranslations.forContext(
                        this, "Gesture saved. Bind it via Inject Gesture."), Toast.LENGTH_LONG).show()
                    removeOverlay()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Could not save gesture", Toast.LENGTH_LONG).show()
                }
            },
        )
        showView(
            view,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.START,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeOverlay()
                removeScreenFilter()
                removeGesturePointer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                refreshAutomationBar()
                return START_STICKY
            }
            ACTION_RESET_BAR -> {
                automationBar?.resetPosition()
                return START_STICKY
            }
            Ipc.SHOW_OVERLAY -> showRequestedOverlay(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        stopWidgetHostListening()
        appWidgetHost = null
        removeScreenFilter()
        removeGesturePointer()
        automationBar?.close()
        automationBar = null
        barScope.cancel()
        runCatching { unregisterReceiver(barScreenReceiver) }
        super.onDestroy()
    }

    private fun showRequestedOverlay(intent: Intent) {
        val kind = intent.getStringExtra(Ipc.EXTRA_OVERLAY_KIND) ?: return
        if (kind == Ipc.KIND_GESTURE_RECORD_STOP) {
            if (activeView is GestureRecordingOverlayView) removeOverlay()
            return
        }
        if (kind == Ipc.KIND_GESTURE_RECORD && activeView is GestureRecordingOverlayView) {
            removeOverlay()
            return
        }
        if (kind == Ipc.KIND_GESTURE_POINTER &&
            !intent.getBooleanExtra(Ipc.EXTRA_POINTER_VISIBLE, false)
        ) {
            removeGesturePointer()
            return
        }
        val needsWindowOverlay = kind != Ipc.KIND_CLIPBOARD_HISTORY &&
            kind != Ipc.KIND_UNIVERSAL_COPY &&
            kind != Ipc.KIND_SCREENSHOT_REGION &&
            kind != Ipc.KIND_UNLOCK_REQUEST
        if (needsWindowOverlay && !Settings.canDrawOverlays(this)) {
            if (kind != Ipc.KIND_GESTURE_POINTER || !gesturePointerFailed) {
                android.util.Log.w(TAG, "overlay permission is missing; open EdgeFlow settings to grant it")
            }
            if (kind == Ipc.KIND_GESTURE_POINTER) gesturePointerFailed = true
            return
        }
        val edgeIndex = intent.getIntExtra(Ipc.EXTRA_EDGE, Edge.LEFT.index)
        val edge = Edge.of(edgeIndex) ?: Edge.LEFT
        val config = (application as EdgeFlowApp).configRepository.current

        when (kind) {
            Ipc.KIND_UNLOCK_REQUEST -> launchToolActivity(
                Intent(this, UnlockRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                "Unlock request",
            )
            Ipc.KIND_GESTURE_RECORD -> showGestureRecorder()
            Ipc.KIND_CLIPBOARD_HISTORY -> openTextTools(kind)
            Ipc.KIND_UNIVERSAL_COPY -> openTextTools(kind)
            Ipc.KIND_SCREENSHOT_REGION -> captureScreenshotRegion()
            Ipc.KIND_PIE -> showPie(edge, config.pie.edge(edge))
            Ipc.KIND_SIDE_BAR -> {
                val actions = config.sideBars.items(edge)
                showActionList("${edge.label} side bar", actions, edge, Ipc.KIND_SIDE_BAR)
            }
            Ipc.KIND_PANEL -> {
                val panel = config.panels.getOrNull(intent.getIntExtra(Ipc.EXTRA_PANEL, 0))
                if (panel == null) {
                    android.util.Log.w(TAG, "panel ${intent.getIntExtra(Ipc.EXTRA_PANEL, 0)} is not configured")
                    return
                }
                showPanel(panel)
            }
            Ipc.KIND_SHORTCUT_PANEL -> {
                val panel = config.shortcutPanels.edges[edge.index]
                if (panel == null) {
                    android.util.Log.w(TAG, "shortcut panel is not configured for ${edge.label}")
                    return
                }
                showPanel(panel, Ipc.KIND_SHORTCUT_PANEL, edge.index)
            }
            Ipc.KIND_APPS -> showInstalledApps()
            Ipc.KIND_RUNNING_APPS -> showPackageList(
                "Running apps",
                intent.getStringArrayListExtra(Ipc.EXTRA_PACKAGES).orEmpty(),
                kind,
            )
            Ipc.KIND_FREEZER -> showPackageList(
                "Frozen apps",
                intent.getStringArrayListExtra(Ipc.EXTRA_PACKAGES).orEmpty(),
                kind,
            )
            Ipc.KIND_APP_OPTION_MENU -> showAppOptionMenu(intent.getStringExtra(Ipc.EXTRA_PACKAGE))
            Ipc.KIND_APP_ACTIVITIES -> showAppActivities(intent.getStringExtra(Ipc.EXTRA_PACKAGE))
            Ipc.KIND_PEN -> showPen()
            Ipc.KIND_FLOATING_WIDGET -> showFloatingWidget(
                Action.FloatingWidget(
                    widgetId = intent.getIntExtra(Ipc.EXTRA_WIDGET_ID, 0),
                    widthDp = intent.getIntExtra(Ipc.EXTRA_WIDGET_WIDTH_DP, 0),
                    heightDp = intent.getIntExtra(Ipc.EXTRA_WIDGET_HEIGHT_DP, 0),
                    initialXPercent = intent.getIntExtra(Ipc.EXTRA_WIDGET_X_PERCENT, 50),
                    initialYPercent = intent.getIntExtra(Ipc.EXTRA_WIDGET_Y_PERCENT, 20),
                    timeoutSeconds = intent.getIntExtra(Ipc.EXTRA_WIDGET_TIMEOUT_SECONDS, 0),
                    rememberLastPosition = intent.getBooleanExtra(Ipc.EXTRA_WIDGET_REMEMBER_POSITION, true),
                ),
            )
            Ipc.KIND_SCREEN_FILTER -> showScreenFilter(intent.getIntExtra(Ipc.EXTRA_FILTER_ALPHA, 0))
            Ipc.KIND_SLIDE_ADJUST -> showSlideAdjustOverlay(
                intent.getFloatExtra(Ipc.EXTRA_SLIDE_PROGRESS, -1f),
                intent.getStringExtra(Ipc.EXTRA_SLIDE_LABEL).orEmpty(),
            )
            Ipc.KIND_GESTURE_POINTER -> showGesturePointer(intent)
            else -> android.util.Log.w(TAG, "unsupported overlay kind: $kind")
        }
    }

    private fun openTextTools(kind: String) {
        // Capture Accessibility text while the target app is still focused.
        val snippets = if (kind == Ipc.KIND_UNIVERSAL_COPY) {
            EdgeAccessibilityService.captureVisibleText().orEmpty()
        } else emptyList()
        val intent = Intent(this, TextToolsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Ipc.EXTRA_OVERLAY_KIND, kind)
            .putStringArrayListExtra(TextToolsActivity.EXTRA_SNIPPETS, ArrayList(snippets))
        launchToolActivity(intent, if (kind == Ipc.KIND_UNIVERSAL_COPY) "Universal Copy" else "Clipboard history")
    }

    private fun launchToolActivity(intent: Intent, title: String) {
        // Android 10+ may prohibit a background service launching an activity,
        // even when it owns a foreground service. A tap notification is the
        // user-controlled fallback rather than retrying an illegal launch.
        runCatching { startActivity(intent) }.onFailure { throwable ->
            android.util.Log.w(TAG, "Android blocked $title activity launch; posting action notification", throwable)
            runCatching {
                val pending = PendingIntent.getActivity(
                    this,
                    intent.getStringExtra(RegionCropActivity.EXTRA_CACHE_NAME)?.hashCode()
                        ?: title.hashCode(),
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("EdgeFlow: $title")
                    .setContentText("Tap to open the requested tool")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
                getSystemService(NotificationManager::class.java)?.notify(TOOL_NOTIFICATION_ID, notification)
            }.onFailure {
                android.util.Log.e(TAG, "could not show tool notification", it)
            }
        }
    }

    private fun captureScreenshotRegion() {
        if (!EdgeAccessibilityService.available()) {
            Toast.makeText(this, "Enable EdgeFlow Accessibility in Settings for region screenshots", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Region screenshot requires Android 11 or later", Toast.LENGTH_LONG).show()
            return
        }
        EdgeAccessibilityService.captureScreenshot { bitmap, reason ->
            if (bitmap == null) {
                Toast.makeText(this, reason ?: "Screenshot denied by Android", Toast.LENGTH_LONG).show()
                return@captureScreenshot
            }
            val file = File(cacheDir, "region-${UUID.randomUUID()}.png")
            runCatching {
                try {
                    file.outputStream().use { stream ->
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                            "Could not encode screenshot"
                        }
                    }
                    launchToolActivity(
                        Intent(this, RegionCropActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(RegionCropActivity.EXTRA_CACHE_NAME, file.name),
                        "Region screenshot",
                    )
                } catch (t: Throwable) {
                    file.delete()
                    throw t
                }
            }.onFailure {
                android.util.Log.e(TAG, "could not open screenshot region editor", it)
                Toast.makeText(this, "Could not start region editor", Toast.LENGTH_LONG).show()
            }
            bitmap.recycle()
        }
    }

    private fun showPie(edge: Edge, config: com.edgeflow.core.model.PieEdge) {
        val view = PieOverlayView(this, config) { slot ->
            dispatchSelection(Ipc.KIND_PIE, edge.index, slot)
        }
        val size = dp(304)
        val gravity = when (edge) {
            Edge.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            Edge.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            Edge.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            Edge.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        showView(view, size, size, gravity)
    }

    private fun showActionList(title: String, actions: List<Action>, edge: Edge, kind: String) {
        val view = ActionListOverlayView(this, title, actions) { index ->
            dispatchSelection(kind, edge.index, index)
        }
        val gravity = when (edge) {
            Edge.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            Edge.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            Edge.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            Edge.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        showView(view, dp(300), WindowManager.LayoutParams.WRAP_CONTENT, gravity)
    }

    private fun showPanel(panel: PanelConfig, kind: String = Ipc.KIND_PANEL, selectionEdge: Int = panel.id) {
        val view = PanelOverlayView(this, panel) { index ->
            dispatchSelection(kind, selectionEdge, index)
        }
        showView(view, dp(320), WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    }

    private fun showInstalledApps() {
        val packages = packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
            .map { it.packageName }
            .toList()
        showPackageList("Applications", packages, Ipc.KIND_APPS)
    }

    private fun showPackageList(title: String, packages: List<String>, kind: String) {
        val distinctPackages = packages.distinct()
        val actions = distinctPackages.map { Action.LaunchApp(it) }
        val view = ActionListOverlayView(this, title, actions) { index ->
            val packageName = distinctPackages.getOrNull(index) ?: return@ActionListOverlayView
            dispatchPackageSelection(kind, packageName)
        }
        val gravity = if (kind == Ipc.KIND_APPS) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
        showView(view, dp(300), WindowManager.LayoutParams.WRAP_CONTENT, gravity)
    }

    private fun showFloatingWidget(action: Action.FloatingWidget) {
        val widgetId = action.widgetId
        if (widgetId <= 0) {
            android.util.Log.w(TAG, "floating widget request has no valid widget ID")
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val provider = runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()
        if (provider == null) {
            android.util.Log.w(TAG, "floating widget $widgetId is no longer bound to a provider")
            return
        }
        val host = appWidgetHost ?: return
        removeOverlay()

        try {
            if (!widgetHostListening) {
                host.startListening()
                widgetHostListening = true
            }
            val hostView = host.createView(this, widgetId, provider)
            val density = resources.displayMetrics.density
            val metrics = resources.displayMetrics
            val availableWidthDp = (metrics.widthPixels / density).toInt().coerceAtLeast(160)
            val availableHeightDp = (metrics.heightPixels / density).toInt().coerceAtLeast(240)
            val widgetWidthDp = WidgetWindowGeometry.sizeDp(
                action.widthDp, provider.minWidth.takeIf { it > 0 } ?: 280,
                availableWidthDp, 160,
            )
            val widgetHeightDp = WidgetWindowGeometry.sizeDp(
                action.heightDp, provider.minHeight.takeIf { it > 0 } ?: 180,
                (availableHeightDp - FLOATING_WIDGET_HEADER_DP).coerceAtLeast(1), 120,
            )
            hostView.updateAppWidgetSize(null, widgetWidthDp, widgetHeightDp, widgetWidthDp, widgetHeightDp)
            val title = runCatching { provider.loadLabel(packageManager).toString() }
                .getOrDefault(provider.provider.className)
            val view = FloatingWidgetOverlayView(this, hostView, title, ::removeOverlay)
            val width = dp(widgetWidthDp)
            val height = dp(widgetHeightDp + FLOATING_WIDGET_HEADER_DP)
            val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
            val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
            val savedPosition = if (action.rememberLastPosition) {
                getSharedPreferences(WIDGET_POSITION_PREFS, MODE_PRIVATE)
            } else null
            val xPercent = savedPosition?.getInt("x_$widgetId", action.initialXPercent)
                ?: action.initialXPercent
            val yPercent = savedPosition?.getInt("y_$widgetId", action.initialYPercent)
                ?: action.initialYPercent
            activeWidgetHostView = hostView
            showView(
                view,
                width,
                height,
                Gravity.TOP or Gravity.LEFT,
                replaceCurrent = false,
                configureParams = { params ->
                    params.x = WidgetWindowGeometry.pixelPosition(maxX, xPercent)
                    params.y = WidgetWindowGeometry.pixelPosition(maxY, yPercent)
                    view.onDrag = { dx, dy ->
                        params.x = (params.x + dx).coerceIn(0, maxX)
                        params.y = (params.y + dy).coerceIn(0, maxY)
                        runCatching { windowManager?.updateViewLayout(view, params) }
                            .onFailure { android.util.Log.w(TAG, "could not move floating widget", it) }
                    }
                    view.onDragFinished = {
                        // Save once at the end of dragging instead of blocking
                        // the UI thread with a preference write on every MOVE.
                        savedPosition?.edit()
                            ?.putInt("x_$widgetId", WidgetWindowGeometry.positionPercent(maxX, params.x))
                            ?.putInt("y_$widgetId", WidgetWindowGeometry.positionPercent(maxY, params.y))
                            ?.apply()
                    }
                },
            )
            if (activeView !== view) {
                activeWidgetHostView = null
                stopWidgetHostListening()
            } else {
                val delay = WidgetWindowGeometry.timeoutMillis(action.timeoutSeconds)
                if (delay > 0) {
                    val callback = Runnable { if (activeView === view) removeOverlay() }
                    widgetTimeoutCallback = callback
                    widgetTimeoutHandler.postDelayed(callback, delay)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "could not show floating widget $widgetId", t)
            activeWidgetHostView = null
            stopWidgetHostListening()
        }
    }

    private fun stopWidgetHostListening() {
        if (!widgetHostListening) return
        runCatching { appWidgetHost?.stopListening() }
            .onFailure { android.util.Log.w(TAG, "could not stop app-widget host listening", it) }
        widgetHostListening = false
    }

    private fun showGesturePointer(intent: Intent) {
        if (!intent.getBooleanExtra(Ipc.EXTRA_POINTER_VISIBLE, false)) {
            removeGesturePointer()
            return
        }

        val metrics = resources.displayMetrics
        val point = GesturePointerGeometry.clampPoint(
            intent.getFloatExtra(Ipc.EXTRA_POINTER_X, 0f),
            intent.getFloatExtra(Ipc.EXTRA_POINTER_Y, 0f),
            metrics.widthPixels,
            metrics.heightPixels,
        )
        val scale = intent.getIntExtra(Ipc.EXTRA_POINTER_SCALE, 100)
        val color = intent.getLongExtra(Ipc.EXTRA_POINTER_COLOR, 0xFF03A9F4)
        val current = gesturePointerView
        if (current != null) {
            current.update(point.x, point.y, scale, color)
            gesturePointerFailed = false
            return
        }

        val view = GesturePointerOverlayView(this, point.x, point.y, scale, color)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Keep a full-screen NOT_TOUCHABLE window below Android's obscuring-opacity threshold
            // so the gesture's original touch stream continues to reach the foreground app.
            alpha = 0.75f
        }
        try {
            windowManager?.addView(view, params)
            gesturePointerView = view
            gesturePointerFailed = false
        } catch (t: Throwable) {
            gesturePointerView = null
            if (!gesturePointerFailed) {
                android.util.Log.e(TAG, "could not attach gesture pointer overlay", t)
            }
            gesturePointerFailed = true
        }
    }

    private fun removeGesturePointer() {
        val view = gesturePointerView ?: return
        try {
            windowManager?.removeView(view)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "gesture pointer overlay already detached", t)
        } finally {
            gesturePointerView = null
        }
    }

    private fun showSlideAdjustOverlay(progress: Float, label: String) {
        if (progress < 0f) {
            slideAdjustView?.let { view ->
                if (activeView === view) removeOverlay() else slideAdjustView = null
            }
            return
        }
        slideAdjustView?.let {
            it.update(label, progress)
            return
        }

        val view = SlideAdjustOverlayView(this, label, progress)
        slideAdjustView = view
        showView(view, dp(296), dp(64), Gravity.TOP or Gravity.CENTER_HORIZONTAL, touchable = false)
        if (activeView !== view) slideAdjustView = null
    }

    private fun showScreenFilter(alpha: Int) {
        if (alpha <= 0) {
            removeScreenFilter()
            return
        }
        val opacity = (alpha.coerceIn(0, MAX_SAFE_FILTER_ALPHA) * 255 / 100) shl 24
        val current = screenFilterView
        if (current != null) {
            current.setBackgroundColor(opacity)
            return
        }

        val view = View(this).apply { setBackgroundColor(opacity) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager?.addView(view, params)
            screenFilterView = view
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "could not attach screen filter overlay", t)
        }
    }

    private fun removeScreenFilter() {
        val view = screenFilterView ?: return
        try {
            windowManager?.removeView(view)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "screen filter overlay already detached", t)
        } finally {
            screenFilterView = null
        }
    }

    private fun showPen() {
        val view = PenOverlayView(this, ::removeOverlay)
        showView(
            view,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.START,
        )
    }

    private fun showAppOptionMenu(packageName: String?) {
        val target = packageName?.takeIf(String::isNotBlank) ?: return
        val appInfo = runCatching { packageManager.getApplicationInfo(target, 0) }.getOrNull() ?: run {
            android.util.Log.w(TAG, "cannot show app options for missing package $target")
            return
        }
        val appLabel = runCatching { packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(target)
        val options = buildList {
            add(AppOptionEntry(Ipc.APP_OPTION_DISMISS, "Close menu"))
            if (packageManager.getLaunchIntentForPackage(target) != null) {
                add(AppOptionEntry(Ipc.APP_OPTION_OPEN, "Open app"))
            }
            add(AppOptionEntry(Ipc.APP_OPTION_FINISH_ACTIVITY, "Finish activity"))
            add(AppOptionEntry(Ipc.APP_OPTION_STOP, "Stop app"))
            add(AppOptionEntry(Ipc.APP_OPTION_FREEZE, "Freeze app"))
            add(AppOptionEntry(Ipc.APP_OPTION_ACTIVITIES, "Activities"))
            add(AppOptionEntry(Ipc.APP_OPTION_APP_INFO, "App info"))
            val storeIntent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$target"),
            )
            if (packageManager.resolveActivity(storeIntent, 0) != null) {
                add(AppOptionEntry(Ipc.APP_OPTION_PLAY_STORE, "Open in Play Store"))
            }
        }
        val view = AppOptionMenuOverlayView(this, appLabel, target, options) { command ->
            dispatchAppOptionSelection(target, command)
        }
        val width = min(resources.displayMetrics.widthPixels - dp(32), dp(420)).coerceAtLeast(dp(240))
        showView(view, width, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    }

    private fun showAppActivities(packageName: String?) {
        val target = packageName?.takeIf(String::isNotBlank) ?: return
        val activities = loadExportedActivities(target)
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
        }.getOrDefault(target)
        val view = ActivityListOverlayView(this, "$appLabel activities", activities) { className ->
            dispatchAppActivitySelection(target, className)
        }
        showView(view, dp(320), WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    }

    @Suppress("DEPRECATION")
    private fun loadExportedActivities(packageName: String): List<ActivityEntry> {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(
                        android.content.pm.PackageManager.GET_ACTIVITIES.toLong(),
                    ),
                )
            } else {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
            }
        }.getOrNull() ?: return emptyList()

        return packageInfo.activities.orEmpty()
            .filter { it.exported && it.name.isNotBlank() }
            .map { info ->
                ActivityEntry(
                    className = info.name,
                    label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.name),
                )
            }
            .distinctBy(ActivityEntry::className)
            .sortedBy { it.label.lowercase() }
    }

    private fun dispatchAppActivitySelection(packageName: String, className: String) {
        removeOverlay()
        sendBroadcast(
            Intent(Ipc.OVERLAY_SELECTION)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_APP_ACTIVITIES)
                .putExtra(Ipc.EXTRA_PACKAGE, packageName)
                .putExtra(Ipc.EXTRA_ACTIVITY_CLASS, className),
        )
    }

    private fun dispatchAppOptionSelection(packageName: String, command: Int) {
        removeOverlay()
        if (command == Ipc.APP_OPTION_FREEZE && packageName !in PROTECTED_PACKAGES) {
            val app = application as EdgeFlowApp
            app.configRepository.update { config ->
                config.copy(freezer = config.freezer.copy(frozen = config.freezer.frozen + packageName))
            }
        }
        sendBroadcast(
            Intent(Ipc.OVERLAY_SELECTION)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_OVERLAY_KIND, Ipc.KIND_APP_OPTION_MENU)
                .putExtra(Ipc.EXTRA_INDEX, command)
                .putExtra(Ipc.EXTRA_PACKAGE, packageName),
        )
    }

    private fun dispatchPackageSelection(kind: String, packageName: String) {
        removeOverlay()
        sendBroadcast(
            Intent(Ipc.OVERLAY_SELECTION)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_OVERLAY_KIND, kind)
                .putExtra(Ipc.EXTRA_PACKAGE, packageName),
        )
    }

    private fun showView(
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        touchable: Boolean = true,
        replaceCurrent: Boolean = true,
        configureParams: ((WindowManager.LayoutParams) -> Unit)? = null,
    ) {
        if (replaceCurrent) removeOverlay()
        val touchFlags = if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                touchFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            windowAnimations = android.R.style.Animation_Dialog
        }
        configureParams?.invoke(params)
        try {
            windowManager?.addView(view, params)
            activeView = view
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "could not attach overlay window", t)
        }
    }

    private fun removeOverlay() {
        widgetTimeoutCallback?.let(widgetTimeoutHandler::removeCallbacks)
        widgetTimeoutCallback = null
        val view = activeView ?: return
        try {
            windowManager?.removeView(view)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "overlay already detached", t)
        } finally {
            if (view === slideAdjustView) slideAdjustView = null
            if (activeWidgetHostView != null) {
                activeWidgetHostView = null
                stopWidgetHostListening()
            }
            activeView = null
        }
    }

    private fun dispatchSelection(kind: String, edge: Int, index: Int) {
        removeOverlay()
        sendBroadcast(
            Intent(Ipc.OVERLAY_SELECTION)
                .setPackage(Ipc.SYSTEM_PACKAGE)
                .putExtra(Ipc.EXTRA_OVERLAY_KIND, kind)
                .putExtra(Ipc.EXTRA_EDGE, edge)
                .putExtra(Ipc.EXTRA_INDEX, index),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.overlay_service_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.overlay_service_title))
        .setContentText(getString(R.string.overlay_service_description))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.overlay_service_stop),
            PendingIntent.getService(
                this,
                1,
                Intent(this, OverlayHostService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TOOL_NOTIFICATION_ID = 2602
        const val WIDGET_POSITION_PREFS = "floating_widget_positions"
        private const val TAG = "EdgeFlowOverlay"
        private const val CHANNEL_ID = "edgeflow-overlays"
        private const val NOTIFICATION_ID = 2601
        private val PROTECTED_PACKAGES = setOf(Ipc.SYSTEM_PACKAGE, Ipc.APP_PACKAGE, "com.android.systemui")
        // Keep touch-obscuring opacity below Android's pass-through threshold.
        private const val MAX_SAFE_FILTER_ALPHA = 75
        const val ACTION_START = "com.edgeflow.overlay.START"
        const val ACTION_RESET_BAR = "com.edgeflow.overlay.RESET_BAR"
        const val ACTION_STOP = "com.edgeflow.overlay.STOP"

        fun startFromUserAction(context: Context) {
            val intent = Intent(context, OverlayHostService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayHostService::class.java))
        }
    }
}

private const val FLOATING_WIDGET_HEADER_DP = 42

private class FloatingWidgetOverlayView(
    context: Context,
    widgetView: AppWidgetHostView,
    title: String,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    var onDrag: ((Int, Int) -> Unit)? = null
    var onDragFinished: (() -> Unit)? = null

    init {
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xF228292E.toInt())
            cornerRadius = dp(18).toFloat()
        }
        elevation = dp(12).toFloat()
        clipToOutline = true

        addView(
            widgetView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = dp(FLOATING_WIDGET_HEADER_DP)
            },
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        val dragHandle = TextView(context).apply {
            text = title
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = "Drag to move $title"
            isClickable = true
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - lastRawX).toInt()
                        val dy = (event.rawY - lastRawY).toInt()
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        if (dx != 0 || dy != 0) onDrag?.invoke(dx, dy)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onDragFinished?.invoke()
                        true
                    }
                    else -> false
                }
            }
        }
        header.addView(dragHandle, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        header.addView(TextView(context).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            contentDescription = "Close floating widget"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(dp(36), LayoutParams.MATCH_PARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(FLOATING_WIDGET_HEADER_DP), Gravity.TOP))
    }

    private var lastRawX = 0f
    private var lastRawY = 0f

    private fun dp(value: Int): Int = (value * density).toInt()
}

private class PieOverlayView(
    context: Context,
    private val config: com.edgeflow.core.model.PieEdge,
    private val onSelect: (Int) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE202124.toInt() }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF303134.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 11f * density
    }
    private val bounds = RectF()
    private var highlightedSlot = -1

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.47f
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        drawRing(
            canvas,
            centerX,
            centerY,
            radius,
            PieMenuGeometry.INNER_RING_INNER_RATIO,
            PieMenuGeometry.INNER_RING_OUTER_RATIO,
            config.level1,
            0,
        )
        drawRing(
            canvas,
            centerX,
            centerY,
            radius,
            PieMenuGeometry.OUTER_RING_INNER_RATIO,
            PieMenuGeometry.OUTER_RING_OUTER_RATIO,
            config.level2,
            PieMenuGeometry.SLOTS_PER_RING,
        )
        canvas.drawCircle(centerX, centerY, radius * 0.25f, centerPaint)
        textPaint.textSize = 10f * density
        canvas.drawText("EdgeFlow", centerX, centerY + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawRing(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        innerRatio: Float,
        outerRatio: Float,
        actions: List<Action>,
        startIndex: Int,
    ) {
        val ringRadius = radius * (innerRatio + outerRatio) / 2f
        ringPaint.strokeWidth = radius * (outerRatio - innerRatio)
        for (index in 0 until PieMenuGeometry.SLOTS_PER_RING) {
            val slot = startIndex + index
            val startAngle = PieMenuGeometry.slotStartAngleDegrees(index)
            ringPaint.color = if (slot == highlightedSlot) 0xFF6750A4.toInt() else 0xFF41434A.toInt()
            bounds.set(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius)
            canvas.drawArc(bounds, startAngle, PieMenuGeometry.SLOT_ARC_SWEEP_DEGREES, false, ringPaint)
            val angle = PieMenuGeometry.slotCenterAngleRadians(index)
            val labelRadius = radius * (innerRatio + outerRatio) / 2f
            val x = centerX + cos(angle).toFloat() * labelRadius
            val y = centerY + sin(angle).toFloat() * labelRadius
            val action = actions.getOrNull(index) ?: Action.None
            val label = if (action.id == com.edgeflow.core.ActionId.NONE) "·" else ActionCatalog.describe(action).take(9)
            canvas.drawText(label, x, y + textPaint.textSize * 0.35f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                highlightedSlot = slotAt(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val slot = slotAt(event.x, event.y)
                if (slot >= 0) onSelect(slot)
                else onSelect(-1)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onSelect(-1)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun slotAt(x: Float, y: Float): Int = PieMenuGeometry.slotAt(
        x = x,
        y = y,
        centerX = width / 2f,
        centerY = height / 2f,
        radius = min(width, height) * 0.47f,
    )
}

/** Shared geometry for drawing and hit-testing the two six-slot Pie rings. */
internal object PieMenuGeometry {
    const val SLOTS_PER_RING = 6
    const val INNER_RING_INNER_RATIO = 0.31f
    const val INNER_RING_OUTER_RATIO = 0.60f
    const val OUTER_RING_INNER_RATIO = 0.67f
    const val OUTER_RING_OUTER_RATIO = 0.94f
    const val SLOT_START_ANGLE_DEGREES = -120.0
    const val SLOT_SECTOR_DEGREES = 60.0
    const val SLOT_ARC_SWEEP_DEGREES = 56f
    private const val SLOT_LABEL_OFFSET_DEGREES = SLOT_ARC_SWEEP_DEGREES / 2f

    fun slotStartAngleDegrees(slotInRing: Int): Float =
        (SLOT_START_ANGLE_DEGREES + slotInRing * SLOT_SECTOR_DEGREES).toFloat()

    fun slotCenterAngleRadians(slotInRing: Int): Double = Math.toRadians(
        SLOT_START_ANGLE_DEGREES + slotInRing * SLOT_SECTOR_DEGREES + SLOT_LABEL_OFFSET_DEGREES,
    )

    fun slotAt(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Int {
        if (!radius.isFinite() || radius <= 0f) return -1

        val dx = x - centerX
        val dy = y - centerY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy) / radius
        val ringStartIndex = when {
            distance in INNER_RING_INNER_RATIO..INNER_RING_OUTER_RATIO -> 0
            distance in OUTER_RING_INNER_RATIO..OUTER_RING_OUTER_RATIO -> SLOTS_PER_RING
            else -> return -1
        }

        // Canvas angles and atan2 share the same screen coordinate system: zero
        // points right and positive angles proceed clockwise. Align sectors to
        // the rendered arc origin rather than introducing a separate top origin.
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val offset = ((angle - SLOT_START_ANGLE_DEGREES) % 360.0 + 360.0) % 360.0
        val sector = (offset / SLOT_SECTOR_DEGREES).toInt().coerceIn(0, SLOTS_PER_RING - 1)
        return ringStartIndex + sector
    }
}

private class GesturePointerOverlayView(
    context: Context,
    x: Float,
    y: Float,
    scalePercent: Int,
    color: Long,
) : View(context) {

    private val density = resources.displayMetrics.density
    private var pointerX = x
    private var pointerY = y
    private var scalePercent = scalePercent
    private var color = color.toInt()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        this.color = android.graphics.Color.WHITE
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        updatePaints()
    }

    fun update(x: Float, y: Float, scalePercent: Int, color: Long) {
        pointerX = x
        pointerY = y
        this.scalePercent = scalePercent
        this.color = color.toInt()
        updatePaints()
        invalidate()
    }

    private fun updatePaints() {
        fillPaint.color = color
        ringPaint.color = android.graphics.Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val point = GesturePointerGeometry.clampPoint(pointerX, pointerY, width, height)
        val radius = GesturePointerGeometry.radiusPx(density, scalePercent)
        canvas.drawCircle(point.x, point.y, radius, fillPaint)
        canvas.drawCircle(point.x, point.y, radius, ringPaint)
    }
}

private class ActionListOverlayView(
    context: Context,
    title: String,
    actions: List<Action>,
    onSelect: (Int) -> Unit,
) : ScrollView(context) {
    init {
        isFillViewport = true
        setBackgroundColor(0xEE202124.toInt())
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        column.addView(label(title, 18f, true).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) })
        if (actions.isEmpty()) {
            column.addView(label("No actions configured", 14f, false).apply { setPadding(dp(8), dp(16), dp(8), dp(16)) })
        } else {
            actions.forEachIndexed { index, action ->
                column.addView(
                    label(ActionCatalog.describe(action), 14f, false).apply {
                        setPadding(dp(12), dp(14), dp(12), dp(14))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onSelect(index) }
                    },
                )
            }
        }
        addView(column)
    }

    private fun label(text: String, size: Float, bold: Boolean) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(0xFFFFFFFF.toInt())
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class SlideAdjustOverlayView(
    context: Context,
    label: String,
    progress: Float,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF228292E.toInt() }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5F6368.toInt() }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9B8AFB.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 13f * density
    }
    private val trackBounds = RectF()
    private var label = label
    private var progress = progress.coerceIn(0f, 1f)

    init {
        elevation = dp(12).toFloat()
        contentDescription = "Adjusting $label"
    }

    fun update(label: String, progress: Float) {
        this.label = label
        this.progress = progress.coerceIn(0f, 1f)
        contentDescription = "Adjusting $label"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val card = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(card, dp(20).toFloat(), dp(20).toFloat(), cardPaint)
        canvas.drawText(label, width / 2f, dp(25).toFloat(), textPaint)

        val start = dp(18).toFloat()
        val end = width - dp(18).toFloat()
        val trackTop = dp(42).toFloat()
        val trackBottom = dp(48).toFloat()
        trackBounds.set(start, trackTop, end, trackBottom)
        canvas.drawRoundRect(trackBounds, dp(3).toFloat(), dp(3).toFloat(), trackPaint)
        val markerX = start + (end - start) * progress
        trackBounds.set(start, trackTop, markerX, trackBottom)
        canvas.drawRoundRect(trackBounds, dp(3).toFloat(), dp(3).toFloat(), progressPaint)
        canvas.drawCircle(markerX, (trackTop + trackBottom) / 2f, dp(7).toFloat(), progressPaint)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

private data class AppOptionEntry(val command: Int, val label: String)
private data class ActivityEntry(val className: String, val label: String)

/**
 * A deliberate touch-capturing overlay. The user draws one stroke here instead
 * of an always-on system_server hook recording private interactions in apps.
 */
private class GestureRecordingOverlayView(
    context: Context,
    onClose: () -> Unit,
    onSave: (RecordedGesture) -> Unit,
) : FrameLayout(context) {
    private val status = TextView(context).apply {
        text = com.edgeflow.ui.i18n.UiTranslations.forContext(context, "Draw a gesture · 0/20 s")
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(18, 14, 18, 14)
        setBackgroundColor(0xBB171D26.toInt())
    }
    private val drawing = GestureCaptureView(context) { text ->
        status.text = com.edgeflow.ui.i18n.UiTranslations.forContext(context, text)
    }

    init {
        // Keep the target application visible while capturing, but consume touches.
        setBackgroundColor(0x33101820.toInt())
        addView(drawing, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val toolbar = LinearLayout(context).apply {
            setPadding(18, 16, 18, 16)
            setBackgroundColor(0xFF242B34.toInt())
        }
        fun button(text: String, action: () -> Unit) = TextView(context).apply {
            this.text = com.edgeflow.ui.i18n.UiTranslations.forContext(context, text)
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            val density = resources.displayMetrics.density
            setPadding((12 * density).toInt(), (10 * density).toInt(),
                (12 * density).toInt(), (10 * density).toInt())
            setOnClickListener { action() }
        }
        toolbar.addView(button("Clear") { drawing.clear() })
        toolbar.addView(button("Save gesture") {
            drawing.currentRecording()?.let(onSave)
                ?: Toast.makeText(context, com.edgeflow.ui.i18n.UiTranslations.forContext(
                    context, drawing.errorMessage()), Toast.LENGTH_LONG).show()
        })
        toolbar.addView(button("Close", onClose))
        addView(toolbar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 36 })
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (24 * resources.displayMetrics.density).toInt()
            })
    }
}

private class GestureCaptureView(
    context: Context,
    private val onStatus: (String) -> Unit,
) : View(context) {
    /** Actual display pixels, not the overlay's size (which may exclude insets). */
    private var displayGeometry = EdgeGeometryFactory.fromDisplay(context, AppSettings())
    private val captured = mutableListOf<GestureStroke>()
    private data class InProgress(
        val id: Int,
        val start: Long,
        var points: List<GestureTracePoint>,
    )
    private val active = linkedMapOf<Int, InProgress>()
    private var startMs = 0L
    private var recordingRotation = -1
    private var invalidReason: String? = null
    private var lastProgressUpdate = 0L
    private val paths = mutableListOf<android.graphics.Path>()
    private val drawn = mutableMapOf<Int, android.graphics.Path>()
    private val location = IntArray(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF03A9F4.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun currentRecording(): RecordedGesture? {
        if (invalidReason != null || active.isNotEmpty() || captured.isEmpty()) return null
        val strokes = captured.toList()
        val record = RecordedGesture(
            id = UUID.randomUUID().toString(),
            name = "Gesture",
            points = strokes.first().points,
            strokes = strokes,
            sourceWidth = displayGeometry.screenWidth,
            sourceHeight = displayGeometry.screenHeight,
            sourceRotation = recordingRotation,
        )
        return record.takeIf(GestureTracePolicy::valid)
    }

    fun errorMessage(): String = invalidReason ?:
        "Finish at least one stroke before saving (maximum 20 seconds, two fingers)."

    fun clear() {
        captured.clear()
        active.clear()
        paths.clear()
        drawn.clear()
        startMs = 0L
        recordingRotation = -1
        invalidReason = null
        lastProgressUpdate = 0L
        onStatus("Draw a gesture · 0/20 s")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paths.forEach { canvas.drawPath(it, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentRotation = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: -1
        if (recordingRotation != -1 && currentRotation != -1 &&
            currentRotation != recordingRotation && startMs > 0
        ) invalidReason = "Screen rotated during recording. Clear and retry."
        fun position(index: Int): Pair<Float, Float> {
            getLocationOnScreen(location)
            return (location[0] + event.getX(index)) to (location[1] + event.getY(index))
        }
        fun sample(index: Int, eventTime: Long) {
            val id = event.getPointerId(index)
            val pointer = active[id] ?: return
            val elapsed = (eventTime - pointer.start).coerceAtLeast(0)
            if (eventTime - startMs > GestureTracePolicy.MAX_LENGTH_MS) {
                invalidReason = "Recording exceeded 20 seconds. Clear and retry."
                return
            }
            val (x, y) = position(index)
            pointer.points = GestureTracePolicy.addSample(
                pointer.points, x, y, displayGeometry.screenWidth,
                displayGeometry.screenHeight, elapsed,
            )
            drawn[id]?.lineTo(event.getX(index), event.getY(index))
        }
        fun finish(index: Int) {
            val id = event.getPointerId(index)
            val pointer = active[id] ?: return
            sample(index, maxOf(pointer.start + 1, event.eventTime))
            if (pointer.points.size < 2) {
                invalidReason = "Gesture stroke has too few points."
            } else {
                captured += GestureStroke(
                    id,
                    (pointer.start - startMs).toInt(),
                    pointer.points,
                )
            }
            active.remove(id)
            drawn.remove(id)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (captured.isEmpty() && active.isEmpty()) clear()
                displayGeometry = EdgeGeometryFactory.fromDisplay(context, AppSettings())
                if (startMs == 0L) startMs = event.eventTime
                if (recordingRotation == -1) recordingRotation = currentRotation
                if (captured.size >= GestureTracePolicy.MAX_STROKES) {
                    invalidReason = "Maximum of 12 strokes reached."
                } else {
                    val id = event.getPointerId(0)
                    active[id] = InProgress(id, event.eventTime, emptyList())
                    paths += android.graphics.Path().apply { moveTo(event.x, event.y) }
                    drawn[id] = paths.last()
                    sample(0, event.eventTime)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (active.size >= GestureTracePolicy.MAX_SIMULTANEOUS_POINTERS ||
                    captured.size + active.size >= GestureTracePolicy.MAX_STROKES
                ) {
                    invalidReason = "Maximum two fingers and 12 strokes."
                } else {
                    val index = event.actionIndex
                    val id = event.getPointerId(index)
                    active[id] = InProgress(id, event.eventTime, emptyList())
                    paths += android.graphics.Path().apply { moveTo(event.getX(index), event.getY(index)) }
                    drawn[id] = paths.last()
                    sample(index, event.eventTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) sample(index, event.eventTime)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> finish(event.actionIndex)
            MotionEvent.ACTION_CANCEL -> {
                invalidReason = "Recording was interrupted; clear and retry."
                active.clear()
                drawn.clear()
            }
            else -> return false
        }
        if (invalidReason != null) {
            onStatus(invalidReason!!)
        } else if (event.eventTime - lastProgressUpdate >= 120L ||
            event.actionMasked == MotionEvent.ACTION_UP
        ) {
            lastProgressUpdate = event.eventTime
            val elapsed = if (startMs == 0L) 0L else
                (event.eventTime - startMs).coerceAtLeast(0)
            onStatus("Recorded ${captured.size + active.size} strokes · ${elapsed / 1000}/20 s")
        }
        invalidate()
        return true
    }
}

private class PenOverlayView(
    context: Context,
    onClose: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val canvas = PenCanvasView(context)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xF228292E.toInt())
                cornerRadius = dp(20).toFloat()
            }
            addView(toolbarButton("Clear") { canvas.clear() })
            addView(toolbarButton("Close", onClose))
        }
        addView(
            toolbar,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(24)
            },
        )
    }

    private fun toolbarButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(14), dp(10), dp(14), dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

private class PenCanvasView(context: Context) : View(context) {

    private val strokes = mutableListOf<android.graphics.Path>()
    private var activeStroke: android.graphics.Path? = null
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF03A9F4.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun clear() {
        strokes.clear()
        activeStroke = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { canvas.drawPath(it, strokePaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeStroke = android.graphics.Path().apply { moveTo(event.x, event.y) }
                    .also(strokes::add)
            }
            MotionEvent.ACTION_MOVE -> activeStroke?.lineTo(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeStroke = null
            else -> return false
        }
        invalidate()
        return true
    }
}

private class AppOptionMenuOverlayView(
    context: Context,
    appLabel: String,
    packageName: String,
    options: List<AppOptionEntry>,
    onSelect: (Int) -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xF228292E.toInt())
            cornerRadius = dp(24).toFloat()
        }
        elevation = dp(12).toFloat()

        addView(TextView(context).apply {
            text = appLabel
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            text = packageName
            textSize = 12f
            setTextColor(0xFFBDC1C6.toInt())
            setPadding(0, dp(3), 0, dp(12))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        options.forEach { option ->
            val row = TextView(context).apply {
                text = option.label
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(0xFFE8EAED.toInt())
                setPadding(dp(12), dp(13), dp(12), dp(13))
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(option.command) }
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

private class ActivityListOverlayView(
    context: Context,
    title: String,
    activities: List<ActivityEntry>,
    onSelect: (String) -> Unit,
) : ScrollView(context) {

    private val density = resources.displayMetrics.density

    init {
        isFillViewport = true
        setBackgroundColor(0xEE202124.toInt())
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        column.addView(TextView(context).apply {
            text = title
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(12))
        })
        if (activities.isEmpty()) {
            column.addView(TextView(context).apply {
                text = "No exported activities were found"
                textSize = 14f
                setTextColor(0xFFBDC1C6.toInt())
                setPadding(dp(8), dp(16), dp(8), dp(16))
            })
        } else {
            activities.forEach { activity ->
                column.addView(TextView(context).apply {
                    text = "${activity.label}\n${activity.className}"
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onSelect(activity.className) }
                })
            }
        }
        addView(column)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

private class PanelOverlayView(
    context: Context,
    panel: PanelConfig,
    onSelect: (Int) -> Unit,
) : ScrollView(context) {
    init {
        setBackgroundColor(0xEE202124.toInt())
        val grid = GridLayout(context).apply {
            rowCount = panel.rows.coerceIn(1, 8)
            columnCount = panel.columns.coerceIn(1, 8)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val cellCount = (panel.rows * panel.columns).coerceIn(1, 64)
        repeat(cellCount) { index ->
            val action = panel.cells.getOrNull(index) ?: Action.None
            val cell = TextView(context).apply {
                text = if (action.id == com.edgeflow.core.ActionId.NONE) "＋" else ActionCatalog.title(action.id)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF41434A.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(index) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = dp(88)
                height = dp(72)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(cell, params)
        }
        addView(grid)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
