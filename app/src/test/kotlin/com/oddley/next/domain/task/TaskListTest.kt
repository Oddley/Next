package com.oddley.next.domain.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskListTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun task(id: Long, order: Int, crossed: Boolean = false, text: String = "Task $id") =
        Task(id = id, text = text, order = order, crossedOff = crossed)

    // ── activeTasks ───────────────────────────────────────────────────────────

    @Test
    fun `activeTasks returns only uncrossed tasks`() {
        val tasks = listOf(task(1, 0), task(2, 1000, crossed = true), task(3, 2000))
        val result = activeTasks(tasks)
        assertEquals(listOf(task(1, 0), task(3, 2000)), result)
    }

    @Test
    fun `activeTasks returns tasks sorted by order ascending`() {
        val tasks = listOf(task(1, 2000), task(2, 0), task(3, 1000))
        val result = activeTasks(tasks)
        assertEquals(listOf(task(2, 0), task(3, 1000), task(1, 2000)), result)
    }

    @Test
    fun `activeTasks returns empty list when all tasks are crossed off`() {
        val tasks = listOf(task(1, 0, crossed = true), task(2, 1000, crossed = true))
        assertTrue(activeTasks(tasks).isEmpty())
    }

    @Test
    fun `activeTasks returns empty list when task list is empty`() {
        assertTrue(activeTasks(emptyList()).isEmpty())
    }

    // ── crossedOffTasks ───────────────────────────────────────────────────────

    @Test
    fun `crossedOffTasks returns only crossed tasks`() {
        val tasks = listOf(task(1, 0), task(2, 1000, crossed = true), task(3, 2000))
        val result = crossedOffTasks(tasks)
        assertEquals(listOf(task(2, 1000, crossed = true)), result)
    }

    @Test
    fun `crossedOffTasks returns tasks sorted by order ascending`() {
        val tasks = listOf(
            task(1, 2000, crossed = true),
            task(2, 0, crossed = true),
            task(3, 1000, crossed = true)
        )
        val result = crossedOffTasks(tasks)
        assertEquals(
            listOf(task(2, 0, crossed = true), task(3, 1000, crossed = true), task(1, 2000, crossed = true)),
            result
        )
    }

    @Test
    fun `crossedOffTasks returns empty list when no tasks are crossed off`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        assertTrue(crossedOffTasks(tasks).isEmpty())
    }

    // ── nextOrderForInsert ────────────────────────────────────────────────────

    @Test
    fun `nextOrderForInsert returns 0 for empty list`() {
        assertEquals(0, nextOrderForInsert(emptyList()))
    }

    @Test
    fun `nextOrderForInsert returns max order plus 1000`() {
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000))
        assertEquals(3000, nextOrderForInsert(tasks))
    }

    @Test
    fun `nextOrderForInsert considers crossed tasks too`() {
        // Crossed tasks can have high order values; new insert must clear all of them
        val tasks = listOf(task(1, 0), task(2, 5000, crossed = true))
        assertEquals(6000, nextOrderForInsert(tasks))
    }

    // ── crossOff ─────────────────────────────────────────────────────────────

    @Test
    fun `crossOff marks the target task as crossed off`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        val result = crossOff(tasks, id = 1)
        assertTrue(result.first { it.id == 1L }.crossedOff)
    }

    @Test
    fun `crossOff does not affect other tasks`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        val result = crossOff(tasks, id = 1)
        assertFalse(result.first { it.id == 2L }.crossedOff)
    }

    @Test
    fun `crossOff preserves the order value of the crossed task`() {
        val tasks = listOf(task(1, 1000), task(2, 2000))
        val result = crossOff(tasks, id = 1)
        assertEquals(1000, result.first { it.id == 1L }.order)
    }

    @Test
    fun `crossOff is a no-op for unknown id`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        assertEquals(tasks, crossOff(tasks, id = 99))
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    fun `restore marks the target task as not crossed off`() {
        val tasks = listOf(task(1, 0), task(2, 1000, crossed = true))
        val result = restore(tasks, id = 2)
        assertFalse(result.first { it.id == 2L }.crossedOff)
    }

    @Test
    fun `restore places task at bottom of active section`() {
        // Active tasks have orders 0, 1000. Restored task order > max(all orders).
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000, crossed = true))
        val result = restore(tasks, id = 3)
        val activeResult = activeTasks(result)
        assertEquals(3L, activeResult.last().id)
    }

    @Test
    fun `restore is a no-op for unknown id`() {
        val tasks = listOf(task(1, 0), task(2, 1000, crossed = true))
        assertEquals(tasks, restore(tasks, id = 99))
    }

    // ── editText ──────────────────────────────────────────────────────────────

    @Test
    fun `editText updates the text of the target task`() {
        val tasks = listOf(task(1, 0, text = "Old text"), task(2, 1000))
        val result = editText(tasks, id = 1, newText = "New text")
        assertEquals("New text", result.first { it.id == 1L }.text)
    }

    @Test
    fun `editText does not affect other tasks`() {
        val tasks = listOf(task(1, 0, text = "A"), task(2, 1000, text = "B"))
        val result = editText(tasks, id = 1, newText = "A updated")
        assertEquals("B", result.first { it.id == 2L }.text)
    }

    @Test
    fun `editText is a no-op for unknown id`() {
        val tasks = listOf(task(1, 0, text = "A"), task(2, 1000, text = "B"))
        assertEquals(tasks, editText(tasks, id = 99, newText = "X"))
    }

    // ── reorder ───────────────────────────────────────────────────────────────

    @Test
    fun `reorder moves a task within the active list`() {
        // Active: A(0), B(1000), C(2000). Move index 2 → index 0.
        val tasks = listOf(task(1, 0, text = "A"), task(2, 1000, text = "B"), task(3, 2000, text = "C"))
        val result = reorder(tasks, fromIndex = 2, toIndex = 0)
        val activeResult = activeTasks(result)
        assertEquals(listOf(3L, 1L, 2L), activeResult.map { it.id })
    }

    @Test
    fun `reorder move down shifts intermediate tasks up`() {
        // Active: A(0), B(1000), C(2000), D(3000). Move index 0 → index 2.
        val tasks = listOf(
            task(1, 0, text = "A"), task(2, 1000, text = "B"),
            task(3, 2000, text = "C"), task(4, 3000, text = "D")
        )
        val result = reorder(tasks, fromIndex = 0, toIndex = 2)
        val activeResult = activeTasks(result)
        assertEquals(listOf(2L, 3L, 1L, 4L), activeResult.map { it.id })
    }

    @Test
    fun `reorder does not affect crossed-off tasks`() {
        val tasks = listOf(
            task(1, 0), task(2, 1000), task(3, 2000, crossed = true)
        )
        val result = reorder(tasks, fromIndex = 0, toIndex = 1)
        assertTrue(result.first { it.id == 3L }.crossedOff)
        assertEquals(2, activeTasks(result).size)
    }

    @Test
    fun `reorder preserves relative order of crossed-off tasks`() {
        val tasks = listOf(
            task(1, 0, text = "A"),
            task(2, 1000, crossed = true, text = "B-crossed"),
            task(3, 2000, text = "C"),
            task(4, 3000, crossed = true, text = "D-crossed"),
        )
        val result = reorder(tasks, fromIndex = 0, toIndex = 1)
        val crossedResult = crossedOffTasks(result)
        assertEquals(listOf(2L, 4L), crossedResult.map { it.id })
    }

    @Test
    fun `reorder no-op when fromIndex equals toIndex`() {
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000))
        val result = reorder(tasks, fromIndex = 1, toIndex = 1)
        assertEquals(listOf(1L, 2L, 3L), activeTasks(result).map { it.id })
    }

    // ── bulkDeleteCrossedOff ──────────────────────────────────────────────────

    @Test
    fun `bulkDeleteCrossedOff removes all crossed tasks`() {
        val tasks = listOf(task(1, 0), task(2, 1000, crossed = true), task(3, 2000, crossed = true))
        val result = bulkDeleteCrossedOff(tasks)
        assertTrue(result.none { it.crossedOff })
        assertEquals(1, result.size)
    }

    @Test
    fun `bulkDeleteCrossedOff leaves active tasks untouched`() {
        val tasks = listOf(task(1, 0), task(2, 1000), task(3, 2000, crossed = true))
        val result = bulkDeleteCrossedOff(tasks)
        assertEquals(listOf(task(1, 0), task(2, 1000)), result)
    }

    @Test
    fun `bulkDeleteCrossedOff is a no-op when no tasks are crossed off`() {
        val tasks = listOf(task(1, 0), task(2, 1000))
        assertEquals(tasks, bulkDeleteCrossedOff(tasks))
    }

    @Test
    fun `bulkDeleteCrossedOff on empty list returns empty list`() {
        assertTrue(bulkDeleteCrossedOff(emptyList()).isEmpty())
    }
}
