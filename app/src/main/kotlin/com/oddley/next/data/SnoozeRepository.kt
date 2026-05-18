package com.oddley.next.data

import com.oddley.next.domain.snooze.NullSnoozeSession
import com.oddley.next.domain.snooze.SnoozeSession
import com.oddley.next.domain.snooze.applyMarkComplete
import com.oddley.next.domain.snooze.applySnooze
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for the snooze session.
 *
 * Reads emit [NullSnoozeSession] when no row exists in the DB.
 * Writes persist the result of the relevant domain operation.
 */
class SnoozeRepository(private val dao: SnoozeDao) {

    /** Emits the current session on every change. Never null — uses [NullSnoozeSession]. */
    val session: Flow<SnoozeSession> = dao.observe().map { entity ->
        entity?.toDomain() ?: NullSnoozeSession
    }

    /** One-shot snapshot of the current session (for notification receivers). */
    suspend fun sessionOnce(): SnoozeSession = dao.getOnce()?.toDomain() ?: NullSnoozeSession

    /** Applies a Snooze tap and persists the result. */
    suspend fun snooze(now: Long) {
        val current = dao.getOnce()?.toDomain() ?: NullSnoozeSession
        val updated = applySnooze(current, now)
        dao.upsert(updated.toEntity())
    }

    /**
     * Applies a Mark Complete tap to the session and persists the result.
     *
     * Callers must separately cross off the current top task via [TaskRepository].
     */
    suspend fun markComplete(now: Long) {
        val current = dao.getOnce()?.toDomain() ?: NullSnoozeSession
        val updated = applyMarkComplete(current, now)
        if (updated == NullSnoozeSession) {
            dao.delete()
        } else {
            dao.upsert(updated.toEntity())
        }
    }

    /** Clears the snooze session unconditionally (user tapped "Clear snooze"). */
    suspend fun clear() {
        dao.delete()
    }
}
