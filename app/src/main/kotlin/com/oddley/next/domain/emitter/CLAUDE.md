# domain/emitter — Contract

**Purpose:** Pure Kotlin types and operations for Task Emitters. No Android imports, no Room, no Compose. Compiles on a plain JVM.

## Types

| Type | Role |
|---|---|
| `TaskEmitter` | Scheduled/recurring task template: id, label, rrule, dtStart, nextEmission |
| `NullTaskEmitter` | Null Object sentinel for "no emitter" |

## Exported Functions

All functions are top-level, pure, and side-effect-free.

| Function | Inputs | Returns | Notes |
|---|---|---|---|
| `computeNextEmission` | `rrule, dtStart, after, tz?` | `Long?` | First RRULE occurrence strictly after `after`; null = exhausted or malformed |
| `shouldEmit` | `emitter, now` | `Boolean` | True when `nextEmission` ≤ `now` (non-null) |
| `advanceEmitter` | `emitter, now, tz?` | `TaskEmitter` | Copy with `nextEmission` advanced past `now`; null if exhausted |

## Key invariants

- `dtStart` is immutable — it always anchors the original first occurrence so COUNT/UNTIL work correctly across multiple firings.
- `nextEmission = null` means the rule is exhausted (COUNT/UNTIL reached). A null emitter never fires.
- `computeNextEmission` is O(1) with `fastForward`; safety limit of 100 000 iterations prevents runaway.
- One-time emitters use `"FREQ=DAILY;COUNT=1"`. After firing, `advanceEmitter` returns `nextEmission = null`.

## Dependencies

- `org.dmfs:lib-recur` (RFC 5545 RRULE parsing + iteration)
- `java.util.TimeZone` (JVM standard library)

## Anti-patterns

- Never import `android.*`, `androidx.*`, or Room in this package
- Never make functions suspend — domain is synchronous
- Never accept or return nullable TaskEmitter — use NullTaskEmitter
