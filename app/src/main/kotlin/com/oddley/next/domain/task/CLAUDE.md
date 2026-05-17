# domain/task — Contract

**Purpose:** Pure Kotlin types and operations for the task list. No Android imports, no Room, no Compose. Compiles on a plain JVM.

## Types

| Type | Role |
|---|---|
| `Task` | Core entity: id, text, order, crossedOff |
| `NullTask` | Null Object sentinel for "no task" — use instead of nullable Task returns |

## Exported Functions

All functions are top-level, pure, and side-effect-free. They take the current task list and return a new list (or derived value). The caller (Repository) is responsible for persisting any changes.

| Function | Inputs | Returns | Notes |
|---|---|---|---|
| `activeTasks` | `List<Task>` | `List<Task>` | Uncrossed tasks sorted by `order` asc |
| `crossedOffTasks` | `List<Task>` | `List<Task>` | Crossed tasks sorted by `order` asc |
| `nextOrderForInsert` | `List<Task>` | `Int` | Order value to assign when inserting at bottom |
| `crossOff` | `List<Task>`, `id: Long` | `List<Task>` | Marks task crossedOff=true; order unchanged |
| `restore` | `List<Task>`, `id: Long` | `List<Task>` | Sets crossedOff=false; task goes to bottom of active |
| `editText` | `List<Task>`, `id: Long`, `newText: String` | `List<Task>` | Updates text field; no-op if id not found |
| `reorder` | `List<Task>`, `fromIndex: Int`, `toIndex: Int` | `List<Task>` | Moves item within active list; renumbers active orders |
| `bulkDeleteCrossedOff` | `List<Task>` | `List<Task>` | Removes all crossedOff tasks |

## Invariants

- No Android imports anywhere in this package
- Functions never throw for "not found" id — they return the list unchanged
- `order` is a sparse positive integer used to sort tasks within each section (active / crossed) independently
- `nextOrderForInsert` returns 0 for an empty list, else `max(all orders) + 1000`
- `reorder` indices are into the **active** task list (not the full list)
- Crossed tasks' relative order to each other is preserved across all operations

## Dependencies

None. No imports outside `kotlin.*`.

## Anti-patterns

- Never import `android.*`, `androidx.*`, or Room in this package
- Never make functions suspend — domain is synchronous
- Never accept or return nullable Task — use NullTask
