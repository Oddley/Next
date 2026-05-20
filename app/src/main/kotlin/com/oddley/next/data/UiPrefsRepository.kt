package com.oddley.next.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for UI section collapse state.
 *
 * Backed by a singleton Room row ([UiPrefsEntity]) so preferences survive app
 * restarts and process death. Emits defaults when no row exists yet.
 */
class UiPrefsRepository(private val dao: UiPrefsDao) {

    /** Emits current prefs on every change; falls back to defaults when row is absent. */
    val prefs: Flow<UiPrefsEntity> = dao.observe().map { it ?: UiPrefsEntity() }

    suspend fun setTasksExpanded(expanded: Boolean) {
        val current = dao.getOnce() ?: UiPrefsEntity()
        dao.upsert(current.copy(tasksExpanded = expanded))
    }

    suspend fun setEmittersExpanded(expanded: Boolean) {
        val current = dao.getOnce() ?: UiPrefsEntity()
        dao.upsert(current.copy(emittersExpanded = expanded))
    }

    suspend fun setCompletedExpanded(expanded: Boolean) {
        val current = dao.getOnce() ?: UiPrefsEntity()
        dao.upsert(current.copy(completedExpanded = expanded))
    }
}
