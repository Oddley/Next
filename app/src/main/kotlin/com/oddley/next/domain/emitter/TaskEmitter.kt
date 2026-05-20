package com.oddley.next.domain.emitter

/**
 * Represents a scheduled or recurring task template.
 *
 * When [nextEmission] arrives, the emitter creates or resurfaces its associated
 * task (one-task-per-emitter constraint) and advances to the next occurrence.
 *
 * The [rrule] is an RFC 5545 RRULE string (e.g. "FREQ=DAILY", "FREQ=WEEKLY;BYDAY=MO,WE").
 * One-time emitters use "FREQ=DAILY;COUNT=1".
 *
 * [dtStart] is the epoch-ms anchor for COUNT/UNTIL math — always the original
 * first occurrence, never updated as the emitter fires.
 *
 * [nextEmission] is null when the rule is exhausted (COUNT/UNTIL reached).
 */
data class TaskEmitter(
    val id: Long,
    val label: String,
    val rrule: String,
    val dtStart: Long,
    val nextEmission: Long?,
)

/** Null Object sentinel for "no emitter". Avoids nullable returns in domain code. */
val NullTaskEmitter = TaskEmitter(
    id = -1L,
    label = "",
    rrule = "",
    dtStart = 0L,
    nextEmission = null,
)
