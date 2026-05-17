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
    val activeTasks: List<Task> = emptyList(),
    val crossedOffTasks: List<Task> = emptyList(),
)

class ListViewModel(private val repository: TaskRepository) : ViewModel() {

    val uiState: StateFlow<ListUiState> = repository.tasks
        .map { tasks ->
            ListUiState(
                activeTasks = activeTasks(tasks),
                crossedOffTasks = crossedOffTasks(tasks),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListUiState(),
        )

    fun addTask(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addTask(text.trim()) }
    }

    fun crossOff(id: Long) {
        viewModelScope.launch { repository.crossOff(id) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun editText(id: Long, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch { repository.editText(id, newText.trim()) }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { repository.reorder(fromIndex, toIndex) }
    }

    fun bulkDeleteCrossedOff() {
        viewModelScope.launch { repository.bulkDeleteCrossedOff() }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ListViewModel(repository) as T
    }
}
