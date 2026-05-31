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
 * Restarts [TopTaskService] and reschedules the emission alarm after device reboot.
 *
 * Exact alarms are cleared by Android on reboot, so we must re-arm here. We also
 * call processEmissions in case any emission was due while the device was off.
 * Requires RECEIVE_BOOT_COMPLETED permission in the manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        AppLogger.log(context, "BootReceiver", "boot completed — restarting service and rescheduling alarms")
        TopTaskService.start(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as NextApplication
                val now = System.currentTimeMillis()
                // Process any emissions that were due while the device was off
                val fired = app.emitterRepository.processEmissions(now)
                AppLogger.log(context, "BootReceiver", "processEmissions fired=$fired")
                val nextMs = app.emitterRepository.earliestNextEmission()
                AlarmScheduler.scheduleNext(context, nextMs)
            } catch (e: Exception) {
                AppLogger.log(context, "BootReceiver", "ERROR: ${e::class.simpleName}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
