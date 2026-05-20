package com.oddley.next.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oddley.next.app.NextApplication
import com.oddley.next.domain.task.NullTask
import com.oddley.next.domain.task.computeNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Mark complete" notification action.
 *
 * Finds the current NEXT task via [computeNext] and crosses it off.
 * The service's Flow collector picks up the change and updates the notification.
 */
class MarkCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopTaskService.ACTION_MARK_COMPLETE) return

        val app = context.applicationContext as NextApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = app.taskRepository.tasksOnce()
                val top = computeNext(tasks, System.currentTimeMillis())
                if (top != NullTask) {
                    app.taskRepository.crossOff(top.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
