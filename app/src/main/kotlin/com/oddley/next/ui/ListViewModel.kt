package com.oddley.next.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oddley.next.data.TaskRepository
import com.oddley.next.domain.task.Task
import com.oddley.next.domain.task.activeTasks
import com.oddley.next.domain.task.crossedOffTasks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    /** Active (uncrossed) tasks in drag order. */
    val activeTasks: List<Task> = emptyList(),
    val crossedOffTasks: List<Task> = emptyList(),
)

class ListViewModel(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState: StateFlow<ListUiState> = taskRepository.tasks.map { tasks ->
        ListUiState(
            activeTasks = activeTasks(tasks),
            crossedOffTasks = crossedOffTasks(tasks),
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

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val taskRepository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ListViewModel(taskRepository) as T
    }
}
