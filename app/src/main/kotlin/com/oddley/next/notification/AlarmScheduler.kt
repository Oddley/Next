package com.oddley.next.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oddley.next.util.AppLogger

/**
 * Schedules (or cancels) the single exact-alarm used for Task Emitter emissions.
 *
 * Strategy: one alarm at a time, always set to the earliest [TaskEmitter.nextEmission]
 * across all active emitters. On each alarm fire (or emitter change), this object is
 * called to cancel the old alarm and set a fresh one.
 *
 * On Android 13+ (targeting SDK 33+), SCHEDULE_EXACT_ALARM is not auto-granted.
 * We check canScheduleExactAlarms() and fall back to setAndAllowWhileIdle (inexact,
 * fires within a few minutes) rather than crashing with SecurityException.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val REQUEST_CODE = 1001

    /**
     * Cancels any existing emission alarm and, if [nextEmissionMs] is non-null,
     * schedules a new alarm at that timestamp. Uses an exact alarm when the OS
     * grants SCHEDULE_EXACT_ALARM; falls back to inexact otherwise.
     */
    fun scheduleNext(context: Context, nextEmissionMs: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        am.cancel(pi)
        if (nextEmissionMs == null) {
            AppLogger.log(context, TAG, "cancel — no active emitters")
            return
        }
        if (am.canScheduleExactAlarms()) {
            AppLogger.log(context, TAG, "scheduleExact at $nextEmissionMs")
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextEmissionMs, pi)
        } else {
            // Exact alarm permission not granted — inexact fires within ~few minutes
            AppLogger.log(context, TAG, "WARN exact alarm permission not granted; using inexact at $nextEmissionMs")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextEmissionMs, pi)
        }
    }

    /** Cancels the emission alarm without rescheduling (e.g., when all emitters are deleted). */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, EmissionAlarmReceiver::class.java).apply {
            action = "com.oddley.next.EMISSION_ALARM"
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
