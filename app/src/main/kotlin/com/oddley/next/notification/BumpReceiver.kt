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
 * Handles the "Bump" notification action.
 *
 * Moves the current NEXT task to the bottom of the active list so the next
 * task becomes NEXT. Unlike snooze, this is permanent — the task stays at the
 * bottom until the user manually reorders it.
 */
class BumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopTaskService.ACTION_BUMP) return

        val app = context.applicationContext as NextApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = app.taskRepository.tasksOnce()
                val top = computeNext(tasks, System.currentTimeMillis())
                if (top != NullTask) {
                    app.taskRepository.bumpToBottom(top.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
