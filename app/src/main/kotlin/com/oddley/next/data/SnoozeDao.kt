package com.oddley.next.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the singleton snooze_session table.
 *
 * The table has at most one row (id = 1 always).
 * Absence of a row means no active snooze session.
 */
@Dao
interface SnoozeDao {

    /** Emits the current session (or null) on every change. */
    @Query("SELECT * FROM snooze_session WHERE id = 1")
    fun observe(): Flow<SnoozeSessionEntity?>

    /** One-shot read for mutation operations. */
    @Query("SELECT * FROM snooze_session WHERE id = 1")
    suspend fun getOnce(): SnoozeSessionEntity?

    /** Inserts or replaces the single session row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnoozeSessionEntity)

    /** Removes the session row (clears snooze). */
    @Query("DELETE FROM snooze_session")
    suspend fun delete()
}
