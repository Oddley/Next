package com.oddley.next.app

import android.app.Application
import com.oddley.next.data.NextDatabase
import com.oddley.next.data.TaskRepository

/**
 * Composition root. Creates the object graph once for the application lifetime.
 *
 * Usage from Activity:
 *   val repository = (application as NextApplication).taskRepository
 */
class NextApplication : Application() {

    private val database: NextDatabase by lazy {
        NextDatabase.create(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }
}
