# ADR-007: Notification Strategy — Foreground Service for Persistence

**Status:** Accepted

## Context

The defining UX of Next is a persistent notification showing the current top task. "Persistent" means: foster mama doesn't have to launch the app to see what's next, and she can't accidentally lose the notification by swiping carelessly.

Android offers two flavors of "sticky" notification:

1. **`setOngoing(true)` on a regular notification** — visually pinned (no swipe-to-dismiss), but can be cleared via "Clear all" or app-info → clear notifications. Lightweight; no service required.
2. **Foreground service notification** — tied to a running service. Cannot be dismissed by the user at all (only by stopping the service). Heavyweight: requires a service, an Android 14+ service type declaration, and runtime permission handling.

## Decision

**Use a Foreground Service.** Truly non-dismissable persistence is a core part of the spec — "the notification is the app's main interface" (per `CLAUDE.md`). Anything less risks foster mama losing her primary surface to a casual swipe.

### Implementation outline

- **`TopTaskService`** — `Service` subclass that calls `startForeground(NOTIFICATION_ID, notification)` in `onStartCommand`
- **`AndroidManifest.xml`** declares the service with `android:foregroundServiceType="dataSync"` (or `specialUse` if `dataSync` doesn't fit Android's evolving categories)
- **`POST_NOTIFICATIONS` permission** requested on first launch (Android 13+ requirement)
- **`BOOT_COMPLETED` receiver** restarts the service after device reboot
- **Notification builder** reads current state from the repository, computes the current top via domain code, and renders the notification with Mark complete + Snooze action buttons
- **Action receivers** (`MarkCompleteReceiver`, `SnoozeReceiver`) handle taps on the notification actions; they dispatch back to the repository → domain → update notification

### Notification refresh

The notification text needs to update whenever:
- Tasks change (add, edit, cross off, reorder)
- Snooze session changes (created, refreshed, cleared)
- Snooze session expires AND would change what's displayed (mostly irrelevant since the offset persists past expiry; but if we show a snoozed indicator, it might toggle on expiry)

Refresh strategy: the service collects a `Flow` from the repository that emits whenever the underlying state changes. Each emission re-renders the notification via `NotificationManager.notify(NOTIFICATION_ID, …)`.

## Rationale

- Non-dismissability is a hard spec requirement; only foreground services deliver it
- The service is genuinely doing work (observing state, refreshing notification) which justifies its `dataSync` categorization
- Modern Android (8+, our min SDK 26) handles foreground services cleanly with NotificationChannels

## Consequences

- First-launch prompt for notification permission (Android 13+); we need a clear in-app explanation of why we want it before the system prompt
- Service runs persistently; consumes a small amount of battery (< 0.1% typical)
- Android may kill the service in extreme low-memory scenarios; `START_STICKY` causes restart
- The service must be running BEFORE any notification can be posted — including after boot, hence the boot receiver
- Service type rules tighten in newer Android versions; we may need to re-categorize (e.g., from `dataSync` to `specialUse`) as Android evolves. Monitor when bumping target SDK.

## Anti-patterns

- Posting a notification directly from a `BroadcastReceiver` without going through the service — would work but bypasses our lifecycle and may inconsistencies across SDK levels
- Using `setOngoing(true)` and hoping it's "good enough" — it isn't; users dismiss via "Clear all" without realizing what they're doing
- Running the service in the same process as Compose UI work — fine for v1 (the service is lightweight), revisit if we ever do heavy work
