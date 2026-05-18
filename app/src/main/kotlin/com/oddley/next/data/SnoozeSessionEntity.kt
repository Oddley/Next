package com.oddley.next.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oddley.next.domain.snooze.NullSnoozeSession
import com.oddley.next.domain.snooze.SnoozeSession

/**
 * Room entity for the snooze session singleton.
 *
 * The table always has at most one row, enforced by a fixed primary key of 1.
 * "No session" is represented by the absence of any row — the DAO returns null
 * and the repository converts that to [NullSnoozeSession].
 */
@Entity(tableName = "snooze_session")
data class SnoozeSessionEntity(
    @PrimaryKey val id: Int = 1,
    val expiry: Long,
    val offset: Int,
)

fun SnoozeSessionEntity.toDomain(): SnoozeSession =
    SnoozeSession(expiry = expiry, offset = offset)

fun SnoozeSession.toEntity(): SnoozeSessionEntity =
    SnoozeSessionEntity(expiry = expiry, offset = offset)
