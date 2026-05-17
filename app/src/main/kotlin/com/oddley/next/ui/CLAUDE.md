# ui — Contract

**Purpose:** Compose screens and ViewModels. Thin glue between domain state and the user's finger. No branching domain logic here — that lives in `domain/`.

## Files

| File | Role |
|---|---|
| `ListViewModel.kt` | Collects `TaskRepository.tasks` Flow; exposes `UiState`; dispatches user actions to Repository |
| `ListScreen.kt` | Stateless Compose screen; renders UiState; calls ViewModel for all interactions |

## UiState shape

```kotlin
data class ListUiState(
    val activeTasks: List<Task> = emptyList(),
    val crossedOffTasks: List<Task> = emptyList(),
)
```

Derived from the full task list by the domain helpers `activeTasks()` and `crossedOffTasks()`.

## Screen structure

Two sections in a single `LazyColumn`:
1. **To do** — active tasks, each row has: checkbox (cross off), drag handle (reorder), tappable text (edit)
2. **Crossed off** — crossed tasks, each row has: checkbox (restore). No drag handle.

Bottom of the list: inline "add task" row that appears when the FAB is tapped (text field + confirm). FAB lives in the scaffold.

Buttons in the app bar or bottom of crossed section: **Delete all crossed off** (visible only when crossed section is non-empty).

## Key Decisions

- ViewModel is created via `viewModel()` in `MainActivity`; passed down to `ListScreen` as a parameter
- `ListScreen` is stateless — it receives `uiState: ListUiState` and lambda callbacks
- Drag-and-drop uses `sh.calvin.reorderable` — `ReorderableItem` + `rememberReorderableLazyListState`
- Inline edit: tap task text → that row switches to a `BasicTextField`; confirm on IME Done or focus loss
- Inline add: FAB tap → `showAddRow = true` state in screen; a `BasicTextField` row appears at the bottom of the active section; confirm on IME Done

## Dependencies

- `domain/task/Task` (type only — never call domain functions from here)
- `data/TaskRepository` (via ViewModel)
- Jetpack Compose, Material3
- `sh.calvin.reorderable`
- `androidx.lifecycle:lifecycle-viewmodel-compose`

## Anti-patterns

- Never import Room or TaskEntity in this package
- Never call domain operation functions directly from a composable — go through ViewModel
- Never put if/else domain logic in the ViewModel — it should call one repository method and return
