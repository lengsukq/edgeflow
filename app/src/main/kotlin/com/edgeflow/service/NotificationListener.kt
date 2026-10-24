package com.edgeflow.service

import android.app.Notification
import android.os.Build
import android.os.Parcelable
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.edgeflow.EdgeFlowApp
import com.edgeflow.core.StateTriggerId
import com.edgeflow.core.model.Action
import com.edgeflow.core.model.Action.Sequence
import com.edgeflow.core.model.Action.Simple
import com.edgeflow.core.model.Action.Special

/**
 * Provides the notification-derived triggers.
 *
 * A notification listener is used rather than a hook because the information is
 * already available to any app that the user grants access to, and asking for
 * that grant is more honest than silently reading notifications from inside the
 * system server.
 *
 * The service only forwards the *fact* that a notification arrived; it never
 * reads or stores the content. A binding attached to the SMS trigger therefore
 * fires without the module learning what the message said.
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        // Ongoing notifications are the system's own (media, downloads); firing
        // on them would make the trigger fire constantly.
        if (notification.isOngoing) return
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return
        if (notification.packageName != smsPackage) return
        val posted = notification.notification
        val isMessage = posted.category == Notification.CATEGORY_MESSAGE || hasMessagingStyleMessages(posted)
        if (!isMessage) return

        val app = applicationContext as? EdgeFlowApp ?: return
        val action = app.configRepository.current.stateTriggers[StateTriggerId.SMS_RECEIVED] ?: return
        if (action.id == com.edgeflow.core.ActionId.NONE) return

        // The action runs in the system server, so it cannot be executed from
        // here directly. Broadcasting the intent lets the hook pick it up.
        val intent = android.content.Intent(com.edgeflow.core.Ipc.PERFORM_ACTION)
            .setPackage(com.edgeflow.core.Ipc.SYSTEM_PACKAGE)
            .putExtra(com.edgeflow.core.Ipc.EXTRA_TRIGGER, StateTriggerId.SMS_RECEIVED)
        sendBroadcast(intent)
    }

    @Suppress("DEPRECATION")
    private fun hasMessagingStyleMessages(notification: Notification): Boolean {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        return messages?.isNotEmpty() == true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
