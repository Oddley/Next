# app — Composition Root

**Purpose:** Application class and manual dependency injection. Owns the single object graph for the entire app lifetime.

## Files

| File | Role |
|---|---|
| `NextApplication.kt` | `Application` subclass; creates `NextDatabase` and `TaskRepository`; exposes them as properties |

## Object graph (Phase 1)

```
NextApplication
  └── NextDatabase       (Room singleton, application lifetime)
        └── TaskDao
  └── TaskRepository     (wraps TaskDao; application lifetime)
```

`MainActivity` retrieves `TaskRepository` from `(application as NextApplication).taskRepository` and passes it to the ViewModel factory.

## Key Decisions

- Manual DI — no Hilt, no Koin. Single graph, simple app.
- Database and Repository are created lazily (`by lazy`) in `NextApplication` to avoid blocking the main thread at startup
- `NextDatabase` uses `Room.databaseBuilder`; no `fallbackToDestructiveMigration()` — explicit migrations only

## Dependencies

- `data/NextDatabase`, `data/TaskRepository`
- `android.app.Application`

## Anti-patterns

- Never put business logic in `NextApplication`
- Never create a second instance of `NextDatabase`
- Never inject dependencies through the Activity — pass through ViewModel factory
