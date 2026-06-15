package com.oddley.next.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules (or cancels) the alarm that fires when the earliest active snooze expires.
 *
 * On alarm fire, [SnoozeAlarmReceiver] clears expired snoozedUntil values in the DB,
 * which triggers the tasks Flow and causes [TopTaskService] to refresh the notification.
 *
 * Uses the same exact/inexact fallback strategy as [AlarmScheduler].
 */
object SnoozeAlarmScheduler {

    private const val REQUEST_CODE = 1002

    fun scheduleNext(context: Context, nextWakeMs: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        am.cancel(pi)
        if (nextWakeMs == null) return
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextWakeMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextWakeMs, pi)
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
            action = "com.oddley.next.SNOOZE_ALARM"
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
