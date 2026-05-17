package com.oddley.next.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database singleton.
 *
 * Version history:
 *   1 — initial schema (tasks table)
 *
 * Every schema change requires an explicit Migration. Never use
 * fallbackToDestructiveMigration() in a release build.
 */
@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NextDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): NextDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NextDatabase::class.java,
                "next.db",
            ).build()
    }
}
