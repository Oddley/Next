package com.oddley.next.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules (or cancels) the single exact-alarm used for Task Emitter emissions.
 *
 * Strategy: one alarm at a time, always set to the earliest [TaskEmitter.nextEmission]
 * across all active emitters. On each alarm fire (or emitter change), this object is
 * called to cancel the old alarm and set a fresh one.
 *
 * Requires SCHEDULE_EXACT_ALARM permission (declared in AndroidManifest; minSdk 31).
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 1001

    /**
     * Cancels any existing emission alarm and, if [nextEmissionMs] is non-null,
     * schedules a new exact alarm at that timestamp.
     */
    fun scheduleNext(context: Context, nextEmissionMs: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        am.cancel(pi)
        if (nextEmissionMs == null) return
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextEmissionMs, pi)
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
