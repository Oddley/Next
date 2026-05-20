package com.oddley.next.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the last_processed singleton table.
 */
@Dao
interface LastProcessedDao {

    /** One-shot read (null if no row exists yet). */
    @Query("SELECT * FROM last_processed WHERE id = 1")
    suspend fun getOnce(): LastProcessedEntity?

    /** Inserts or replaces the single row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LastProcessedEntity)
}
