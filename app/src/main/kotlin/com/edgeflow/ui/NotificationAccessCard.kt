package com.edgeflow.ui

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.app.NotificationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.edgeflow.service.NotificationListener
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow

@Composable
fun NotificationAccessCard() {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val component = remember(context) { ComponentName(context, NotificationListener::class.java) }
    val granted = remember(refreshKey) {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(component) == true
    }

    SectionCard(
        title = "Notification access",
        subtitle = "Used only to detect messages from the selected default SMS app; notification contents are not stored.",
    ) {
        SettingRow(
            title = if (granted) "Notification access granted" else "Grant notification access",
            subtitle = if (granted) "SMS trigger can observe eligible message notifications." else "Android requires explicit special access for the SMS trigger.",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                refreshKey++
            },
        )
    }
    CardSpacer()
}
