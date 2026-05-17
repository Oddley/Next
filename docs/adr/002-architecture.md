# ADR-002: Architecture — Functional Core / Imperative Shell on Android

**Status:** Accepted

## Context

We want testable, understandable code with a clear separation between pure logic and side effects. We've used Functional Core / Imperative Shell (FC/IS) successfully in [Bean Counter](https://github.com/Oddley/BeanCounter) and want to carry the discipline here, adapted to Android idioms.

## Decision

Four layers, top-down:

```
ui/             ← Compose screens + ViewModels (Android-bound)
notification/   ← Foreground service + notification builders (Android-bound)
data/           ← Room DAOs + Drive REST wrappers (Android-bound)
domain/         ← Pure Kotlin, JVM-tested (no Android imports)
```

Plus a thin `app/` layer at the top with the Application class and manual DI wiring.

### Domain layer (functional core)

- Pure Kotlin only — no `android.*`, no `androidx.*`, no Room annotations, no Compose
- All exports unit-tested (JUnit 5, JVM) per ADR-003
- Operations on tasks, snooze sessions, and computed views (current top, partitions into to-do/crossed-off) live here
- Uses sealed classes and Null Objects for absence (ADR-004); no nullable returns
- One folder per bounded concept (`domain/task/`, `domain/snooze/`, `domain/sync/`)

### Data layer (imperative shell)

- Room entities, DAOs, the database class, migration plan
- Drive REST API wrapper (direct HTTP, no SDK)
- Repository classes that translate between Room/Drive shapes and domain types
- Not unit-tested per ADR-003; verified via on-device testing

### UI layer (imperative shell)

- Compose composables per screen
- ViewModels expose `StateFlow<DomainType>` consumed by composables via `collectAsStateWithLifecycle`
- ViewModels are linear sequences of "fetch from repo → call domain function → write back to repo" — no branching domain logic in ViewModel bodies (lift to `domain/`)
- Not unit-tested per ADR-003

### Notification layer (imperative shell)

- `TopTaskService` (foreground service) owns the notification lifecycle
- `NotificationBuilder` composes the notification from current domain state
- `ActionReceiver` (BroadcastReceiver) handles Mark complete + Snooze taps from the notification, dispatching back to the data layer
- Boot receiver restarts the service after device reboot

## Dependency rules

- `domain/` may import only standard Kotlin / pure libraries
- `data/`, `ui/`, `notification/` may import from `domain/`
- `data/`, `ui/`, `notification/` MAY import each other only at the `app/` composition root (otherwise we get cycles)
- **Domain never depends on Android.** This is enforceable by putting `domain/` in a separate Gradle module if we want a hard guarantee; for v1, the discipline is convention + code review.

## Consequences

- Domain code can be run in a JVM test runner with no emulator overhead — fast feedback.
- Adding Drive sync, swapping persistence, or adding a second UI surface (tablet?) all become localized changes — the domain doesn't care.
- ViewModels are thin glue, which means they have less to test, which is fine because we don't test them anyway.

## Anti-patterns to avoid

- Branching domain logic inside a ViewModel (e.g., "if task.crossedOff and snoozeSession.expired then…") — lift it to `domain/`
- Compose code calling Room DAOs directly — must go through a ViewModel → repository → DAO
- Domain code importing `android.os.SystemClock` or similar for "just one little thing" — pass `now: Long` as a parameter instead (testability)
