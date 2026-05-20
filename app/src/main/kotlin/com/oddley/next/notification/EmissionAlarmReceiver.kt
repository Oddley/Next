package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
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
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as NextApplication
                val now = System.currentTimeMillis()
                app.emitterRepository.processEmissions(now)
                val nextMs = app.emitterRepository.earliestNextEmission()
                AlarmScheduler.scheduleNext(context, nextMs)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
