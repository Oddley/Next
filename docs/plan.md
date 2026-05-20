# Next — Phased Implementation Plan

Three phases, each producing a feature-complete deliverable installable on foster mama's phone.

## Locked Architectural Decisions

| Decision | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Persistence | Room (SQLite wrapper) |
| Min SDK | API 31 (Android 12) — raised from 26 for SCHEDULE_EXACT_ALARM |
| Target SDK | 36 |
| Architecture | FC/IS — pure-Kotlin `domain/`, Android `data/`/`ui/`/`notification/` shells (ADR-002) |
| TDD scope | All `domain/` exports, JVM unit tests only (ADR-003) |
| Notification | Foreground service, non-dismissable (ADR-007) |
| Drive sync | DEFERRED indefinitely (ADR-009) |
| DI | Manual (single-graph composition root in `app/`); revisit if it gets painful |
| Recurrence | RFC 5545 RRULE via `dmfs/lib-recur` (ADR-010) |

## Phase Map

| Phase | Deliverable | Status |
|---|---|---|
| 1 | List + cross-off + reorder + Room persistence | ✓ COMPLETE |
| 2 | Foreground-service notification + snooze | ✓ COMPLETE |
| 3 | Foundation refactor — per-task snooze, UI sections, minSDK | Next |
| 4 | UI refactor — four collapsible sections, NEXT panel | After 3 |
| 5 | Task Emitters — scheduled + recurring tasks | After 4 |

---

## Phase 1 — List, Cross-off, Reorder, Persistence ✓ COMPLETE

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

## Phase 2 — Foreground Notification + Snooze ✓ COMPLETE

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

## Phase 3 — Foundation Refactor (snooze model + minSDK)

> **Status: Next**

**Deliverable:** No visible user-facing change, but the internal architecture is
correct for Phases 4 and 5. SnoozeSession is deleted, per-task snooze is in place,
the database is migrated, and minSDK is 31.

**Verification:**
1. `./gradlew test` — all domain tests pass (SnoozeOpsTest is replaced/updated)
2. Install on device — existing tasks preserved, notification still works
3. Snooze action from notification sets `snoozedUntil` on the top task; next task
   becomes the notification target
4. Mark complete from notification still works
5. Reboot — notification restores correctly

**Domain scope:**
- Delete `domain/snooze/` entirely (`SnoozeSession`, `NullSnoozeSession`, `SnoozeOps`)
- Add `snoozedUntil: Long?` and `emitterId: Long?` to `Task`
- Update `TaskOps`: add `computeNext(tasks)` (first non-snoozed task, scanning
  top-to-bottom), `applySnoozedUntil(task, epochMs)`, `applyUnsnoozed(task)`
- TDD: replace `SnoozeOpsTest` with `TaskOpsExtendedTest` covering snooze transitions

**Data scope:**
- Delete `SnoozeSessionEntity`, `SnoozeDao`, `SnoozeRepository`
- Add `snoozed_until` (Long?) and `emitter_id` (Long?) columns to `tasks` (nullable,
  default null — zero migration friction)
- Add `ui_prefs` table: `UiPrefsEntity(id=1, tasksExpanded, emittersExpanded, completedExpanded)`
- Add `last_processed` table: `LastProcessedEntity(id=1, epochMs)`
- Room MIGRATION_2_3
- Update `TaskEntity` mappers; update `TaskRepository`

**Notification scope:**
- Update `TopTaskService` to call `TaskOps.computeNext()` instead of `SnoozeOps.computeCurrentTop()`
- Update `SnoozeReceiver` to write `snoozedUntil` on the task directly
- Delete `SnoozeDao`/`SnoozeRepository` references

**minSDK:**
- Raise from 26 to 31 in `app/build.gradle.kts`
- Add `SCHEDULE_EXACT_ALARM` permission declaration to `AndroidManifest.xml`

---

## Phase 4 — UI Refactor (four collapsible sections)

> **Status: After Phase 3**

**Deliverable:** The list view is restructured into four collapsible sections.
NEXT, Tasks, Scheduled Tasks (empty placeholder), Completed.

