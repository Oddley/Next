package com.oddley.next.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the collapsed/expanded state of each list section across app restarts.
 *
 * Singleton row (id = 1 always). Absence of a row → use defaults (all expanded,
 * completed collapsed).
 */
@Entity(tableName = "ui_prefs")
data class UiPrefsEntity(
    @PrimaryKey val id: Int = 1,
    val tasksExpanded: Boolean = true,
    val emittersExpanded: Boolean = true,
    val completedExpanded: Boolean = false,
)
