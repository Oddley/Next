package com.oddley.next.ui

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.text.input.ImeAction
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
) {
    var showAddRow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Next") })
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
) {
    // Reorderable state — tied to the active task list
    val reorderState = rememberReorderableLazyListState(
        lazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
        onMove = { from, to ->
            // Adjust indices: LazyColumn keys include section headers, so we
            // calculate the offset from the "To do" header item.
            val headerOffset = 1 // "To do" header is index 0
            onReorder(from.index - headerOffset, to.index - headerOffset)
        },
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = reorderState.lazyListState,
    ) {
        // ── To do section ─────────────────────────────────────────────────────
        item(key = "header_todo") {
            SectionHeader(title = "To do")
        }

        itemsIndexed(
            items = uiState.activeTasks,
            key = { _, task -> task.id },
        ) { _, task ->
            ReorderableItem(reorderState, key = task.id) { isDragging ->
                ActiveTaskRow(
                    task = task,
                    isDragging = isDragging,
                    onCrossOff = { onCrossOff(task.id) },
                    onEditText = { newText -> onEditText(task.id, newText) },
                    dragHandle = {
                        IconButton(
                            modifier = Modifier.draggableHandle(),
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
    isDragging: Boolean,
    onCrossOff: () -> Unit,
    onEditText: (String) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var draftText by remember(task.id) { mutableStateOf(task.text) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { onCrossOff() })

        if (isEditing) {
            BasicTextField(
                value = draftText,
                onValueChange = { draftText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = LocalTextStyle.current,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onEditText(draftText)
                    isEditing = false
                }),
                singleLine = true,
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = task.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
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
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
            textStyle = LocalTextStyle.current,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) onConfirm(text) else onDismiss()
            }),
            singleLine = true,
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
            Icon(Icons.Default.Check, contentDescription = "Confirm")
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
