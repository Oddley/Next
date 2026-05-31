package com.oddley.next.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight file logger. Appends timestamped lines to [LOG_FILE] in the app's
 * private files directory. Also forwards to android.util.Log for Logcat.
 *
 * The file is deleted and restarted when it exceeds [MAX_BYTES], keeping disk
 * usage bounded without needing a rolling-file library.
 */
object AppLogger {

    private const val LOG_FILE = "next.log"
    private const val MAX_BYTES = 200 * 1024L // 200 KB
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context, tag: String, msg: String) {
        Log.d(tag, msg)
        try {
            val file = File(context.applicationContext.filesDir, LOG_FILE)
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText("${fmt.format(Date())} [$tag] $msg\n")
        } catch (_: Exception) {
            // Never let logging crash the app
        }
    }

    /** Returns up to [maxChars] of the most recent log content. */
    fun readRecent(context: Context, maxChars: Int = 8_000): String {
        return try {
            val file = File(context.applicationContext.filesDir, LOG_FILE)
            if (!file.exists()) return "(log is empty)"
            val text = file.readText()
            if (text.length <= maxChars) text else "...\n${text.takeLast(maxChars)}"
        } catch (e: Exception) {
            "(could not read log: ${e.message})"
        }
    }
}
