# ADR-010: Task Emitters — Scheduled and Recurring Tasks

**Status:** Accepted

## Context

Elly requested the ability to schedule tasks at a specific future date/time and to create
recurring tasks (e.g., "remind me every Monday"). These features require a scheduling
primitive that sits above the existing manually-ordered task list.

The existing snooze mechanism (`SnoozeSession` with a list-position offset) does not
compose with tasks that arrive from an external schedule. The offset model requires
knowing the position of the snoozed item in a flat list; emitted tasks arriving at the
"very tippy top" break that assumption. The offset model is therefore replaced entirely.

Separately, the flat single-section list view needs to be restructured to present the
three distinct surfaces (notification mirror, manual task list, emitter list) without
visual clutter.

---

## Decisions

### 1. UI Layout: Four Collapsible Sections

The list view contains four sections in this order:

| Section | Content | Collapsible? | Ordering |
|---|---|---|---|
| **NEXT** | Current top task; Complete + Snooze actions | No | — |
| **Tasks** | Active tasks; drag handles; 💤 on snoozed; NEXT task highlighted | Yes | Manual (drag) |
| **Scheduled Tasks** | Emitters; next-emission date visible | Yes | By next emission |
| **Completed** | Crossed-off tasks; Delete All button | Yes | Most-recently-completed first |

Footer rows under each collapsible section:
- Tasks → "Add New Task" (inline text entry)
- Scheduled Tasks → "Schedule New Task" (opens creation dialog)
- Completed → "Delete All Completed" (confirm before acting)

Collapsed sections display a count badge on the section header. Sections with zero
items replace the entire header + collapse toggle with a simple grey label
("No Scheduled Tasks").

Collapsed state is stored in Room (`UiPrefsEntity`, singleton row, id = 1) so it
survives app restarts without requiring a full data layer like DataStore.

### 2. Snooze: Per-Task Expiry Replaces SnoozeSession

`SnoozeSession`, `SnoozeSessionEntity`, `SnoozeOps`, `SnoozeDao`, and
`SnoozeRepository` are deleted entirely.

Snooze becomes a nullable field on each `Task`:

```kotlin
data class Task(
    val id: Long,
    val text: String,
    val position: Int,
    val crossedOff: Boolean,
    val snoozedUntil: Long?,   // epoch ms; null = not snoozed
    val emitterId: Long?,      // FK to TaskEmitter; null = manual task
)
```

A task is considered snoozed when `snoozedUntil != null &&
snoozedUntil > System.currentTimeMillis()`. Expired snooze timestamps are treated as
null (not snoozed).

**Tasks do not reorder when snoozed.** They stay at their drag position and gain a
💤 indicator. The NEXT section always shows the first non-snoozed task scanning
top-to-bottom through the Tasks list.

**Consequence:** `computeDisplayTasks()` and the dual-list `ListUiState`
(`displayTasks` + `activeTasks`) are removed. The drag-and-drop index mapping that
converts display position → underlying position is no longer needed. The ViewModel
becomes significantly simpler.

### 3. Task Emitters

A `TaskEmitter` is a first-class entity that generates tasks on a schedule.

```kotlin
data class TaskEmitter(
    val id: Long,
    val name: String,           // Task text to emit
    val nextEmission: Long,     // Epoch ms of next scheduled emission
    val rrule: String?,         // RFC 5545 RRULE string; null = one-shot
    val taskId: Long?,          // ID of the single living Task for this emitter
)
```

**One task per emitter, always.** When an emitter fires, the system looks up a task
by `emitterId`. Regardless of that task's current state (crossed off, snoozed, active),
the system uncrosses it, unsnoozes it, and moves it to the top of the active list.
If no task exists (first emission ever), a new one is created at the top and its ID is
written back to `TaskEmitter.taskId`.

**Catch-up.** If the device was off across multiple emission intervals, only one task
is emitted. `nextEmission` is advanced using modulus arithmetic to find the smallest
future timestamp — no interval replay.

**One-shot emitters** (null rrule) are deleted after their task is created.

### 4. Recurrence Library: lib-recur (RFC 5545)

`dmfs/lib-recur` is used to parse, iterate, and advance RRULE strings per RFC 5545
(the same standard used by Google Calendar, Apple Calendar, and Outlook). This gives
correct handling of complex rules: BYDAY, BYMONTHDAY, COUNT, UNTIL, WKST, etc.

The recurrence editor UI is custom-built in Compose. Google Calendar's recurrence
dialog is used as the UX reference:
- Frequency picker: Does not repeat / Daily / Weekly / Monthly / Yearly
- Weekly: day-of-week checkbox grid
- End condition: Never / After N occurrences / On date

### 5. Alarm Strategy: AlarmManager with SCHEDULE_EXACT_ALARM

Task emission is driven by `AlarmManager.setExactAndAllowWhileIdle()`. A single alarm
is maintained for the next pending emission across all emitters. On alarm fire:
1. Process all emitters whose `nextEmission ≤ now`
2. Advance each fired emitter's `nextEmission` (or delete if one-shot)
3. Schedule next alarm for the smallest remaining `nextEmission`

WorkManager is not used — its minimum deferrable window (~15 min) is insufficient for
minute-granularity scheduling.

### 6. minSDK: Raised from 26 to 31 (Android 12)

API 31 is required for `SCHEDULE_EXACT_ALARM`. Both target devices run Android 16
(API 36). No known users below API 31.

The app requests the `SCHEDULE_EXACT_ALARM` permission on first launch (if not
already granted), directing the user to Settings → Apps → Special app access →
Alarms & reminders. The alarm feature degrades gracefully with a UI prompt if the
permission is denied.

### 7. Persistence: Room Singleton Rows

Two new singleton-row tables follow the existing `SnoozeSessionEntity` pattern:

- `UiPrefsEntity(id = 1, tasksExpanded, emittersExpanded, completedExpanded)` —
  collapsed section state
- `LastProcessedEntity(id = 1, epochMs)` — timestamp of last emission check,
  used for catch-up math

Both survive device reboot (SQLite-backed) and app updates.

---

## Consequences

**Removed:**
- `domain/snooze/` — `SnoozeSession`, `SnoozeOps` (net deletion)
- `data/` — `SnoozeSessionEntity`, `SnoozeDao`, `SnoozeRepository`
- `ui/` — `computeDisplayTasks()`, dual-list `ListUiState`, display→underlying index
  mapping in `onDragStopped`

**Added:**
- `domain/task/Task.kt` — two new fields: `snoozedUntil`, `emitterId`
- `domain/emitter/` — `TaskEmitter`, `EmitterOps` (pure, TDD'd)
- `data/` — `TaskEmitterEntity`, `EmitterDao`, `EmitterRepository`,
  `UiPrefsEntity`, `UiPrefsDao`, `LastProcessedEntity`, `LastProcessedDao`
- `notification/` — `EmissionAlarmReceiver`, updated `BootReceiver`
- `ui/` — four-section `ListScreen`, `ScheduledTasksSection`, collapsible headers,
  recurrence editor dialog

**Schema:** Room database version bumps from v2 to v3. Migration adds
`snoozed_until` (Long?) and `emitter_id` (Long?) to `tasks`; adds `task_emitters`,
`ui_prefs`, `last_processed` tables.

**New dependency:** `org.dmfs:lib-recur`

**Breaking change:** minSDK 31 — users below Android 12 cannot install.
