package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Snooze" notification action.
 *
 * Delegates entirely to [SnoozeRepository.snooze]; the repository applies
 * the domain logic and persists the result. The service's Flow collector then
 * picks up the change and refreshes the notification automatically.
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopTaskService.ACTION_SNOOZE) return

        val app = context.applicationContext as NextApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.snoozeRepository.snooze(System.currentTimeMillis())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
