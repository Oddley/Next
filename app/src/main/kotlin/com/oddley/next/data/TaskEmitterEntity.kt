package com.oddley.next.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oddley.next.domain.emitter.TaskEmitter

@Entity(tableName = "task_emitters")
data class TaskEmitterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val rrule: String,
    val dtStart: Long,
    val nextEmission: Long?,    // null = exhausted (COUNT/UNTIL reached)
)

fun TaskEmitterEntity.toDomain() = TaskEmitter(
    id = id,
    label = label,
    rrule = rrule,
    dtStart = dtStart,
    nextEmission = nextEmission,
)

fun TaskEmitter.toEntity() = TaskEmitterEntity(
    id = id,
    label = label,
    rrule = rrule,
    dtStart = dtStart,
    nextEmission = nextEmission,
)
