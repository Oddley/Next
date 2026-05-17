# ADR-001: Tech Stack

**Status:** Accepted

## Context

Native Android app for foster mama. Single user, single device at a time. Persistent notification is the primary user surface. Local storage is the source of truth; Drive is a manual backup channel.

## Decision

| Concern | Choice |
|---|---|
| Language | Kotlin (latest stable) |
| UI | Jetpack Compose + Material 3 |
| Persistence | Room (SQLite wrapper) |
| Async | Kotlin coroutines + Flow |
| Notifications | Foreground Service + NotificationCompat |
| Drive integration | Google Sign-In + Drive REST API v3 (no SDK; direct HTTP) |
| Build system | Gradle Kotlin DSL |
| Min SDK | API 26 (Android 8.0) — covers ~98% of devices, enables NotificationChannels |
| Target SDK | API 35 (latest) |
| Testing | JUnit 5 (JVM, no Android dependencies in domain tests) |
| DI | Manual (single composition root in `app/`); revisit if it gets painful |

## Rationale

- **Kotlin + Compose + Room** are Google's current recommended stack. Standard, well-documented, plenty of community examples.
- **Coroutines + Flow** are the idiomatic async story; compose reactively from Room queries.
- **Foreground Service** is the only way to get a truly non-dismissable persistent notification on modern Android (per ADR-007).
- **Drive REST directly (no SDK)** mirrors what worked in Bean Counter — fewer dependencies, less mystery, no Play Services version conflicts. Sign-in uses Credential Manager (modern replacement for Google Sign-In SDK).
- **No DI framework yet** — for one app with one composition graph, Hilt/Koin add ceremony without clear payoff. Revisit if the graph grows.
- **JUnit 5** for domain tests gives us the modern assertion API and parameterized tests; works fine on JVM without an emulator.

## Consequences

- Min SDK 26 excludes pre-Oreo devices (8-year-old phones); acceptable for foster mama's current device.
- Direct Drive REST means we hand-roll auth refresh, error handling, and the multipart-upload boundary — same trade-off as Bean Counter, with the advantage that we already know the gotchas (see Bean Counter's `docs/post-1.0-audit.md` and its Drive Picker gotchas note in user memory).
- Manual DI requires discipline to keep the composition root clean; if it gets messy, Hilt is the upgrade path.

## Alternatives considered

- **Flutter / React Native:** rejected — single platform target, no need for cross-platform overhead, and the foreground-service + notification story is cleaner in native Android.
- **Compose Multiplatform:** rejected for the same reason; not worth the early-tech-stack risk for a single-platform app.
- **DataStore instead of Room:** considered for the simple "task list + snooze session" data. Room won because the list ordering, filtering, and reactive queries are awkward in DataStore but native in Room.
