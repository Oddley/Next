package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import com.oddley.next.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when the [AlarmScheduler] exact alarm triggers.
 *
 * Uses [goAsync] so coroutine work (DB + reschedule) can complete after
 * [onReceive] returns. Android guarantees ~10 s before the process is killed.
 */
class EmissionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.log(context, "EmissionAlarmReceiver", "alarm fired")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as NextApplication
                val now = System.currentTimeMillis()
                val fired = app.emitterRepository.processEmissions(now)
                AppLogger.log(context, "EmissionAlarmReceiver", "processEmissions fired=$fired")
                // Ensure the notification service is running so it picks up the new task.
                // If the OS killed it while there were no tasks, it won't be observing the
                // Room Flow and the notification would stay stale ("All caught up").
                TopTaskService.start(context)
                val nextMs = app.emitterRepository.earliestNextEmission()
                AlarmScheduler.scheduleNext(context, nextMs)
            } catch (e: Exception) {
                AppLogger.log(context, "EmissionAlarmReceiver", "ERROR: ${e::class.simpleName}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
