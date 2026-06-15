package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a snoozed task's snooze period expires.
 *
 * Clears [snoozedUntil] on all expired tasks via a DB write, which causes the
 * tasks Flow to emit → [TopTaskService] recomputes NEXT → notification updates.
 * Then reschedules for the next remaining snooze, if any.
 */
class SnoozeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.oddley.next.SNOOZE_ALARM") return

        val app = context.applicationContext as NextApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                app.taskRepository.clearExpiredSnoozes(now)
                val next = app.taskRepository.earliestFutureSnooze()
                SnoozeAlarmScheduler.scheduleNext(context, next)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
