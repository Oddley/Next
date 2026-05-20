package com.oddley.next.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records the epoch-ms timestamp of the last emission check.
 *
 * Used by [EmissionAlarmReceiver] (Phase 5) to detect missed emission intervals
 * and apply catch-up logic via modulus math rather than replaying every interval.
 *
 * Singleton row (id = 1 always). Defaults to 0 (no previous check recorded).
 */
@Entity(tableName = "last_processed")
data class LastProcessedEntity(
    @PrimaryKey val id: Int = 1,
    val epochMs: Long = 0L,
)
