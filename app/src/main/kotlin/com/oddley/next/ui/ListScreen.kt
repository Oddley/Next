package com.oddley.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import com.oddley.next.domain.snooze.NullSnoozeSession
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.oddley.next.domain.task.Task
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    uiState: ListUiState,
    onAddTask: (String) -> Unit,
    onCrossOff: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onEditText: (Long, String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onBulkDeleteCrossedOff: () -> Unit,
    onClearSnooze: () -> Unit,
) {
    var showAddRow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Next") },
                actions = {
                    if (uiState.snoozeSession != NullSnoozeSession) {
                        FilledTonalButton(
                            onClick = onClearSnooze,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Clear snooze")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRow = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        },
    ) { paddingValues ->
        TaskList(
            modifier = Modifier
                .padding(paddingValues)
                .imePadding(),
            uiState = uiState,
            showAddRow = showAddRow,
            onAddTask = { text ->
                onAddTask(text)
                showAddRow = false
            },
            onAddRowDismiss = { showAddRow = false },
            onCrossOff = onCrossOff,
            onRestore = onRestore,
            onEditText = onEditText,
            onReorder = onReorder,
            onBulkDeleteCrossedOff = onBulkDeleteCrossedOff,
            onClearSnooze = onClearSnooze,
        )
    }
}

@Composable
private fun TaskList(
    modifier: Modifier = Modifier,
    uiState: ListUiState,
    showAddRow: Boolean,
    onAddTask: (String) -> Unit,
    onAddRowDismiss: () -> Unit,
    onCrossOff: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onEditText: (Long, String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onBulkDeleteCrossedOff: () -> Unit,
    onClearSnooze: () -> Unit,
) {
    val lazyListState = rememberLazyListState()

    // Local drag state — update visually on every onMove, write to DB only when drag stops.
    // This prevents Room round-trips during the drag gesture (which caused lurching).
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggedItems by remember { mutableStateOf<List<DisplayTask>?>(null) }
    val displayedActiveTasks = draggedItems ?: uiState.displayTasks

    // When the DB updates after the drag completes, clear the local snapshot so the
    // canonical Room order takes over again.
    LaunchedEffect(uiState.activeTasks) {
        if (draggingId == null) draggedItems = null
    }

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Reorder the local snapshot for smooth animation — no DB write here.
        val headerOffset = 1 // "To do" header occupies index 0
        val fromIdx = from.index - headerOffset
        val toIdx = to.index - headerOffset
        val current = (draggedItems ?: uiState.displayTasks).toMutableList()
        if (fromIdx in current.indices && toIdx in current.indices) {
            current.add(toIdx, current.removeAt(fromIdx))
            draggedItems = current
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
    ) {
        // ── To do section ─────────────────────────────────────────────────────
        item(key = "header_todo") {
            SectionHeader(title = "To do")
        }

        itemsIndexed(
            items = displayedActiveTasks,
            key = { _, displayTask -> displayTask.task.id },
        ) { _, displayTask ->
            ReorderableItem(reorderState, key = displayTask.task.id) { isDragging ->
                ActiveTaskRow(
                    task = displayTask.task,
                    isSnoozed = displayTask.isSnoozed,
                    isDragging = isDragging,
                    onCrossOff = { onCrossOff(displayTask.task.id) },
                    onEditText = { newText -> onEditText(displayTask.task.id, newText) },
                    dragHandle = {
                        IconButton(
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    // Snapshot which task is being dragged
                                    draggingId = displayTask.task.id
                                },
                                onDragStopped = {
                                    // Convert display-space indices to underlying (DB) indices,
                                    // then write once to the DB.
                                    val id = draggingId
                                    val finalItems = draggedItems
                                    if (id != null && finalItems != null) {
                                        val fromUnderlying = uiState.activeTasks.indexOfFirst { it.id == id }
                                        val toDisplay = finalItems.indexOfFirst { it.task.id == id }
                                        // Map display position → underlying position.
                                        // With snooze offset O (0 < O < N):
                                        //   display[0]   → underlying[O]        (top item)
                                        //   display[1..O] → underlying[0..O-1]  (snoozed)
                                        //   display[O+1..] → underlying[O+1..]  (remaining)
                                        val O = uiState.snoozeSession.offset
                                        val N = uiState.activeTasks.size
                                        val toUnderlying = when {
                                            O <= 0 || O >= N -> toDisplay
                                            toDisplay == 0 -> O
                                            toDisplay <= O -> toDisplay - 1
                                            else -> toDisplay
                                        }
                                        if (fromUnderlying >= 0 && toUnderlying >= 0 && fromUnderlying != toUnderlying) {
                                            onReorder(fromUnderlying, toUnderlying)
                                        }
                                    }
                                    // Clear the in-progress marker; LaunchedEffect above will
                                    // clear draggedItems once the DB update arrives.
                                    draggingId = null
                                },
                            ),
                            onClick = {},
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder")
                        }
                    },
                )
            }
        }

        // Inline add row appears at the bottom of the To do section
        if (showAddRow) {
            item(key = "add_row") {
                AddTaskRow(
                    onConfirm = onAddTask,
                    onDismiss = onAddRowDismiss,
                )
            }
        }

        // ── Crossed off section ───────────────────────────────────────────────
        if (uiState.crossedOffTasks.isNotEmpty()) {
            item(key = "header_crossed") {
                CrossedSectionHeader(
                    onDeleteAll = onBulkDeleteCrossedOff,
                )
            }

            itemsIndexed(
                items = uiState.crossedOffTasks,
                key = { _, task -> "crossed_${task.id}" },
            ) { _, task ->
                CrossedTaskRow(
                    task = task,
                    onRestore = { onRestore(task.id) },
                )
            }
        }

        // Bottom padding so FAB doesn't obscure the last item
        item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Row composables ───────────────────────────────────────────────────────────

@Composable
private fun ActiveTaskRow(
    task: Task,
    isSnoozed: Boolean,
    isDragging: Boolean,
    onCrossOff: () -> Unit,
    onEditText: (String) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var draftValue by remember(task.id) { mutableStateOf(TextFieldValue(task.text)) }
    // Guard: onFocusChanged fires with isFocused=false the instant BasicTextField is
    // composed (before requestFocus() runs). Without this flag we would immediately
    // reset isEditing=false, giving the user a one-frame flicker with no visible edit.
    var editorFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { onCrossOff() })

        if (isSnoozed) {
            Text(
                text = "💤",
                modifier = Modifier.padding(end = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (isEditing) {
            BasicTextField(
                value = draftValue,
                onValueChange = { newValue ->
                    if ('\n' in newValue.text) {
                        // Return key pressed — commit and exit edit mode
                        val trimmed = newValue.text.replace("\n", "")
                        editorFocused = false
                        isEditing = false
                        if (trimmed.isNotBlank()) onEditText(trimmed)
                    } else {
                        draftValue = newValue
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            editorFocused = true
                            // Select all so typing immediately replaces the existing text
                            draftValue = draftValue.copy(
                                selection = TextRange(0, draftValue.text.length),
                            )
                        } else if (editorFocused) {
                            // Genuine focus loss (user tapped elsewhere) — commit and exit
                            editorFocused = false
                            isEditing = false
                            if (draftValue.text.isNotBlank()) onEditText(draftValue.text)
                        }
                        // isFocused=false before editorFocused=true → initial compose
                        // event, ignore it so requestFocus() below can run first.
                    },
                // Explicit color so dark-mode themes don't render invisible text
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                // maxLines=1 keeps single-line visual but lets keyboard show ↵ (not ✓)
                maxLines = 1,
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = task.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clickable { isEditing = true },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        dragHandle()
    }
}

@Composable
private fun CrossedTaskRow(
    task: Task,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = true, onCheckedChange = { onRestore() })
        Text(
            text = task.text,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun AddTaskRow(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { newValue ->
                if ('\n' in newValue) {
                    // Return key pressed — confirm or dismiss
                    val trimmed = newValue.replace("\n", "")
                    if (trimmed.isNotBlank()) onConfirm(trimmed) else onDismiss()
                } else {
                    text = newValue
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
            // Explicit color so dark-mode themes don't render invisible text
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            // maxLines=1 keeps single-line visual but lets keyboard show ↵ (not ✓)
            maxLines = 1,
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            "New task…",
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            ),
                        )
                    }
                    inner()
                }
            },
        )
        // Return arrow (↵) — matches the keyboard's natural return key
        IconButton(onClick = { if (text.isNotBlank()) onConfirm(text) else onDismiss() }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Confirm")
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ── Section headers ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
    }
}

@Composable
private fun CrossedSectionHeader(onDeleteAll: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Crossed off",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            TextButton(onClick = onDeleteAll) {
                Text("Delete all")
            }
        }
        HorizontalDivider()
    }
}
