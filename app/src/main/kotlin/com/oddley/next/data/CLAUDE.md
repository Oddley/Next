# data — Contract

**Purpose:** Room persistence and the Repository that translates between Room entities and domain types. The only layer that touches SQLite.

## Files

| File | Role |
|---|---|
| `TaskEntity.kt` | Room `@Entity` — mirrors `Task` shape; id is auto-generated |
| `TaskDao.kt` | Room `@Dao` — queries and mutations; all methods are suspend or return Flow |
| `NextDatabase.kt` | Room `@Database` — singleton, version-tracked, migration-ready |
| `TaskRepository.kt` | Public API for the app; loads full list from DB, delegates logic to domain functions, writes results back |

## Contract (callers see only Repository)

```kotlin
// Observe all tasks (emits on every DB change)
val tasks: Flow<List<Task>>

// Mutations — all suspend, called from ViewModel coroutine scope
suspend fun addTask(text: String)
suspend fun crossOff(id: Long)
suspend fun restore(id: Long)
suspend fun editText(id: Long, newText: String)
suspend fun reorder(fromIndex: Int, toIndex: Int)
suspend fun bulkDeleteCrossedOff()
```

## Key Decisions

- Repository loads the full task list once per mutation (via `taskDao.getAllOnce()`), runs the domain function, and writes back only changed rows
- `tasks: Flow<List<Task>>` is the single source of truth; ViewModel collects it
- Room schema version starts at 1; every future schema change requires a `Migration` object — never `fallbackToDestructiveMigration()` in release builds
- `TaskEntity` ↔ `Task` conversion lives in extension functions at the bottom of `TaskRepository.kt`, not in the entity or DAO

## Dependencies

- `domain/task/` — for `Task`, `NullTask`, and all operation functions
- Room, KSP (annotation processing)
- `kotlinx.coroutines` (Flow, suspend)

## Anti-patterns

- Never expose `TaskEntity` outside this package — callers work with `Task`
- Never call domain functions from the DAO
- Never run Room queries on the main thread
- Never put business logic (ordering math, cross-off rules) in the DAO or Repository — delegate to domain functions
