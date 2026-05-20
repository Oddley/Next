package com.oddley.next.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.oddley.next.domain.task.NullTask
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
    onMarkComplete: () -> Unit,
    onSnooze: () -> Unit,
    onToggleTasks: () -> Unit,
    onToggleEmitters: () -> Unit,
    onToggleCompleted: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Next") }) },
    ) { paddingValues ->
        SectionedList(
            modifier = Modifier
                .padding(paddingValues)
                .imePadding(),
            uiState = uiState,
            onAddTask = onAddTask,
            onCrossOff = onCrossOff,
            onRestore = onRestore,
            onEditText = onEditText,
            onReorder = onReorder,
            onBulkDeleteCrossedOff = onBulkDeleteCrossedOff,
            onMarkComplete = onMarkComplete,
            onSnooze = onSnooze,
            onToggleTasks = onToggleTasks,
            onToggleEmitters = onToggleEmitters,
            onToggleCompleted = onToggleCompleted,
        )
    }
}

@Composable
private fun SectionedList(
    modifier: Modifier = Modifier,
    uiState: ListUiState,
    onAddTask: (String) -> Unit,
    onCrossOff: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onEditText: (Long, String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onBulkDeleteCrossedOff: () -> Unit,
    onMarkComplete: () -> Unit,
    onSnooze: () -> Unit,
    onToggleTasks: () -> Unit,
    onToggleEmitters: () -> Unit,
    onToggleCompleted: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var showAddRow by remember { mutableStateOf(false) }

    // Local drag state — write to DB only on drag stop to prevent lurching.
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggedItems by remember { mutableStateOf<List<Task>?>(null) }
    val displayedActiveTasks = draggedItems ?: uiState.activeTasks

    LaunchedEffect(uiState.activeTasks) {
        if (draggingId == null) draggedItems = null
    }

    // NEXT section (index 0) + Tasks header (index 1) = headerOffset 2
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val headerOffset = 2
        val fromIdx = from.index - headerOffset
        val toIdx = to.index - headerOffset
        val current = (draggedItems ?: uiState.activeTasks).toMutableList()
        if (fromIdx in current.indices && toIdx in current.indices) {
            current.add(toIdx, current.removeAt(fromIdx))
            draggedItems = current
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
    ) {

        // ── NEXT ─────────────────────────────────────────────────────────────
        item(key = "next_section") {
            NextSection(
                nextTask = uiState.nextTask,
                onMarkComplete = onMarkComplete,
                onSnooze = onSnooze,
            )
        }

        // ── Tasks ─────────────────────────────────────────────────────────────
        if (uiState.activeTasks.isEmpty()) {
            item(key = "tasks_empty") {
                EmptySectionLabel(label = "No Tasks")
            }
        } else {
            item(key = "tasks_header") {
                CollapsibleSectionHeader(
                    title = "Tasks",
                    count = uiState.activeTasks.size,
                    expanded = uiState.tasksExpanded,
                    onToggle = onToggleTasks,
                )
            }

            if (uiState.tasksExpanded) {
                itemsIndexed(
                    items = displayedActiveTasks,
                    key = { _, task -> task.id },
                ) { _, task ->
                    ReorderableItem(reorderState, key = task.id) { isDragging ->
                        ActiveTaskRow(
                            task = task,
                            isNextTask = uiState.nextTask != NullTask &&
                                    task.id == uiState.nextTask.id,
                            isDragging = isDragging,
                            onCrossOff = { onCrossOff(task.id) },
                            onEditText = { newText -> onEditText(task.id, newText) },
                            dragHandle = {
                                IconButton(
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { draggingId = task.id },
                                        onDragStopped = {
                                            val id = draggingId
                                            val finalItems = draggedItems
                                            if (id != null && finalItems != null) {
                                                val fromIndex =
                                                    uiState.activeTasks.indexOfFirst { it.id == id }
                                                val toIndex =
                                                    finalItems.indexOfFirst { it.id == id }
                                                if (fromIndex >= 0 && toIndex >= 0 &&
                                                    fromIndex != toIndex
                                                ) {
                                                    onReorder(fromIndex, toIndex)
                                                }
                                            }
                                            draggingId = null
                                        },
                                    ),
                                    onClick = {},
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        // "Add New Task" footer — always visible so you can add even when collapsed
        if (showAddRow) {
            item(key = "add_task_row") {
                AddTaskRow(
                    onConfirm = { text ->
                        onAddTask(text)
                        showAddRow = false
                    },
                    onDismiss = { showAddRow = false },
                )
            }
        } else {
            item(key = "add_task_footer") {
                SectionFooterAction(
                    label = "+ Add New Task",
                    onClick = { showAddRow = true },
                )
            }
        }

        // ── Scheduled Tasks ───────────────────────────────────────────────────
        // Phase 4: emitters not yet implemented — always show empty state.
        // Phase 5 will replace this with a real emitter list + collapse toggle.
        item(key = "scheduled_empty") {
            EmptySectionLabel(label = "No Scheduled Tasks")
        }
        item(key = "schedule_new_task_footer") {
            SectionFooterAction(
                label = "+ Schedule New Task",
                onClick = { /* Phase 5: open emitter creation dialog */ },
            )
        }

        // ── Completed ─────────────────────────────────────────────────────────
        if (uiState.crossedOffTasks.isEmpty()) {
            item(key = "completed_empty") {
                EmptySectionLabel(label = "No Completed Tasks")
            }
        } else {
            item(key = "completed_header") {
                CollapsibleSectionHeader(
                    title = "Completed",
                    count = uiState.crossedOffTasks.size,
                    expanded = uiState.completedExpanded,
                    onToggle = onToggleCompleted,
                )
            }
            if (uiState.completedExpanded) {
                itemsIndexed(
                    items = uiState.crossedOffTasks,
                    key = { _, task -> "crossed_${task.id}" },
                ) { _, task ->
                    CrossedTaskRow(
                        task = task,
                        onRestore = { onRestore(task.id) },
                    )
                }
                item(key = "delete_all_footer") {
                    SectionFooterAction(
                        label = "Delete All Completed",
                        onClick = onBulkDeleteCrossedOff,
                        isDestructive = true,
                    )
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
    }
}

// ── NEXT section ──────────────────────────────────────────────────────────────

@Composable
private fun NextSection(
    nextTask: Task,
    onMarkComplete: () -> Unit,
    onSnooze: () -> Unit,
) {
    val isEmpty = nextTask == NullTask
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isEmpty) "All caught up 🎉" else nextTask.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (!isEmpty) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onMarkComplete) {
                        Text("Mark complete")
                    }
                    OutlinedButton(onClick = onSnooze) {
                        Text("Snooze")
                    }
                }
            }
        }
    }
}

// ── Section chrome ────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!expanded) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun EmptySectionLabel(label: String) {
    Column {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        HorizontalDivider()
    }
}

@Composable
private fun SectionFooterAction(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Task rows ─────────────────────────────────────────────────────────────────

@Composable
private fun ActiveTaskRow(
    task: Task,
    isNextTask: Boolean,
    isDragging: Boolean,
    onCrossOff: () -> Unit,
    onEditText: (String) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val now = System.currentTimeMillis()
    val isSnoozed = task.snoozedUntil != null && task.snoozedUntil > now

    var isEditing by remember { mutableStateOf(false) }
    var draftValue by remember(task.id) { mutableStateOf(TextFieldValue(task.text)) }
    var editorFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNextTask) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) else Modifier
            )
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
                            draftValue = draftValue.copy(
                                selection = TextRange(0, draftValue.text.length),
                            )
                        } else if (editorFocused) {
                            editorFocused = false
                            isEditing = false
                            if (draftValue.text.isNotBlank()) onEditText(draftValue.text)
                        }
                    },
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
private fun CrossedTaskRow(task: Task, onRestore: () -> Unit) {
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

// ── Add task ──────────────────────────────────────────────────────────────────

@Composable
private fun AddTaskRow(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
        IconButton(onClick = { if (text.isNotBlank()) onConfirm(text) else onDismiss() }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Confirm")
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
