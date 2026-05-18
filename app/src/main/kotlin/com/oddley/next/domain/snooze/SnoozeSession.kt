package com.oddley.next.domain.snooze

/**
 * Represents the user's active snooze state.
 *
 * [expiry]  — epoch millis after which the session is "expired" (but still applied to
 *             current-top computation — expiry only gates applySnooze / applyMarkComplete).
 * [offset]  — number of top items to skip when computing the current top. Always ≥ 1
 *             when a real session exists.
 *
 * Absence of a session is represented by [NullSnoozeSession], which has offset = 0
 * (no skip) and expiry = Long.MIN_VALUE (always expired).
 */
data class SnoozeSession(val expiry: Long, val offset: Int)

/**
 * Null Object for "no snooze session is active."
 *
 * offset = 0  →  computeCurrentTop returns the natural top of the list.
 * expiry = Long.MIN_VALUE  →  always considered expired by applySnooze / applyMarkComplete.
 */
val NullSnoozeSession = SnoozeSession(expiry = Long.MIN_VALUE, offset = 0)
