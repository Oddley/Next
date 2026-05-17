# ADR-004: Nothing is Something (Null Object Pattern)

**Status:** Accepted

## Context

Kotlin has `null` as a first-class concept, but absence usually means more than "no value here." It usually means a specific, meaningful state — "no snooze session is active," "no task is currently top," "no Drive connection." Pretending those are the same shape as "a value that's missing" loses information.

## Decision

**Domain code never returns nullable types for absence.** Use one of:

### Null Object — for "absence is its own valid state"

```kotlin
object NullSnoozeSession : SnoozeSession {
    override val expiry = Long.MIN_VALUE
    override val offset = 0
}

fun loadSnoozeSession(): SnoozeSession = repo.get() ?: NullSnoozeSession
```

Callers can use `if (session.offset > 0)` instead of `if (session != null)` — and the math just works (NullSnoozeSession's offset of 0 means "no offset applied").

### Sealed class — for "absence has structured variants"

```kotlin
sealed class CurrentTop {
    data class Real(val task: Task) : CurrentTop()
    data class SnoozedFallback(val task: Task) : CurrentTop()   // offset exceeded list; surfacing top snoozed
    object Empty : CurrentTop()
}
```

Each call site `when`s exhaustively; the compiler enforces all branches are handled.

### Result-style — for "operation might fail with a reason"

```kotlin
sealed class SyncResult {
    object Success : SyncResult()
    data class Failure(val reason: String) : SyncResult()
}
```

## When nullable IS acceptable

- **Bridge points to Android code** that returns nullable types (e.g., `findViewById`, `getStringExtra`) — wrap immediately in domain types
- **DAO query results in `data/`** — Room returns nullables; the repository converts them to Null Object / sealed type at the layer boundary
- **Local computation** within a function body when null is the natural shape and immediately handled

## Rationale

- Null Objects make domain code read declaratively: `task.crossOff()` works whether the task is real or null
- Sealed classes give the compiler the info to ensure every state is handled
- The pattern composes with TDD (ADR-003): tests can pass `NullSnoozeSession` as input naturally

## Consequences

- Slightly more domain types (one Null Object per "thing that might be absent")
- Conversion logic at the data/domain boundary (repository methods)
- Compiler-enforced exhaustiveness in `when` expressions catches whole categories of bugs

## Anti-patterns

- Returning `Task?` from a domain function then `!!`-ing it at the call site — defeats the whole point
- Using `NullSnoozeSession.offset == 0` as a sentinel check instead of `is NullSnoozeSession` — fragile if real sessions ever legitimately have offset 0 (in our spec they don't, but the principle stands: prefer type checks over value sentinels)
