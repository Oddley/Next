package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the notification's deleteIntent when the user swipes it away.
 *
 * Android 13+ allows foreground service notifications to be dismissed by swipe.
 * Responding by immediately restarting the service causes [TopTaskService.onStartCommand]
 * to call [android.app.Service.startForeground] again, which re-anchors the notification.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TopTaskService.ACTION_NOTIFICATION_DISMISSED) {
            TopTaskService.start(context)
        }
    }
}
