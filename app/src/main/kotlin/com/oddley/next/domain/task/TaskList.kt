package com.oddley.next.domain.task

/**
 * Pure operations on a list of Tasks.
 *
 * All functions are side-effect-free and take the full task list as input,
 * returning a new list. The Repository is responsible for persisting changes.
 *
 * Order management: [order] values use gaps of 1000 between items so that
 * reordering can assign intermediate values without renumbering the whole list.
 * Crossed-off tasks keep their original order value so their relative positions
 * to each other are preserved in the crossed section.
 */

// ── Views ────────────────────────────────────────────────────────────────────

/** Uncrossed tasks sorted by [Task.order] ascending. */
fun activeTasks(tasks: List<Task>): List<Task> =
    tasks.filter { !it.crossedOff }.sortedBy { it.order }

/** Crossed-off tasks sorted by [Task.order] ascending (preserves relative order). */
fun crossedOffTasks(tasks: List<Task>): List<Task> =
    tasks.filter { it.crossedOff }.sortedBy { it.order }

// ── Insertion ─────────────────────────────────────────────────────────────────

/**
 * Returns the [Task.order] value to use when inserting a new task at the
 * bottom of the active section.
 *
 * 0 for an empty list; otherwise max(all orders) + 1000.
 */
fun nextOrderForInsert(tasks: List<Task>): Int =
    if (tasks.isEmpty()) 0 else tasks.maxOf { it.order } + 1000

// ── Mutations ────────────────────────────────────────────────────────────────

/**
 * Marks [id] as crossed off. Order value is preserved so the task's position
 * in the crossed section reflects its original position in the list.
 *
 * No-op if [id] is not found.
 */
fun crossOff(tasks: List<Task>, id: Long): List<Task> =
    tasks.map { if (it.id == id) it.copy(crossedOff = true) else it }

/**
 * Restores [id] to the active section by clearing its crossedOff flag and
 * assigning it a new order that places it at the bottom of the active list.
 *
 * No-op if [id] is not found.
 */
fun restore(tasks: List<Task>, id: Long): List<Task> {
    if (tasks.none { it.id == id }) return tasks
    val newOrder = nextOrderForInsert(tasks)
    return tasks.map { if (it.id == id) it.copy(crossedOff = false, order = newOrder) else it }
}

/**
 * Updates the text of [id]. No-op if [id] is not found.
 */
fun editText(tasks: List<Task>, id: Long, newText: String): List<Task> =
    tasks.map { if (it.id == id) it.copy(text = newText) else it }

/**
 * Moves the item at [fromIndex] to [toIndex] within the active task list,
 * then renumbers active task orders to reflect the new sequence.
 *
 * Indices are into [activeTasks], not the full list. Crossed-off tasks are
 * unaffected (their relative order to each other is preserved).
 *
 * No-op if [fromIndex] == [toIndex].
 */
fun reorder(tasks: List<Task>, fromIndex: Int, toIndex: Int): List<Task> {
    if (fromIndex == toIndex) return tasks

    // Work on the active sub-list; leave crossed tasks alone.
    val active = activeTasks(tasks).toMutableList()
    val crossed = crossedOffTasks(tasks)

    val item = active.removeAt(fromIndex)
    active.add(toIndex, item)

    // Renumber active tasks with gaps so future reorders have room to insert.
    val renumberedActive = active.mapIndexed { i, task -> task.copy(order = i * 1000) }

    // Preserve crossed tasks' relative order by keeping their existing order
    // values (they sort among themselves by those values). Shift them above
    // the max active order so insertAtBottom never collides.
    val crossedOffset = renumberedActive.size * 1000
    val renumberedCrossed = crossed.mapIndexed { i, task ->
        task.copy(order = crossedOffset + i * 1000)
    }

    return renumberedActive + renumberedCrossed
}

/**
 * Removes all crossed-off tasks from the list.
 */
fun bulkDeleteCrossedOff(tasks: List<Task>): List<Task> =
    tasks.filter { !it.crossedOff }
