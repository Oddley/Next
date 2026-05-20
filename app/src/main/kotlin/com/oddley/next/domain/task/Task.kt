package com.oddley.next.domain.task

/**
 * Core task entity. Pure data — no Android, no Room imports.
 *
 * [order] is a sparse integer used to sort tasks within each section
 * (active / crossed) independently. Gaps of 1000 leave room for insertions
 * without full renumbers.
 */
data class Task(
    val id: Long,
    val text: String,
    val order: Int,
    val crossedOff: Boolean,
    /** Epoch ms after which this task is visible again. Null = not snoozed. */
    val snoozedUntil: Long? = null,
    /** FK to TaskEmitter that owns this task. Null = manual task. */
    val emitterId: Long? = null,
)

/**
 * Null Object for Task — represents "no task" without nullable returns.
 * Use instead of Task? anywhere in domain or UI code.
 */
val NullTask = Task(id = -1L, text = "", order = -1, crossedOff = false)
