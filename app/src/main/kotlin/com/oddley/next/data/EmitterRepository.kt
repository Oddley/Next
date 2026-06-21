package com.oddley.next.data

import android.util.Log
import com.oddley.next.domain.emitter.TaskEmitter
import com.oddley.next.domain.emitter.advanceEmitter
import com.oddley.next.domain.emitter.computeNextEmission
import com.oddley.next.domain.emitter.shouldEmit
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.orderForTopInsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for Task Emitter data.
 *
 * [processEmissions] is called by [com.oddley.next.notification.EmissionAlarmReceiver]
 * when the scheduled alarm fires. It applies the one-task-per-emitter rule:
 * find the emitter's existing task and resurface it (uncross + unsnooze + move to top),
 * or create a new one if the task was deleted.
 */
class EmitterRepository(
    private val dao: EmitterDao,
    private val taskDao: TaskDao,
) {

    private val emissionMutex = Mutex()

    val emitters: Flow<List<TaskEmitter>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new emitter. [dtStart] is the epoch-ms for the first occurrence;
     * [rrule] is an RFC 5545 RRULE string. Returns the auto-generated id.
     */
    suspend fun addEmitter(label: String, rrule: String, dtStart: Long): Long {
        val nextEmission = computeNextEmission(rrule, dtStart, dtStart - 1)
        return dao.insert(
            TaskEmitterEntity(
                label = label,
                rrule = rrule,
                dtStart = dtStart,
                nextEmission = nextEmission,
            )
        )
    }

    suspend fun updateEmitter(emitter: TaskEmitter) {
        // Recompute nextEmission so the edited schedule takes effect immediately.
        val now = System.currentTimeMillis()
        val updated = emitter.copy(
            nextEmission = computeNextEmission(emitter.rrule, emitter.dtStart, now),
        )
        dao.update(updated.toEntity())
    }

    suspend fun deleteEmitter(id: Long) {
        dao.deleteById(id)
        // Leave the associated task in place — it becomes a manual task (emitterId still
        // points to the deleted emitter, but the emitter no longer exists so it is never
        // resurfaced again). The user can delete or complete it manually.
    }

    // ── Scheduling helpers ────────────────────────────────────────────────────

    /** Returns the earliest [TaskEmitter.nextEmission] across all active emitters, or null. */
    suspend fun earliestNextEmission(): Long? =
        dao.getAllOnce().mapNotNull { it.nextEmission }.minOrNull()

    // ── Emission processing ───────────────────────────────────────────────────

    /**
     * Processes all emitters whose [TaskEmitter.nextEmission] ≤ [now].
     *
     * For each due emitter:
     * 1. Finds the task with matching [TaskEntity.emitterId].
     *    - If found: uncross, unsnooze, and move to top of active list.
     *    - If not found: insert a new task at the top of the active list.
     * 2. Advances the emitter's [TaskEmitter.nextEmission] to the next future
     *    occurrence (null if the rule is exhausted).
     *
     * Returns true if at least one emitter fired (so the caller can reschedule).
     */
    suspend fun processEmissions(now: Long): Boolean = emissionMutex.withLock {
        val emitters = dao.getAllOnce().map { it.toDomain() }
        val due = emitters.filter { shouldEmit(it, now) }
        Log.d("EmitterRepository", "processEmissions: ${emitters.size} emitters, ${due.size} due at $now")
        if (due.isEmpty()) return false

        val tasks = taskDao.getAllOnce().map { it.toDomain() }
        val active = activeTasks(tasks)
        // Compute base insertion order; each subsequent emitter gets a lower value.
        var insertOrder = orderForTopInsert(active)

        for (emitter in due) {
            val existingTask = tasks.firstOrNull { it.emitterId == emitter.id }
            if (existingTask != null) {
                Log.d("EmitterRepository", "resurface task ${existingTask.id} for emitter ${emitter.id} (${emitter.label})")
                // Resurface: uncross, unsnooze, move to top
                taskDao.update(
                    existingTask.copy(
                        crossedOff = false,
                        snoozedUntil = null,
                        order = insertOrder,
                    ).toEntity()
                )
            } else {
                Log.d("EmitterRepository", "insert new task for emitter ${emitter.id} (${emitter.label})")
                // Create a fresh task linked to this emitter
                taskDao.insert(
                    TaskEntity(
                        text = emitter.label,
                        order = insertOrder,
                        crossedOff = false,
                        snoozedUntil = null,
                        emitterId = emitter.id,
                    )
                )
            }
            insertOrder -= 1000   // each subsequent emitter stacks below the previous

            // Advance the emitter to its next occurrence
            val advanced = advanceEmitter(emitter, now)
            Log.d("EmitterRepository", "advance emitter ${emitter.id}: ${emitter.nextEmission} → ${advanced.nextEmission}")
            dao.update(advanced.toEntity())
        }

        true
    }
}
