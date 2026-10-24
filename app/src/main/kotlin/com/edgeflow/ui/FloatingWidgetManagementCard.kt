package com.edgeflow.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.edgeflow.core.WidgetReferences
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.EdgeConfig
import com.edgeflow.service.OverlayHostService
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow

/**
 * Remove orphaned AppWidgetHost IDs without invalidating saved gestures or
 * workflows. Removing a referenced ID would otherwise leave a silent action.
 */
@Composable
fun FloatingWidgetManagementCard(config: EdgeConfig) {
    val context = LocalContext.current
    val host = remember(context) { AppWidgetHost(context, Action.FloatingWidget.HOST_ID) }
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    var refresh by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ids = remember(refresh, context) {
        runCatching { host.appWidgetIds.sorted() }.getOrDefault(emptyList())
    }
    val referenced = remember(config) { WidgetReferences.referencedIds(config) }

    SectionCard(
        title = "Floating widget bindings",
        subtitle = "Bindings still used by any gesture, panel, or saved action are protected. Unused widgets can be removed.",
    ) {
        if (ids.isEmpty()) {
            Text("No hosted app widgets", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ids.forEachIndexed { index, widgetId ->
            val inUse = widgetId in referenced
            val provider = runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()
            val label = provider?.let {
                runCatching { it.loadLabel(context.packageManager).toString() }.getOrNull()
            } ?: "Widget #$widgetId (provider unavailable)"
            if (index > 0) RowDivider()
            SettingRow(
                title = label,
                subtitle = if (inUse) "In use · ID $widgetId · protected"
                    else "Unused · ID $widgetId · tap to remove binding",
                onClick = {
                    if (inUse) {
                        error = "Remove this widget from all configured actions before deleting its Android binding."
                    } else toDelete = widgetId
                },
            )
        }
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }

    toDelete?.let { widgetId ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove unused widget?") },
            text = {
                Text("Remove Android widget binding #$widgetId and its saved window position? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Recheck latest configuration immediately before deleting.
                    if (widgetId in WidgetReferences.referencedIds(config)) {
                        error = "This widget is still referenced and cannot be deleted."
                    } else {
                        runCatching {
                            host.deleteAppWidgetId(widgetId)
                            context.getSharedPreferences(
                                OverlayHostService.WIDGET_POSITION_PREFS,
                                android.content.Context.MODE_PRIVATE,
                            ).edit().remove("x_$widgetId").remove("y_$widgetId").apply()
                        }.onSuccess {
                            error = null
                            refresh++
                        }.onFailure { error = it.message ?: "Widget could not be removed" }
                    }
                    toDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            },
        )
    }
}
