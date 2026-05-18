package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import com.oddley.next.domain.snooze.NullSnoozeSession
import com.oddley.next.domain.snooze.computeCurrentTop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Handles the "Mark complete" notification action.
 *
 * Crosses off the current top task, then applies mark-complete to the snooze session
 * (which may clear an expired session). Both operations use the domain layer.
 */
class MarkCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopTaskService.ACTION_MARK_COMPLETE) return

        val app = context.applicationContext as NextApplication
        // goAsync() is overkill for a quick DB operation; use runBlocking on IO.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()

                // 1. Find which task is currently shown
                val tasks = app.taskRepository.tasksOnce()
                val session = app.snoozeRepository.sessionOnce()
                val top = computeCurrentTop(tasks, session, now)

                // 2. Cross off that task
                val taskId = when (top) {
                    is com.oddley.next.domain.snooze.CurrentTop.Real -> top.task.id
                    is com.oddley.next.domain.snooze.CurrentTop.SnoozedFallback -> top.task.id
                    com.oddley.next.domain.snooze.CurrentTop.Empty -> null
                }
                if (taskId != null) {
                    app.taskRepository.crossOff(taskId)
                }

                // 3. Apply mark-complete to the session
                app.snoozeRepository.markComplete(now)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
