# Next — Project Context

Native Android TODO app: foster mama's working stack with controlled deferral.

The defining mechanic is **snooze + task emitters**: tasks can be snoozed individually
(temporarily removed from the NEXT slot), and emitters automatically surface tasks on a
schedule (one-shot or recurring). The persistent notification mirrors the NEXT slot —
the top non-snoozed task — keeping it in her face until she acts on it.

## Standards (read before writing any code)

| Concern | ADR |
|---|---|
| Tech stack | [ADR-001](docs/adr/001-tech-stack.md) |
| Architecture (Domain/Data/UI/Notification) | [ADR-002](docs/adr/002-architecture.md) |
| Testing (Red-Green TDD on domain) | [ADR-003](docs/adr/003-tdd.md) |
| Null handling (Nothing is Something) | [ADR-004](docs/adr/004-nothing-is-something.md) |
| Documentation | [ADR-005](docs/adr/005-documentation.md) |
| Git workflow | [ADR-006](docs/adr/006-git-workflow.md) |
| Notification (foreground service) | [ADR-007](docs/adr/007-notification-strategy.md) |
| Snooze data model (**superseded by ADR-010**) | [ADR-008](docs/adr/008-snooze-data-model.md) |
| Drive sync (manual push/pull, deferred) | [ADR-009](docs/adr/009-drive-sync-strategy.md) |
| Task Emitters — scheduled + recurring tasks | [ADR-010](docs/adr/010-task-emitters.md) |

## Actual Directory Structure (as built — Phases 1 & 2 complete)

```
app/src/main/
  kotlin/com/oddley/next/
    domain/
      task/
        Task.kt               ← data class + NullTask + pure list ops
        TaskOps.kt            ← activeTasks(), crossedOffTasks(), reorder(), etc.
        CLAUDE.md
      snooze/
        SnoozeSession.kt      ← data class + NullSnoozeSession sentinel
        SnoozeOps.kt          ← computeCurrentTop(), applySnooze(), applyMarkComplete()
        CLAUDE.md
    data/
      TaskEntity.kt           ← Room entity + toDomain()/toEntity() mappers
      TaskDao.kt
      SnoozeSessionEntity.kt  ← Room entity (singleton row, id=1)
      SnoozeDao.kt
      NextDatabase.kt         ← Room DB v2, MIGRATION_1_2
      TaskRepository.kt       ← exposes Flow<List<Task>>, reorder (id-based diff)
      SnoozeRepository.kt     ← exposes Flow<SnoozeSession>, snooze(), markComplete(), clear()
      CLAUDE.md
    ui/
      ListViewModel.kt        ← combine(tasks, session) → ListUiState
                                 DisplayTask(task, isSnoozed) for display ordering
                                 computeDisplayTasks(): [top, 💤snoozed..., remaining...]
      ListScreen.kt           ← stateless Compose; drag state held locally (draggedItems)
                                 to avoid Room round-trips per move
      CLAUDE.md
    notification/
      TopTaskService.kt       ← foreground service; observing flag prevents double-start;
                                 lastNotification re-posted on dismiss to re-anchor
      MarkCompleteReceiver.kt
      SnoozeReceiver.kt
      BootReceiver.kt         ← restarts service after reboot
      NotificationDismissedReceiver.kt ← re-anchors when user swipes (Android 13+)
      CLAUDE.md
    app/
      NextApplication.kt      ← manual DI root; taskRepository + snoozeRepository lazy
      CLAUDE.md
  res/
    drawable/
      ic_launcher_background.xml   ← solid #7BD7FF (light blue)
      ic_launcher_foreground.xml   ← 108dp adaptive icon foreground (72dp safe zone)
      ic_notification.xml          ← 24dp white silhouette for status bar
    mipmap-anydpi/
      ic_launcher.xml              ← adaptive-icon + monochrome (API 33+ themed icons)
      ic_launcher_round.xml
    mipmap-anydpi-v26/
      ic_launcher.xml              ← adaptive-icon (API 26+)
      ic_launcher_round.xml
    mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
      ic_launcher.webp             ← legacy fallback bitmaps (unchanged from template)
      ic_launcher_round.webp
  AndroidManifest.xml
app/src/test/
  kotlin/com/oddley/next/domain/snooze/
    SnoozeOpsTest.kt          ← 12 tests, covers full ADR-008 state-transition matrix
logo/
  check.svg                   ← source artwork (Affinity Designer export)
  check.af                    ← Affinity Designer source file
  gen_icons.py                ← regenerates all Android icon XML from check.svg
                                 (flattens shear matrix transforms into path coords,
                                  scales to 72dp safe zone, outputs 5 XML files)
app/
  release-notes.txt           ← edit before each Firebase distribution; script refuses
                                 to run if this is empty
scripts/
  distribute.ps1              ← builds debug APK + uploads to Firebase App Distribution
                                 reads firebase.appId and firebase.testers from
                                 local.properties (gitignored)
docs/
  spec.md
  plan.md                     ← Phases 1 & 2 marked COMPLETE; Phase 3 DEFERRED
  adr/001-009
  setup.md
```

## Key Invariants (override everything else)

