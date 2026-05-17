package com.oddley.next.data

import com.oddley.next.domain.task.Task
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.bulkDeleteCrossedOff
import com.oddley.next.domain.task.crossOff
import com.oddley.next.domain.task.crossedOffTasks
import com.oddley.next.domain.task.editText
import com.oddley.next.domain.task.nextOrderForInsert
import com.oddley.next.domain.task.reorder
import com.oddley.next.domain.task.restore
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
        val changedIds = current.zip(updated)
            .filter { (a, b) -> a.order != b.order }
            .map { (_, b) -> b }
        dao.updateAll(changedIds.map { it.toEntity() })
    }

    suspend fun bulkDeleteCrossedOff() {
        dao.deleteAllCrossedOff()
    }
}
