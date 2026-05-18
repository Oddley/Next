package com.oddley.next.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oddley.next.MainActivity
import com.oddley.next.R
import com.oddley.next.app.NextApplication
import com.oddley.next.domain.snooze.CurrentTop
import com.oddley.next.domain.snooze.NullSnoozeSession
import com.oddley.next.domain.snooze.SnoozeSession
import com.oddley.next.domain.snooze.computeCurrentTop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the persistent "current top task" notification.
 *
 * Lifecycle:
 *   - Started from [MainActivity] on launch and from [BootReceiver] after reboot.
 *   - Runs indefinitely; [START_STICKY] causes restart after OS kills it.
 *   - Observes TaskRepository.tasks ⊕ SnoozeRepository.session; rebuilds the
 *     notification on every emission via [startForeground] to re-anchor it.
 *
 * Swipe-to-dismiss (Android 13+):
 *   The platform allows foreground service notifications to be swiped away.
 *   The notification carries a [deleteIntent] pointing at [NotificationDismissedReceiver],
 *   which immediately calls [start] again — [onStartCommand] re-posts [lastNotification]
 *   within one frame, making the notification effectively permanent.
 *
 * Action intents:
 *   - [ACTION_MARK_COMPLETE] → [MarkCompleteReceiver]
 *   - [ACTION_SNOOZE]        → [SnoozeReceiver]
 */
class TopTaskService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var observing = false
    private var lastNotification: Notification? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        // Re-anchor: re-call startForeground with the most recent notification we have.
        // On first start lastNotification is null → use placeholder; the Flow collector
        // will replace it within milliseconds. On subsequent starts (dismiss re-post) we
        // immediately show the correct content with no flicker.
        startForeground(
            NOTIFICATION_ID,
            lastNotification ?: buildNotification(this, CurrentTop.Empty, NullSnoozeSession),
        )
        if (!observing) {
            observing = true
            observeState()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun observeState() {
        val app = application as NextApplication
        scope.launch {
            combine(app.taskRepository.tasks, app.snoozeRepository.session) { tasks, session ->
                Pair(tasks, session)
            }.collect { (tasks, session) ->
                val top = computeCurrentTop(tasks, session, System.currentTimeMillis())
                val notification = buildNotification(this@TopTaskService, top, session)
                lastNotification = notification
                // Use startForeground (not just notify) so the notification stays
                // bound to the foreground service declaration on every update.
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    companion object {
        // Bumped to _v2 so Android creates a fresh channel with updated importance.
        // (Android ignores importance changes on existing channels.)
        const val CHANNEL_ID = "next_top_task_v2"
        const val NOTIFICATION_ID = 1
        const val ACTION_MARK_COMPLETE = "com.oddley.next.MARK_COMPLETE"
        const val ACTION_SNOOZE = "com.oddley.next.SNOOZE"
        const val ACTION_NOTIFICATION_DISMISSED = "com.oddley.next.NOTIFICATION_DISMISSED"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TopTaskService::class.java))
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            // Clean up old low-importance channel if present
            nm.deleteNotificationChannel("next_top_task")
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Current task",
                // IMPORTANCE_DEFAULT → "Alerting" section (not "Silent")
                // Sound + vibration disabled so it's quiet despite the higher tier.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Shows your current top task at all times"
                setShowBadge(false)
                setSound(null, null)          // no sound
                enableVibration(false)        // no vibration
                enableLights(false)
            }
            nm.createNotificationChannel(channel)
        }

        fun buildNotification(
            context: Context,
            top: CurrentTop,
            session: SnoozeSession,
        ): Notification {
            val (title, isSnoozedFallback) = when (top) {
                is CurrentTop.Real -> top.task.text to false
                is CurrentTop.SnoozedFallback -> top.task.text to true
                CurrentTop.Empty -> "All caught up 🎉" to false
            }
            val contentText = if (isSnoozedFallback) "↩ Showing top snoozed item" else null

            // Tap notification body → open app
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // Delete intent: re-anchor when user swipes (Android 13+)
            val deleteIntent = PendingIntent.getBroadcast(
                context, 10,
                Intent(ACTION_NOTIFICATION_DISMISSED).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)       // no heads-up on content updates
                .setSilent(true)              // belt-and-suspenders: suppress any alert
                .setContentIntent(openApp)
                .setDeleteIntent(deleteIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (contentText != null) builder.setContentText(contentText)

            if (top !is CurrentTop.Empty) {
                builder.addAction(0, "Mark complete", pendingBroadcast(context, ACTION_MARK_COMPLETE, 1))
                builder.addAction(0, "Snooze", pendingBroadcast(context, ACTION_SNOOZE, 2))
            }

            return builder.build()
        }

        private fun pendingBroadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
