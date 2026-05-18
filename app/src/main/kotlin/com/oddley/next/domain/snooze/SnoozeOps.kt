package com.oddley.next.domain.snooze

import com.oddley.next.domain.task.Task

// ── Output type ───────────────────────────────────────────────────────────────

/**
 * The result of [computeCurrentTop].
 *
 * [Real]             — the task at [SnoozeSession.offset] positions into the active list.
 * [SnoozedFallback]  — offset exceeded the active list; the natural first task is shown
 *                      but marked as a snoozed fallback so the UI can signal this state.
 * [Empty]            — no uncrossed tasks remain.
 */
sealed class CurrentTop {
    data class Real(val task: Task) : CurrentTop()
    data class SnoozedFallback(val task: Task) : CurrentTop()
    object Empty : CurrentTop()
}

// ── Constants ─────────────────────────────────────────────────────────────────

internal const val SNOOZE_DURATION_MS = 5L * 60 * 1_000   // 5 minutes

// ── Operations ────────────────────────────────────────────────────────────────

/**
 * Applies a Snooze action given the [current] session state and [now] (epoch millis).
 *
 * - No session (NullSnoozeSession) OR expired session → fresh session, offset = 1
 * - Active session (not yet expired)                  → offset += 1, expiry reset
 */
fun applySnooze(current: SnoozeSession, now: Long): SnoozeSession =
    if (current == NullSnoozeSession || now >= current.expiry) {
        SnoozeSession(expiry = now + SNOOZE_DURATION_MS, offset = 1)
    } else {
        SnoozeSession(expiry = now + SNOOZE_DURATION_MS, offset = current.offset + 1)
    }

/**
 * Applies a Mark Complete action to the [current] session given [now] (epoch millis).
 *
 * - No session     → no change (NullSnoozeSession returned)
 * - Active session → no change (user is burning down through snoozed items)
 * - Expired session → clear (return NullSnoozeSession)
 *
 * Note: the caller is separately responsible for crossing off the current top task.
 */
fun applyMarkComplete(current: SnoozeSession, now: Long): SnoozeSession =
    if (current != NullSnoozeSession && now >= current.expiry) {
        NullSnoozeSession          // expired session → cleared after mark complete
    } else {
        current                    // no session or active session → unchanged
    }

/**
 * Computes the task that should be displayed as "current top."
 *
 * Expiry does NOT affect this computation — the offset is applied regardless of
 * whether the session has expired. Expiry only affects [applySnooze] and
 * [applyMarkComplete].
 *
 * Algorithm:
 *   1. candidates = uncrossed tasks sorted by order ascending
 *   2. if empty → [CurrentTop.Empty]
 *   3. effectiveOffset = session.offset  (0 for NullSnoozeSession → natural first)
 *   4. if offset < candidates.size → [CurrentTop.Real]
 *   5. else → [CurrentTop.SnoozedFallback] (wrap around to first candidate)
 *
 * @param now Provided for API symmetry but not used in computation.
 */
fun computeCurrentTop(
    tasks: List<Task>,
    session: SnoozeSession,
    now: Long,
): CurrentTop {
    val candidates = tasks.filter { !it.crossedOff }.sortedBy { it.order }
    if (candidates.isEmpty()) return CurrentTop.Empty
    val offset = session.offset      // NullSnoozeSession.offset == 0
    return if (offset < candidates.size) {
        CurrentTop.Real(candidates[offset])
    } else {
        CurrentTop.SnoozedFallback(candidates[0])
    }
}
