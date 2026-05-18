package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts [TopTaskService] after device reboot.
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in the manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TopTaskService.start(context)
        }
    }
}
