package com.oddley.next.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oddley.next.data.SnoozeRepository
import com.oddley.next.data.TaskRepository
import com.oddley.next.domain.snooze.NullSnoozeSession
import com.oddley.next.domain.snooze.SnoozeSession
import com.oddley.next.domain.task.Task
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.crossedOffTasks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A task entry in the display-ordered list.
 *
 * Display order differs from underlying (DB) order when a snooze session is active:
 *   [underlying[offset], underlying[0..offset-1] (💤), underlying[offset+1..] ]
 *
 * [isSnoozed] drives the 💤 icon in the list row.
 */
data class DisplayTask(
    val task: Task,
    val isSnoozed: Boolean,
)

data class ListUiState(
    /** Display-ordered tasks (may differ from activeTasks order when snooze is active). */
    val displayTasks: List<DisplayTask> = emptyList(),
    /** Canonical DB order — used for drag-and-drop index mapping. */
    val activeTasks: List<Task> = emptyList(),
    val crossedOffTasks: List<Task> = emptyList(),
    val snoozeSession: SnoozeSession = NullSnoozeSession,
)

class ListViewModel(
    private val taskRepository: TaskRepository,
    private val snoozeRepository: SnoozeRepository,
) : ViewModel() {

    val uiState: StateFlow<ListUiState> = combine(
        taskRepository.tasks,
        snoozeRepository.session,
    ) { tasks, session ->
        val active = activeTasks(tasks)
        ListUiState(
            displayTasks = computeDisplayTasks(active, session),
            activeTasks = active,
            crossedOffTasks = crossedOffTasks(tasks),
            snoozeSession = session,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    fun addTask(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { taskRepository.addTask(text.trim()) }
    }

    fun crossOff(id: Long) {
        viewModelScope.launch { taskRepository.crossOff(id) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { taskRepository.restore(id) }
    }

    fun editText(id: Long, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch { taskRepository.editText(id, newText.trim()) }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { taskRepository.reorder(fromIndex, toIndex) }
    }

    fun bulkDeleteCrossedOff() {
        viewModelScope.launch { taskRepository.bulkDeleteCrossedOff() }
    }

    fun clearSnooze() {
        viewModelScope.launch { snoozeRepository.clear() }
    }

    // ── Display ordering ─────────────────────────────────────────────────────

    /**
     * Reorders [activeTasks] for display when a snooze session is active.
     *
     * With offset O (0 < O < N):
     *   display = [ activeTasks[O],                 ← top (not snoozed)
     *               activeTasks[0..O-1] (💤),       ← snoozed items
     *               activeTasks[O+1..N-1] ]          ← remaining
     *
     * Degenerate cases (offset=0 or offset≥N) fall back to the natural order.
     */
    private fun computeDisplayTasks(activeTasks: List<Task>, session: SnoozeSession): List<DisplayTask> {
        val offset = session.offset
        val size = activeTasks.size
        if (size == 0) return emptyList()
        return when {
            offset <= 0 || offset >= size ->
                activeTasks.map { DisplayTask(it, isSnoozed = false) }
            else -> {
                val top = DisplayTask(activeTasks[offset], isSnoozed = false)
                val snoozed = activeTasks.subList(0, offset).map { DisplayTask(it, isSnoozed = true) }
                val remaining = activeTasks.subList(offset + 1, size).map { DisplayTask(it, isSnoozed = false) }
                listOf(top) + snoozed + remaining
            }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val taskRepository: TaskRepository,
        private val snoozeRepository: SnoozeRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ListViewModel(taskRepository, snoozeRepository) as T
    }
}