**Verification:**
1. NEXT section shows the top non-snoozed task with Complete and Snooze buttons
2. Complete from NEXT section crosses off the task; next non-snoozed task appears
3. Snooze from NEXT section sets snoozedUntil; next non-snoozed task appears; 💤
   appears on snoozed task in Tasks section
4. NEXT highlighted task in Tasks section matches NEXT section
5. Collapse Tasks → count badge; expand → list returns
6. Empty Scheduled Tasks → shows "No Scheduled Tasks" label (no header/toggle)
7. Collapsed state survives app restart (Room-persisted)
8. Drag reorder in Tasks section still works; no offset math

**UI scope:**
- Rewrite `ListScreen.kt` as a four-section `LazyColumn` with collapsible headers
- `ListUiState` simplification: single `activeTasks: List<Task>`, no `displayTasks`
- Remove `computeDisplayTasks()`, dual-list state, display→underlying index mapping
- `NEXT` composable: shows top task text + Complete + Snooze action buttons
- `CollapsibleSection` composable: header with chevron + count; "none" placeholder
- `TaskRow` loses `isSnoozed` parameter; reads `task.snoozedUntil` directly
- `UiPrefsRepository` for collapsed state read/write
- `ListViewModel` subscribes to `uiPrefsRepository.prefs` flow

---

## Phase 5 — Task Emitters (scheduled + recurring tasks)

> **Status: After Phase 4**

**Deliverable:** Elly can create one-shot or recurring scheduled tasks. Emitters appear
in the Scheduled Tasks section. At the scheduled time the emitter's task surfaces at
the top of the active list (and the notification).

**Verification:**
1. "Schedule New Task" opens a creation dialog: name, date/time, recurrence (None /
   Daily / Weekly / Monthly / Yearly), optional end condition
2. Created emitter appears in Scheduled Tasks section with "next: [date]" label
3. At the scheduled time, the task appears at the top of Tasks and in NEXT
4. For recurring: after the task is acted on (or the next interval arrives), the
   emitter's next-emission date advances correctly
5. Tapping an emitter in Scheduled Tasks opens the edit dialog
6. One-shot emitter disappears from Scheduled Tasks after firing
7. Device rebooted before emission → alarm is rescheduled on boot; fires correctly
8. Phone off for 3 intervals → catches up with one emission, not three

**Domain scope (`domain/emitter/`):**
- `TaskEmitter` data class, `EmitterOps` (pure, TDD'd)
- `EmitterOps.computeNextEmission(rrule, after)` — advances via lib-recur
- `EmitterOps.shouldEmit(emitter, now)` — `nextEmission ≤ now`
- `EmitterOps.advanceEmitter(emitter, now)` — returns updated emitter or null if
  one-shot/exhausted
- TDD: covers one-shot, recurring, catch-up, exhausted recurrence

**Data scope:**
- `TaskEmitterEntity`, `EmitterDao`, `EmitterRepository`
- `EmitterRepository.dueEmitters(now)` — returns emitters ready to fire
- Emission transaction: update/create task (uncross + unsnooze + move to top OR
  insert new), advance `nextEmission`, write back `taskId`

**Notification scope:**
- `EmissionAlarmReceiver` — fired by AlarmManager; runs emission transaction;
  reschedules next alarm
- `AlarmScheduler` utility: `scheduleNext(emitters)` — sets
  `AlarmManager.setExactAndAllowWhileIdle` for earliest `nextEmission`
- `BootReceiver` — also calls `AlarmScheduler.scheduleNext()` after boot
- Permission check: `SCHEDULE_EXACT_ALARM` — request on first launch if missing;
  disable scheduling with UI prompt if denied

**UI scope:**
- `ScheduledTasksSection` composable: list of emitter rows (name + next emission)
- `EmitterEditDialog` composable: name field, date/time picker, recurrence picker
- `RecurrencePickerRow` composable: frequency dropdown + conditional day-of-week
  grid (Weekly) + end-condition row (Never / After N / Until date)
- Reference: Google Calendar's recurrence UI

**New dependency:**
```kotlin
implementation("org.dmfs:lib-recur:0.15.1")
```

---

## [DEFERRED] Drive Push/Pull (was Phase 3)

> **Status: Deferred indefinitely** — The backup use-case may be revisited in a
> future phase, but is explicitly out of scope for active development. See ADR-009.

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
