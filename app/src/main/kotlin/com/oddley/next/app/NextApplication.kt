package com.oddley.next.app

import android.app.Application
import com.oddley.next.data.NextDatabase
import com.oddley.next.data.SnoozeRepository
import com.oddley.next.data.TaskRepository
import com.oddley.next.notification.TopTaskService

/**
 * Composition root. Creates the object graph once for the application lifetime.
 *
 * Usage from Activity / Service / Receiver:
 *   val app = context.applicationContext as NextApplication
 *   app.taskRepository / app.snoozeRepository
 */
class NextApplication : Application() {

    private val database: NextDatabase by lazy {
        NextDatabase.create(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }

    val snoozeRepository: SnoozeRepository by lazy {
        SnoozeRepository(database.snoozeDao())
    }

    override fun onCreate() {
        super.onCreate()
        TopTaskService.ensureChannel(this)
    }
}
