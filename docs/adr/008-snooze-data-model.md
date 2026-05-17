# ADR-008: Snooze Data Model

**Status:** Accepted

## Context

The snooze mechanic is the defining domain feature of Next. It deserves precise specification because the behavior is subtle in ways that affect every state transition.

## Decision

### Entity

```kotlin
data class SnoozeSession(
    val expiry: Long,    // epoch millis
    val offset: Int      // ≥ 1 when present
)

object NullSnoozeSession : ... // see ADR-004
```

### Invariants

- **Zero or one ever.** Persistence layer enforces this via a singleton table (single row, fixed primary key).
- **`offset` is always ≥ 1 when the session exists.** A session with offset 0 is meaningless — clear it instead.
- **Both fields are stored together.** They mutate as a pair; never half-update.

### Operations

All operations are pure functions in `domain/snooze/`, tested per ADR-003:

```kotlin
// Apply snooze, given current session and current time.
// - No session OR expired session → new session, offset=1, expiry=now+5min
// - Active session → reset expiry, offset += 1
fun applySnooze(current: SnoozeSession, now: Long): SnoozeSession

// Apply mark-complete to the session, given current session and current time.
// - No session → no change
// - Active session → no change (user is "burning down" through snoozed items)
// - Expired session → clear (return NullSnoozeSession)
fun applyMarkComplete(current: SnoozeSession, now: Long): SnoozeSession

// Compute the current top of stack, given tasks + session + now.
// See `computeCurrentTop` below.
fun computeCurrentTop(
    tasks: List<Task>,
    session: SnoozeSession,
    now: Long,
): CurrentTop

sealed class CurrentTop {
    data class Real(val task: Task) : CurrentTop()           // natural top after offset
    data class SnoozedFallback(val task: Task) : CurrentTop()  // offset exceeded list; first uncrossed shown
    object Empty : CurrentTop()                              // no uncrossed tasks
}
```

### Current-top computation

```
1. candidates = tasks.filter { !it.crossedOff }.sortedBy { it.order }
2. if candidates is empty → Empty
3. effectiveOffset = session.offset      // applied regardless of expiry
4. if effectiveOffset < candidates.size → Real(candidates[effectiveOffset])
5. else → SnoozedFallback(candidates[0])
```

### Expiry semantics

The expiry IS NOT used to compute current top. It is only used to decide:

- **applyMarkComplete:** if session is expired at this moment, clear it after the cross-off
- **applySnooze:** if session is expired at this moment, treat as a fresh new snooze (offset=1, new expiry) instead of incrementing

The 5-minute timer is therefore a "decay window" — within 5 minutes of the last snooze, additional snoozes pile on (offset increments). After 5 minutes, the offset is still applied to current-top computation, but the next user interaction triggers a reset.

## Rationale

- **Offset survives expiry** because foster mama's new top item should stay prominent until she engages with it. Re-shuffling back to the original top item after 5 minutes would defeat the purpose.
- **Mark complete on expired session clears** because she's interacted with the new top — done with this snooze cycle.
- **Snooze on expired session resets** because she clearly wants to defer further; cumulative offset from a stale session would be surprising.

## Consequences

- The data model is tiny (two fields), which makes the state machine fully testable with a small fixture set
- The "active vs expired" distinction lives in `applyMarkComplete` / `applySnooze` but NOT in `computeCurrentTop` — this asymmetry is intentional and worth a comment in the code

## Test coverage targets

Every state transition exhaustively:

| Initial | Action | Expected |
|---|---|---|
| NullSnoozeSession | applySnooze | new session, offset=1 |
| Active session | applySnooze | same session, offset += 1, expiry reset |
| Expired session | applySnooze | new session, offset=1, expiry reset |
| NullSnoozeSession | applyMarkComplete | unchanged (NullSnoozeSession) |
| Active session | applyMarkComplete | unchanged (offset preserved) |
| Expired session | applyMarkComplete | NullSnoozeSession (cleared) |
| Empty task list | computeCurrentTop | Empty |
| Non-empty list, session offset < size | computeCurrentTop | Real(...) |
| Non-empty list, session offset ≥ size | computeCurrentTop | SnoozedFallback(first) |
| NullSnoozeSession (offset=0) | computeCurrentTop | Real(first) |

## Storage

Singleton-row Room table:

```kotlin
@Entity(tableName = "snooze_session")
data class SnoozeSessionEntity(
    @PrimaryKey val id: Int = 1,   // always 1; enforces single-row
    val expiry: Long,
    val offset: Int,
)
```

Repository converts to/from domain `SnoozeSession`; empty row → `NullSnoozeSession`.
