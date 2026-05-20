package com.oddley.next.domain.emitter

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.util.TimeZone

/**
 * Pure operations for [TaskEmitter]. No Android imports — compiles on a plain JVM.
 *
 * Recurrence is computed via RFC 5545 RRULE (lib-recur). [dtStart] is always the
 * original first occurrence anchor; it is never advanced as the emitter fires. This
 * ensures COUNT/UNTIL math works correctly across multiple firings.
 */

// ── Core computation ──────────────────────────────────────────────────────────

/**
 * Finds the first RRULE occurrence that is strictly after [after] (exclusive).
 *
 * Iterates from [dtStart] in [tz], fast-forwards past [after], then returns the
 * first result > [after]. Returns null when the rule is exhausted (COUNT/UNTIL
 * reached) or when [rrule] is malformed.
 *
 * A safety limit of 100 000 iterations guards against pathological rules.
 */
fun computeNextEmission(
    rrule: String,
    dtStart: Long,
    after: Long,
    tz: TimeZone = TimeZone.getDefault(),
): Long? = try {
    val rule = RecurrenceRule(rrule)
    val it = rule.iterator(DateTime(tz, dtStart))
    // Fast-forward to just before the target window for efficiency.
    it.fastForward(DateTime(tz, after))
    var safetyCount = 0
    var result: Long? = null
    while (it.hasNext() && safetyCount < 100_000) {
        safetyCount++
        val next = it.nextMillis()
        if (next > after) {
            result = next
            break
        }
    }
    result
} catch (_: Exception) {
    null
}

// ── Predicates ────────────────────────────────────────────────────────────────

/**
 * Returns true when [emitter] is due to fire at [now].
 *
 * An emitter fires when [TaskEmitter.nextEmission] is non-null and ≤ [now].
 */
fun shouldEmit(emitter: TaskEmitter, now: Long): Boolean =
    emitter.nextEmission != null && emitter.nextEmission <= now

// ── Mutations ─────────────────────────────────────────────────────────────────

/**
 * Returns a copy of [emitter] with [TaskEmitter.nextEmission] advanced to the
 * next future occurrence after [now], or null if the rule is exhausted.
 *
 * [dtStart] is preserved — it always anchors the original first occurrence.
 */
fun advanceEmitter(
    emitter: TaskEmitter,
    now: Long,
    tz: TimeZone = TimeZone.getDefault(),
): TaskEmitter = emitter.copy(
    nextEmission = computeNextEmission(emitter.rrule, emitter.dtStart, now, tz),
)
