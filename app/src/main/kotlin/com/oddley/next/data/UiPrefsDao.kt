package com.oddley.next.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the ui_prefs singleton table.
 */
@Dao
interface UiPrefsDao {

    /** Emits the current prefs row on every change (null if no row exists yet). */
    @Query("SELECT * FROM ui_prefs WHERE id = 1")
    fun observe(): Flow<UiPrefsEntity?>

    /** One-shot read (null if no row exists yet). */
    @Query("SELECT * FROM ui_prefs WHERE id = 1")
    suspend fun getOnce(): UiPrefsEntity?

    /** Inserts or replaces the single prefs row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UiPrefsEntity)
}
