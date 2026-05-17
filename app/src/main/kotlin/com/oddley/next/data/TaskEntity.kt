package com.oddley.next.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oddley.next.domain.task.Task

/**
 * Room entity for the tasks table. Mirrors [Task] shape.
 *
 * Never expose this type outside the data package — callers work with [Task].
 * Conversion helpers live in TaskRepository.kt.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val order: Int,
    val crossedOff: Boolean,
)

// ── Conversion helpers ────────────────────────────────────────────────────────

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    text = text,
    order = order,
    crossedOff = crossedOff,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    text = text,
    order = order,
    crossedOff = crossedOff,
)
