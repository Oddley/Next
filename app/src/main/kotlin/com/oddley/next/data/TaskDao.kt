package com.oddley.next.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the tasks table.
 *
 * The Repository is the only caller. Never call DAO methods from ViewModels
 * or Composables.
 */
@Dao
interface TaskDao {

    /** Emits the full task list whenever any row changes. */
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    /** One-shot snapshot for mutation operations (load → transform → save). */
    @Query("SELECT * FROM tasks")
    suspend fun getAllOnce(): List<TaskEntity>

    /** Inserts a new task. Returns the auto-generated row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskEntity): Long

    /** Updates an existing task row (matched by primary key). */
    @Update
    suspend fun update(entity: TaskEntity)

    /** Updates multiple rows in one call. */
    @Update
    suspend fun updateAll(entities: List<TaskEntity>)

    /** Deletes a single task by id. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Deletes all crossed-off tasks. */
    @Query("DELETE FROM tasks WHERE crossedOff = 1")
    suspend fun deleteAllCrossedOff()
}
