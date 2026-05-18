package com.oddley.next.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database singleton.
 *
 * Version history:
 *   1 — initial schema (tasks table)
 *   2 — add snooze_session singleton table (Phase 2)
 *
 * Every schema change requires an explicit Migration. Never use
 * fallbackToDestructiveMigration() in a release build.
 */
@Database(
    entities = [TaskEntity::class, SnoozeSessionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class NextDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun snoozeDao(): SnoozeDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS snooze_session (
                        id      INTEGER NOT NULL PRIMARY KEY,
                        expiry  INTEGER NOT NULL,
                        offset  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): NextDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NextDatabase::class.java,
                "next.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
