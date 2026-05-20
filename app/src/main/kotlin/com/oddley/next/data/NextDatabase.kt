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
 *   3 — add snoozedUntil + emitterId to tasks; add ui_prefs + last_processed tables;
 *       drop snooze_session (Phase 3 — per-task snooze replaces SnoozeSession)
 *
 * Every schema change requires an explicit Migration. Never use
 * fallbackToDestructiveMigration() in a release build.
 */
@Database(
    entities = [TaskEntity::class, UiPrefsEntity::class, LastProcessedEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class NextDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun uiPrefsDao(): UiPrefsDao
    abstract fun lastProcessedDao(): LastProcessedDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add per-task snooze and emitter FK columns (nullable, default null)
                db.execSQL("ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN emitterId INTEGER")

                // UI section collapse state (all expanded by default except completed)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ui_prefs (
                        id               INTEGER NOT NULL PRIMARY KEY,
                        tasksExpanded    INTEGER NOT NULL DEFAULT 1,
                        emittersExpanded INTEGER NOT NULL DEFAULT 1,
                        completedExpanded INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Last emission check timestamp (used for catch-up math in Phase 5)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS last_processed (
                        id      INTEGER NOT NULL PRIMARY KEY,
                        epochMs INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Remove snooze session — per-task snoozedUntil replaces it.
                // Any in-progress snooze is discarded; tasks resurface naturally.
                db.execSQL("DROP TABLE IF EXISTS snooze_session")
            }
        }

        fun create(context: Context): NextDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NextDatabase::class.java,
                "next.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
