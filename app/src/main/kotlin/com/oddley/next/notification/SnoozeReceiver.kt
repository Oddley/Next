package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import com.oddley.next.domain.task.NullTask
import com.oddley.next.domain.task.SNOOZE_DURATION_MS
import com.oddley.next.domain.task.computeNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Snooze" notification action.
 *
 * Finds the current NEXT task via [computeNext] and sets its [Task.snoozedUntil]
 * to now + [SNOOZE_DURATION_MS]. The service's Flow collector then picks up the
 * change and refreshes the notification to show the new NEXT task automatically.
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopTaskService.ACTION_SNOOZE) return

        val app = context.applicationContext as NextApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val tasks = app.taskRepository.tasksOnce()
                val top = computeNext(tasks, now)
                if (top != NullTask) {
                    app.taskRepository.snoozeTask(top.id, now + SNOOZE_DURATION_MS)
                    // Schedule wake alarm so the task re-enters the queue when snooze expires.
                    val nextWake = app.taskRepository.earliestFutureSnooze()
                    SnoozeAlarmScheduler.scheduleNext(context, nextWake)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
