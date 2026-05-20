package com.oddley.next.domain.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [computeNext] — the function that determines the "NEXT" task shown in the
 * notification and the NEXT section header.
 *
 * A task is snoozed when snoozedUntil is non-null AND greater than [now].
 * Timestamps at or below [now] are treated as expired (not snoozed).
 */
private const val NOW = 1_000_000L

private fun task(id: Long, order: Int, snoozedUntil: Long? = null) =
    Task(id = id, text = "task $id", order = order, crossedOff = false,
        snoozedUntil = snoozedUntil)

private fun crossed(id: Long, order: Int) =
    Task(id = id, text = "task $id", order = order, crossedOff = true)

class ComputeNextTest {

    @Test
    fun `empty list returns NullTask`() {
        assertEquals(NullTask, computeNext(emptyList(), NOW))
    }

    @Test
    fun `all crossed off returns NullTask`() {
        val tasks = listOf(crossed(1, 0), crossed(2, 1000))
        assertEquals(NullTask, computeNext(tasks, NOW))
    }

    @Test
    fun `no snooze returns first active task by order`() {
        // Insertion order differs from order value — lowest order wins
        val tasks = listOf(task(1, 2000), task(2, 0), task(3, 1000))
        assertEquals(2L, computeNext(tasks, NOW).id)
    }

    @Test
    fun `snoozed task with future expiry is skipped`() {
        val tasks = listOf(
            task(1, 0, snoozedUntil = NOW + 1_000), // snoozed
            task(2, 1000),                           // not snoozed
        )
        assertEquals(2L, computeNext(tasks, NOW).id)
    }

    @Test
    fun `all tasks snoozed returns NullTask`() {
        val tasks = listOf(
            task(1, 0, snoozedUntil = NOW + 1_000),
            task(2, 1000, snoozedUntil = NOW + 2_000),
        )
        assertEquals(NullTask, computeNext(tasks, NOW))
    }

    @Test
    fun `expired snooze timestamp is treated as not snoozed`() {
        val tasks = listOf(
            task(1, 0, snoozedUntil = NOW - 1), // expired
            task(2, 1000),
        )
        // task 1 has lower order and its snooze is expired → it becomes NEXT
        assertEquals(1L, computeNext(tasks, NOW).id)
    }

    @Test
    fun `snooze exactly at now is treated as expired (boundary)`() {
        // snoozedUntil <= now → expired
        val tasks = listOf(
            task(1, 0, snoozedUntil = NOW),
            task(2, 1000),
        )
        assertEquals(1L, computeNext(tasks, NOW).id)
    }

    @Test
    fun `crossed tasks are excluded even when no snooze`() {
        val tasks = listOf(crossed(1, 0), task(2, 1000))
        assertEquals(2L, computeNext(tasks, NOW).id)
    }

    @Test
    fun `first non-snoozed by order wins when mix of snoozed and not`() {
        val tasks = listOf(
            task(1, 0, snoozedUntil = NOW + 999),  // snoozed
            task(2, 1000, snoozedUntil = NOW + 999), // snoozed
            task(3, 2000),                            // not snoozed → NEXT
        )
        assertEquals(3L, computeNext(tasks, NOW).id)
    }
}