1. **Domain code is pure Kotlin and fully tested.** No Android imports, no Room, no Compose. Compiles in a plain JVM module.
2. **Snooze is per-task.** Each `Task` carries a nullable `snoozedUntil: Long?` timestamp. Tasks do not reorder when snoozed — they stay in drag position and show 💤. NEXT is always the first non-snoozed task scanning top-to-bottom. The old `SnoozeSession`/offset model is deleted (ADR-010).
3. **One task per emitter.** A `TaskEmitter` always has at most one living `Task` (by `emitterId` FK). On emission, reuse that task unconditionally — uncross, unsnooze, move to top. Never create a second task for the same emitter.
4. **Cross-off preserves order.** Items don't reorder when crossed off — they're filtered to the Completed section while keeping their relative positions.
5. **Absence is typed.** No nullable returns in domain code — use sealed types or Null Objects (ADR-004).
6. **Red before green.** No domain feature code without a failing test first.
7. **The notification is the app's main interface.** The full list view is for editing; the notification is the daily-driver surface. If a feature lives only in the list view, foster mama may never see it.

## Gotchas Learned in Implementation

### Drag-and-drop
- `sh.calvin.reorderable` 3.1.0: `draggableHandle` takes `onDragStarted: () -> Unit` (no `Offset` param — older versions had it).
- Don't write to Room on every `onMove` — it causes the list to lurch. Keep a local `draggedItems: List<DisplayTask>?` snapshot and write once in `onDragStopped`.
- `TaskRepository.reorder` must diff by **task ID**, not by list position. A position-based `zip` will always see equal order values if the sort is a permutation of the same set.

### Display ordering with snooze offset
- `ListUiState` carries **two** lists: `displayTasks: List<DisplayTask>` (display order, for rendering) and `activeTasks: List<Task>` (DB order, for index mapping in `onDragStopped`).
- `computeDisplayTasks(activeTasks, session)`: when `offset = O` and `O < N`, display = `[activeTasks[O], activeTasks[0..O-1] (💤), activeTasks[O+1..]]`.
- `onDragStopped` converts display index → underlying index before calling `onReorder`:
  - `toDisplay == 0` → `underlying = O` (dropped at top slot)
  - `1 ≤ toDisplay ≤ O` → `underlying = toDisplay - 1` (dropped in snoozed zone)
  - `toDisplay > O` → `underlying = toDisplay` (dropped in remaining zone)

### BasicTextField quirks
- `singleLine = true` makes the keyboard show "Done" (✓). Use `maxLines = 1` instead and detect `'\n'` in `onValueChange` to commit — gives the ↵ key.
- `onFocusChanged` fires with `isFocused = false` immediately on composition, before `requestFocus()` runs. Guard with an `editorFocused: Boolean` flag — only commit/exit when `editorFocused` was already `true`.
- Use `TextFieldValue` (not `String`) to set `selection = TextRange(0, text.length)` for select-all on enter.

### Notification channel
- `IMPORTANCE_LOW` → "Silent" section (no badge, no sound, and visually demoted). Use `IMPORTANCE_DEFAULT` + `setSound(null, null)` + `enableVibration(false)` to land in "Alerting" without making noise.
- Android ignores importance changes on existing channels. Bump the channel ID (we used `next_top_task_v2`) and delete the old one in `ensureChannel()`.
- Android 13+ lets users swipe foreground-service notifications. Fix: set `deleteIntent` → `NotificationDismissedReceiver` → `TopTaskService.start()` → `onStartCommand` re-posts `lastNotification` via `startForeground()`. Guard with an `observing` flag so the Flow is only collected once.

### Firebase App Distribution
- Use the **Firebase CLI**, not the Gradle plugin. `firebase-appdistribution-gradle` 5.0.0
  expects AGP's legacy `AppExtension` which was removed in AGP 9.x — it hard-crashes at
  sync time. The CLI (`firebase appdistribution:distribute ...`) has no such dependency.
- Private config lives in `local.properties` (already gitignored):
  `firebase.appId` and `firebase.testers` (Elly's email).
- Firebase project: `next-for-elly`
  (console.firebase.google.com/project/next-for-elly/appdistribution)
- Workflow: edit `app/release-notes.txt`, then run `.\scripts\distribute.ps1`.
  The script validates config, builds the APK, and uploads in one shot.
- First build was successfully distributed. Elly uses the **Firebase App Tester** app
  on her phone to receive and install new builds.

### Icon pipeline (`logo/gen_icons.py`)
- The source SVG uses a general affine matrix with shear — Android `<vector>` `<group>` only supports translate/rotate/scale, **not** shear. The script bakes all transforms (outer translate + inner shear matrix) directly into the path coordinates.
- Notification icon gap: the black outline in the colour icon is an evenOdd compound path (outer shell + inset inner shell). Re-use that same compound on the white notification paths — the inner subpath punches out the border zone, reproducing the gap without extra geometry.
- To regenerate all icons: `cd logo && python gen_icons.py`

## What this app is NOT

- **Not a sync tool.** Drive sync is DEFERRED indefinitely (Phase 3). One person, one device.
- **Not a calendar.** No due dates, no reminders, no time-of-day. Just an ordered list with snooze.
- **Not a project management tool.** No categories, tags, subtasks, dependencies. Single flat list.
