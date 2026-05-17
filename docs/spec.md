# Next — Feature Spec

## The Mechanic

Foster mama has an ordered list of tasks. The **top item** is the one she should do next. A persistent notification keeps that top item visible to her. When she doesn't want to deal with it right now, **Snooze** temporarily pushes it (and any previously-snoozed items) down so the next item becomes the new top.

Importantly: the snoozed items stay snoozed even after the "snooze timer" expires. The session is only cleared when she interacts with whatever's now at the top of the stack. This means the new top stays prominent until she engages with it — no surprise re-shuffles back to the previously-snoozed item.

## Entities

### Task
- `id` — internal database key, never shown to user
- `text` — the task's display string
- `order` — integer; lower numbers are higher on the list
- `crossedOff` — boolean

### SnoozeSession (zero or one ever)
- `expiry` — epoch millis; after this point the session is "expired" but still applies
- `offset` — integer ≥ 1; the number of top items to skip when computing the current top

## Behaviors

### Computing the current top

```
activeOffset = snoozeSession?.offset ?? 0
candidates = tasks.filter { !it.crossedOff }.sortedBy { it.order }
top = candidates.getOrNull(activeOffset)
  ?: candidates.firstOrNull()   // fallback: if offset exceeds list, show top snoozed
```

If `activeOffset` exceeds the number of uncrossed tasks, fall back to the first uncrossed task (snoozed indicator in the notification UI signals that this is a snoozed item being surfaced).

If there are zero uncrossed tasks: notification shows "All caught up" or hides; list view shows empty state.

### Snooze action

- **No existing session:** create one with `expiry = now + 5 minutes` and `offset = 1`
- **Existing session (regardless of expiry):** set `expiry = now + 5 minutes`; `offset += 1`

### Mark complete action

- Cross off the current top item (the one computed above)
- If the snooze session is **expired** at this moment, clear the session (back to no snooze)
- If the snooze session is **active** (not expired), leave it alone — the user is "burning down through snoozed items" and the session continues to apply

### Snooze action's effect on session

- If the snooze session is **expired** at the moment Snooze is tapped, treat it as a new snooze: clear the old session, create a fresh one with `expiry = now + 5min`, `offset = 1`
- If the snooze session is **active**, refresh expiry + increment offset as above

## UI Surfaces

### Notification (foreground service, non-dismissable)

- Always visible while the app is installed and the user has granted notification permission
- Displays the current top task's text
- Two action buttons: **Mark complete** | **Snooze**
- Visual indicator when the displayed item is a snoozed-fallback (offset exceeded list, showing top snoozed item with snoozed marker)
- Tap the notification body → opens the list view

### List View (in-app)

Shows two stacked sections:

1. **To do** — uncrossed tasks in `order` sequence; each row has a drag handle (platform-standard reorderable list)
2. **Crossed off** — items that have been completed but not yet bulk-deleted; in their original relative `order` positions

Affordances:

- Tap a task to edit its text
- Tap a task's checkbox to cross it off / restore it
- Drag the handle to reorder (within the "To do" section)
- **Add task** button (FAB or bottom bar) — new task goes to bottom of "To do" by default
- **Delete all crossed off** button — bulk-clear the crossed-off section
- **Clear snooze** button — visible when a snooze session exists; clears it manually
- Long-press / context-menu / standard delete affordance on individual tasks

### Settings (or its own surface, TBD)

- **Push to Drive** — write current state to Drive (overwrites any existing file)
- **Pull from Drive** — replace local state with Drive's contents
- **Sign in to Drive** / **Sign out** — Drive authentication
- Notification permission status + link to grant if missing

## Constraints

- Single user, single device at a time (manual Drive transfer for device migration)
- No due dates, calendars, tags, categories, subtasks
- Tasks are simple strings; richer content is YAGNI for v1
- New tasks always default to bottom (user reorders if they want them higher)
- "Mark complete" = "cross off" (kept in crossed-off list for visibility / undo, not deleted until explicit bulk-delete)

## Out of scope for v1

- Multi-device live sync (manual push/pull is the model)
- Notification customization (sound, icon, content style)
- Bulk operations beyond "delete all crossed off"
- Search / filter / sort beyond manual ordering
- Themes / appearance settings (system-default theme only)
- Widgets / quick-tile / lock-screen integrations
