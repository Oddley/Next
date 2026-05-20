package com.oddley.next.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oddley.next.data.TaskRepository
import com.oddley.next.data.UiPrefsRepository
import com.oddley.next.domain.task.NullTask
import com.oddley.next.domain.task.SNOOZE_DURATION_MS
import com.oddley.next.domain.task.Task
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.computeNext
import com.oddley.next.domain.task.crossedOffTasks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    /** Task shown in the NEXT section and notification. NullTask when list is empty. */
    val nextTask: Task = NullTask,
    /** Active (uncrossed) tasks in drag order. */
    val activeTasks: List<Task> = emptyList(),
    val crossedOffTasks: List<Task> = emptyList(),
    /** Persisted section collapse state. */
    val tasksExpanded: Boolean = true,
    val emittersExpanded: Boolean = true,
    val completedExpanded: Boolean = false,
)

class ListViewModel(
    private val taskRepository: TaskRepository,
    private val uiPrefsRepository: UiPrefsRepository,
) : ViewModel() {

    val uiState: StateFlow<ListUiState> = combine(
        taskRepository.tasks,
        uiPrefsRepository.prefs,
    ) { tasks, prefs ->
        ListUiState(
            nextTask = computeNext(tasks, System.currentTimeMillis()),
            activeTasks = activeTasks(tasks),
            crossedOffTasks = crossedOffTasks(tasks),
            tasksExpanded = prefs.tasksExpanded,
            emittersExpanded = prefs.emittersExpanded,
            completedExpanded = prefs.completedExpanded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    // ── NEXT section actions ──────────────────────────────────────────────────

    /** Crosses off the current NEXT task. */
    fun markComplete() {
        val id = uiState.value.nextTask.id
        if (id == NullTask.id) return
        viewModelScope.launch { taskRepository.crossOff(id) }
    }

    /** Snoozes the current NEXT task for [SNOOZE_DURATION_MS]. */
    fun snooze() {
        val id = uiState.value.nextTask.id
        if (id == NullTask.id) return
        viewModelScope.launch {
            taskRepository.snoozeTask(id, System.currentTimeMillis() + SNOOZE_DURATION_MS)
        }
    }

    // ── Task list actions ─────────────────────────────────────────────────────

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

    // ── Section collapse toggles ──────────────────────────────────────────────

    fun toggleTasksExpanded() {
        viewModelScope.launch {
            uiPrefsRepository.setTasksExpanded(!uiState.value.tasksExpanded)
        }
    }

    fun toggleEmittersExpanded() {
        viewModelScope.launch {
            uiPrefsRepository.setEmittersExpanded(!uiState.value.emittersExpanded)
        }
    }

    fun toggleCompletedExpanded() {
        viewModelScope.launch {
            uiPrefsRepository.setCompletedExpanded(!uiState.value.completedExpanded)
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val taskRepository: TaskRepository,
        private val uiPrefsRepository: UiPrefsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ListViewModel(taskRepository, uiPrefsRepository) as T
    }
}
