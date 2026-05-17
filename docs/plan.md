# Next — Phased Implementation Plan

Three phases, each producing a feature-complete deliverable installable on foster mama's phone.

## Locked Architectural Decisions

| Decision | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Persistence | Room (SQLite wrapper) |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | 35 |
| Architecture | FC/IS — pure-Kotlin `domain/`, Android `data/`/`ui/`/`notification/` shells (ADR-002) |
| TDD scope | All `domain/` exports, JVM unit tests only (ADR-003) |
| Notification | Foreground service, non-dismissable (ADR-007) |
| Drive sync | Manual push/pull, no merge (ADR-009) |
| DI | Manual (single-graph composition root in `app/`); revisit if it gets painful |

## Phase Map

| Phase | Deliverable | Size |
|---|---|---|
| 1 | List + cross-off + reorder + Room persistence | Medium |
| 2 | Foreground-service notification + snooze | Largest |
| 3 | Drive push/pull | Medium |

---

## Phase 1 — List, Cross-off, Reorder, Persistence

**Deliverable:** Installable app on foster mama's phone with a working in-app list view. Add tasks, edit them, reorder via drag handle, cross off / restore, delete all crossed off. Data persists across app launches.

**Verification:**
1. `./gradlew installDebug` puts the app on an attached device or emulator
2. Add several tasks → reorder them → close app → reopen → order preserved
3. Cross off a task → it moves to crossed-off section maintaining relative order
4. Restore → back to top section
5. Delete all crossed off → crossed section empties; "To do" untouched
6. Edit task text → persists

**Domain scope (`domain/task/`, fully TDD'd):**
- `Task` type, `NullTask`, pure list operations: insert at bottom, reorder, cross-off, restore, edit text, bulk-delete-crossed
- Pure sort/filter helpers used by the UI

**Data scope (`data/`):**
- Room schema (single `tasks` table), DAO, migration plan from Day 1
- Repository class that wraps DAO and exposes domain types

**UI scope (`ui/`):**
- `ListScreen` Compose composable with two sections (To do / Crossed off)
- Platform-standard drag-and-drop reorder
- Add task FAB → simple text-entry dialog or inline row
- Bulk delete button

**Notification scope:** none yet (Phase 2)

**Documentation:**
- `CLAUDE.md` per `domain/task/`, `data/`, `ui/`, `app/`

---

## Phase 2 — Foreground Notification + Snooze

**Deliverable:** Persistent notification shows the current top task and supports Mark complete + Snooze actions. The snooze mechanic from the spec works end-to-end.

**Verification:**
1. App is open → notification appears in tray with top task's text
2. Close the app entirely → notification persists; can't be swiped away
3. Tap **Mark complete** in notification → task crosses off, notification updates to next task
4. Tap **Snooze** → notification updates to show the next task; snooze indicator absent (it's the natural new top)
5. Tap **Snooze** again before 5 minutes → notification skips one further; offset increments
6. Wait 5 minutes → notification still showing the deeper item (session expired but still applies)
7. Tap **Mark complete** on expired session → snooze clears; back to natural top of list
8. Reboot phone → notification reappears after boot (BOOT_COMPLETED handling)
9. Empty list → notification shows "All caught up" or hides

**Domain scope (`domain/snooze/`):**
- `SnoozeSession` type, `NullSnoozeSession`, pure functions for compute-top, apply-snooze, apply-mark-complete, expire-handling
- Tests cover every state transition matrix (session present vs absent × active vs expired × top is real vs snooze-fallback)

**Data scope:**
- `SnoozeSession` stored as a Room singleton table (or DataStore — preference TBD during Phase 2 design)

**Notification scope (`notification/`):**
- `TopTaskService` — foreground service that owns the notification lifecycle
- Notification builder that reflects current domain state
- Action receivers for Mark complete and Snooze (broadcast → service → repository update)
- Boot receiver to restart the service after device reboot
- Permission flow for POST_NOTIFICATIONS on Android 13+

**UI scope (additions):**
- "Clear snooze" button in list view (visible only when session exists)
- Visual indicator on the snoozed-fallback case if it surfaces in-app

---

## Phase 3 — Drive Push/Pull

**Deliverable:** User can sign into Google Drive, push their current task state to a file, and pull a previously-pushed file back to replace local state.

**Verification:**
1. Sign in to Drive via in-app button → Google account picker → consent screen
2. Push button → local state written to Drive as a JSON file (consistent name, e.g., `next-tasks.json` or user-picked)
3. On a fresh install (or after a wipe), sign in, Pull → local state matches what was pushed
4. Push → modify a task locally → Push again → Drive file overwritten with new state
5. Sign out → buttons disabled until next sign-in

**Domain scope (`domain/sync/`):**
- Pure serialize/deserialize of the full state (tasks + snooze session + schema version)
- No transport code

**Data scope (`data/drive/`):**
- Google Sign-In + Drive REST API wrapper
- Read/write a single JSON file via `drive.file` scope

**UI scope:**
- Sync section in settings (or as part of the list view): Sign in/out, Push, Pull, last-pushed timestamp
- Confirm dialog before Pull (overwrites local data — destructive)

**Out of scope (intentionally):**
- Auto-sync, conflict resolution, multi-device live sync — all "future phase or never"
- Per-task sync state — the whole state is pushed/pulled as one blob

---

## Execution Approach

Per phase:
1. Brief Q&A round at phase start for decisions not in this plan
2. Create per-folder `CLAUDE.md` contracts before code (ADR-005)
3. Red-green TDD for every domain function (ADR-003)
4. Conventional commits per logical change (ADR-006)
5. Self-test the verification steps end-to-end
6. Hand off to human for on-device testing

**Plan revision:** This file may be updated as phases complete to reflect actual structure and any deviation discovered during execution.
