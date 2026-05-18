package com.oddley.next.domain.snooze

import com.oddley.next.domain.task.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Exhaustive tests for every state transition defined in ADR-008.
 *
 * Time helpers:
 *   NOW      = an arbitrary "current" moment
 *   FUTURE   = NOW + 10 min (session has not expired)
 *   PAST     = NOW - 1 ms  (session has just expired)
 */
private const val NOW = 1_000_000L
private const val FUTURE = NOW + 10 * 60 * 1_000L   // 10 min ahead → not expired
private const val PAST = NOW - 1L                    // 1 ms ago → expired

private fun task(id: Long, order: Int) =
    Task(id = id, text = "task $id", order = order, crossedOff = false)

private fun crossedTask(id: Long, order: Int) =
    Task(id = id, text = "task $id", order = order, crossedOff = true)

// ── applySnooze ───────────────────────────────────────────────────────────────

class ApplySnoozeTest {

    @Test
    fun `NullSnoozeSession creates fresh session with offset 1`() {
        val result = applySnooze(NullSnoozeSession, NOW)
        assertEquals(1, result.offset)
        assertEquals(NOW + SNOOZE_DURATION_MS, result.expiry)
    }

    @Test
    fun `active session increments offset and resets expiry`() {
        val active = SnoozeSession(expiry = FUTURE, offset = 2)
        val result = applySnooze(active, NOW)
        assertEquals(3, result.offset)
        assertEquals(NOW + SNOOZE_DURATION_MS, result.expiry)
    }

    @Test
    fun `expired session creates fresh session with offset 1`() {
        val expired = SnoozeSession(expiry = PAST, offset = 3)
        val result = applySnooze(expired, NOW)
        assertEquals(1, result.offset)
        assertEquals(NOW + SNOOZE_DURATION_MS, result.expiry)
    }
}

// ── applyMarkComplete ─────────────────────────────────────────────────────────

class ApplyMarkCompleteTest {

    @Test
    fun `NullSnoozeSession is unchanged`() {
        val result = applyMarkComplete(NullSnoozeSession, NOW)
        assertEquals(NullSnoozeSession, result)
    }

    @Test
    fun `active session is unchanged`() {
        val active = SnoozeSession(expiry = FUTURE, offset = 1)
        val result = applyMarkComplete(active, NOW)
        assertEquals(active, result)
    }

    @Test
    fun `expired session is cleared`() {
        val expired = SnoozeSession(expiry = PAST, offset = 2)
        val result = applyMarkComplete(expired, NOW)
        assertEquals(NullSnoozeSession, result)
    }
}

// ── computeCurrentTop ─────────────────────────────────────────────────────────

class ComputeCurrentTopTest {

    @Test
    fun `empty task list returns Empty`() {
        val result = computeCurrentTop(emptyList(), NullSnoozeSession, NOW)
        assertInstanceOf(CurrentTop.Empty::class.java, result)
    }

    @Test
    fun `all tasks crossed off returns Empty`() {
        val tasks = listOf(crossedTask(1, 0), crossedTask(2, 1000))
        val result = computeCurrentTop(tasks, NullSnoozeSession, NOW)
        assertInstanceOf(CurrentTop.Empty::class.java, result)
    }

    @Test
    fun `NullSnoozeSession returns natural first active task`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        val result = computeCurrentTop(tasks, NullSnoozeSession, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(1L, (result as CurrentTop.Real).task.id)
    }

    @Test
    fun `session offset 1 skips first task`() {
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000))
        val session = SnoozeSession(expiry = FUTURE, offset = 1)
        val result = computeCurrentTop(tasks, session, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(2L, (result as CurrentTop.Real).task.id)
    }

    @Test
    fun `session offset 2 skips first two tasks`() {
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000))
        val session = SnoozeSession(expiry = FUTURE, offset = 2)
        val result = computeCurrentTop(tasks, session, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(3L, (result as CurrentTop.Real).task.id)
    }

    @Test
    fun `offset equal to list size returns SnoozedFallback of first task`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        val session = SnoozeSession(expiry = FUTURE, offset = 2)
        val result = computeCurrentTop(tasks, session, NOW)
        assertInstanceOf(CurrentTop.SnoozedFallback::class.java, result)
        assertEquals(1L, (result as CurrentTop.SnoozedFallback).task.id)
    }

    @Test
    fun `offset exceeding list size returns SnoozedFallback of first task`() {
        val tasks = listOf(task(1, 0))
        val session = SnoozeSession(expiry = FUTURE, offset = 5)
        val result = computeCurrentTop(tasks, session, NOW)
        assertInstanceOf(CurrentTop.SnoozedFallback::class.java, result)
        assertEquals(1L, (result as CurrentTop.SnoozedFallback).task.id)
    }

    @Test
    fun `expired session still applies offset to current top`() {
        // Expiry does NOT affect computeCurrentTop — offset always applied
        val tasks = listOf(task(1, 0), task(2, 1000))
        val expired = SnoozeSession(expiry = PAST, offset = 1)
        val result = computeCurrentTop(tasks, expired, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(2L, (result as CurrentTop.Real).task.id)
    }

    @Test
    fun `crossed tasks are excluded from candidates`() {
        val tasks = listOf(crossedTask(1, 0), task(2, 1000), task(3, 2000))
        val result = computeCurrentTop(tasks, NullSnoozeSession, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(2L, (result as CurrentTop.Real).task.id)
    }

    @Test
    fun `candidates are sorted by order regardless of list insertion order`() {
        // task 3 has lower order value (should be first)
        val tasks = listOf(task(1, 2000), task(2, 1000), task(3, 0))
        val result = computeCurrentTop(tasks, NullSnoozeSession, NOW)
        assertInstanceOf(CurrentTop.Real::class.java, result)
        assertEquals(3L, (result as CurrentTop.Real).task.id)
    }
}
