package com.oddley.next.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmitterDao {

    @Query("SELECT * FROM task_emitters ORDER BY label ASC")
    fun observeAll(): Flow<List<TaskEmitterEntity>>

    @Query("SELECT * FROM task_emitters ORDER BY label ASC")
    suspend fun getAllOnce(): List<TaskEmitterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emitter: TaskEmitterEntity): Long

    @Update
    suspend fun update(emitter: TaskEmitterEntity)

    @Query("DELETE FROM task_emitters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
