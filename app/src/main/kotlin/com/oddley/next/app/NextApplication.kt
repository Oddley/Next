package com.oddley.next.app

import android.app.Application
import com.oddley.next.data.EmitterRepository
import com.oddley.next.data.NextDatabase
import com.oddley.next.data.TaskRepository
import com.oddley.next.data.UiPrefsRepository
import com.oddley.next.notification.TopTaskService
import com.oddley.next.util.AppLogger
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Composition root. Creates the object graph once for the application lifetime.
 *
 * Usage from Activity / Service / Receiver:
 *   val app = context.applicationContext as NextApplication
 *   app.taskRepository
 *   app.emitterRepository
 */
class NextApplication : Application() {

    private val database: NextDatabase by lazy {
        NextDatabase.create(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }

    val emitterRepository: EmitterRepository by lazy {
        EmitterRepository(database.emitterDao(), database.taskDao())
    }

    val uiPrefsRepository: UiPrefsRepository by lazy {
        UiPrefsRepository(database.uiPrefsDao())
    }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        TopTaskService.ensureChannel(this)
    }

    // Crashes otherwise leave no trace in next.log — the process just dies. Log the
    // full stack trace before handing off to the platform's default handler so a
    // future "Submit Log" capture actually shows what killed the app.
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            AppLogger.log(this, "CRASH", "Uncaught exception on ${thread.name}:\n$sw")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
