package com.oddley.next.data

import com.oddley.next.domain.task.NullTask
import com.oddley.next.domain.task.Task
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.bulkDeleteCrossedOff
import com.oddley.next.domain.task.computeNext
import com.oddley.next.domain.task.crossOff
import com.oddley.next.domain.task.crossedOffTasks
import com.oddley.next.domain.task.editText
import com.oddley.next.domain.task.nextOrderForInsert
import com.oddley.next.domain.task.reorder
import com.oddley.next.domain.task.restore
import com.oddley.next.domain.task.snoozeTask
import com.oddley.next.domain.task.unsnoozeTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for task data.
 *
 * All mutations follow the same pattern:
 *   1. Load full list from DB (getAllOnce)
 *   2. Delegate to a pure domain function
 *   3. Persist only the changed rows back to DB
 *
 * Callers (ViewModel) observe [tasks] and call suspend mutation functions
 * from a coroutine scope.
 */
class TaskRepository(private val dao: TaskDao) {

    /** Emits the full task list (both active and crossed) on every DB change. */
    val tasks: Flow<List<Task>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    /** One-shot snapshot of the full task list (for notification receivers). */
    suspend fun tasksOnce(): List<Task> = dao.getAllOnce().map { it.toDomain() }

    // ── Mutations ─────────────────────────────────────────────────────────────

    suspend fun addTask(text: String) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val order = nextOrderForInsert(current)
        dao.insert(TaskEntity(text = text, order = order, crossedOff = false))
    }

    suspend fun crossOff(id: Long) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val updated = crossOff(current, id)
        val changed = updated.filter { task ->
            current.first { it.id == task.id }.crossedOff != task.crossedOff
        }
        dao.updateAll(changed.map { it.toEntity() })
    }

    suspend fun restore(id: Long) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val updated = restore(current, id)
        val changed = updated.filter { task ->
            val original = current.firstOrNull { it.id == task.id } ?: return@filter false
            original != task
        }
        dao.updateAll(changed.map { it.toEntity() })
    }

    suspend fun editText(id: Long, newText: String) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val updated = editText(current, id, newText)
        val changed = updated.filter { task ->
            current.first { it.id == task.id }.text != task.text
        }
        dao.updateAll(changed.map { it.toEntity() })
    }

    suspend fun reorder(fromIndex: Int, toIndex: Int) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val updated = reorder(current, fromIndex, toIndex)
        // Reorder can change order values of many tasks — update all that changed.
        // Compare by id, not by position: zip() would pair tasks from different positions
        // (current has no ORDER BY, updated is sorted active-then-crossed) and would
        // always see identical order values even when tasks actually moved.
        val changedIds = updated.filter { updatedTask ->
            current.first { it.id == updatedTask.id }.order != updatedTask.order
        }
        dao.updateAll(changedIds.map { it.toEntity() })
    }

    suspend fun bulkDeleteCrossedOff() {
        dao.deleteAllCrossedOff()
    }

    /**
     * Snoozes the task with [id] until [until] (epoch ms).
     *
     * **All-snoozed fallback:** if snoozing would leave zero non-snoozed active tasks
     * (no NEXT task available), all active task snoozes are cleared instead so the list
     * never goes entirely dark. This matches Elly's expectation that snoozed items
     * un-snooze when you reach the bottom of the list.
     */
    suspend fun snoozeTask(id: Long, until: Long) {
        val current = dao.getAllOnce().map { it.toDomain() }
        val now = System.currentTimeMillis()

        // Apply the requested snooze tentatively
        val tentative = current.map { if (it.id == id) snoozeTask(it, until) else it }

        val toSave: List<Task> = if (computeNext(tentative, now) == NullTask) {
            // Every active task would be snoozed — clear all snoozes instead
            current.map { if (!it.crossedOff) unsnoozeTask(it) else it }
        } else {
            tentative
        }

        val changed = toSave.filter { task ->
            current.firstOrNull { it.id == task.id } != task
        }
        if (changed.isNotEmpty()) {
            dao.updateAll(changed.map { it.toEntity() })
        }
    }

    /** Clears [snoozedUntil] on the task with [id]. No-op if id not found. */
    suspend fun unsnoozeTask(id: Long) {
        val task = dao.getAllOnce().map { it.toDomain() }.firstOrNull { it.id == id } ?: return
        dao.update(unsnoozeTask(task).toEntity())
    }
}
