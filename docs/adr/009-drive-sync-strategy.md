# ADR-009: Drive Sync — Manual Push/Pull, No Merge

**Status:** Accepted

## Context

Foster mama may want to back up her task state and restore it on a different device. She does not need multi-device live sync — at any given time, exactly one device is the authority.

Google Drive is the natural backup channel: she has an account, the data is small, and we don't need to host anything.

## Decision

**Two explicit buttons: Push to Drive and Pull from Drive. No merge logic.**

### Semantics

- **Push** — serialize current local state (tasks + snooze session + schema version) to a single JSON file in Drive. Overwrites any existing file. The user is explicitly committing "this is the canonical state right now."
- **Pull** — fetch the JSON from Drive, parse it, wipe local Dexie/Room, replace with the parsed contents. Confirmation dialog required because Pull is destructive to local state.

### File location

- One file in the user's Drive: `next-tasks.json` (or user-picked location via Picker)
- `drive.file` scope only — app sees only files it created or user explicitly picked
- File created on first Push if it doesn't exist

### Authentication

- Google Sign-In via Credential Manager (modern replacement for the deprecated Google Sign-In SDK)
- OAuth scope: `https://www.googleapis.com/auth/drive.file`
- Sign-in is a separate explicit action ("Sign in to Drive" button in Settings); user can sign out and stay disconnected indefinitely

### What's serialized

```json
{
  "schemaVersion": 1,
  "tasks": [
    { "id": "...", "text": "...", "order": 0, "crossedOff": false }
  ],
  "snoozeSession": { "expiry": 1234567890, "offset": 1 }
}
```

`snoozeSession` is `null` in JSON if the local state has `NullSnoozeSession`.

### Schema version

The serialized JSON includes a `schemaVersion` field. On Pull, if the file's schema version is **newer** than what this app knows, refuse to import (would risk corrupting state). If **older**, run any necessary migrations or refuse with a clear "old data, please update the source app" message.

## Rationale

- **No merge logic** because manual push/pull with explicit confirmation matches foster mama's mental model ("save my progress" / "restore my progress")
- **drive.file scope** matches our least-privilege stance and avoids the broad-Drive consent screen
- **Single file** is simple, atomic, and trivially small (a few KB even with hundreds of tasks)
- **Schema version guard** prevents future-app-format files from corrupting older-app installs — cheap insurance

## Consequences

- The user is responsible for sync timing. Forgetting to Push before switching devices = data loss on the second device. We can mitigate with prominent "Last pushed: N minutes ago" indicator in Settings.
- No live updates between devices — Drive sees current state only at Push moments
- The first Pull on a new device fully replaces local state. This is the right behavior for a manual backup model.

## Anti-patterns

- Auto-pushing on every edit — defeats the "manual" promise; user loses control over what gets persisted
- Diff/merge logic — explicit overwrite is simpler and matches user intent
- Multiple files per task or per category — single file is simpler and the data is small enough

## Drive Picker gotchas to remember

If the file picker is ever used (for choosing a custom location or importing a shared file), the Picker has known quirks:

- **`setAppId(projectNumber)` is required** for drive.file scope to grant on files shared from another user. Project number is the numeric prefix of the OAuth client ID.
- **Folder-search queries fail** when the user only has file-scope (not folder-scope) — use direct file ID for reads.
- **`gapi.drive.share.ShareClient` is deprecated** — don't reach for it for sharing UI; use Drive's web UI deep-link instead.

(For Next v1 we don't expose Drive sharing — single user — but these notes will save time if/when we ever add it.)

## Out of scope

- Multi-device live sync
- Conflict resolution (a Pull always wins; user is responsible for sequencing)
- Selective sync (per-task, partial state)
- Other backends (iCloud, Dropbox, self-hosted)
