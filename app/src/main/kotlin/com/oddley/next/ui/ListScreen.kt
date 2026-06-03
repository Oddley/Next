package com.oddley.next.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.oddley.next.BuildConfig
import com.oddley.next.domain.emitter.TaskEmitter
import com.oddley.next.domain.task.NullTask
import com.oddley.next.domain.task.Task
import com.oddley.next.util.AppLogger
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ── Recurrence model ──────────────────────────────────────────────────────────

private enum class RecurrenceMode(val label: String) {
    ONE_TIME("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom..."),
}

private enum class EndCondition(val label: String) {
    FOREVER("Forever"),
    AFTER_COUNT("After N times"),
    UNTIL_DATE("Until date"),
}

/** Unit options for the Custom recurrence interval. */
private enum class CustomUnit(val label: String) {
    HOURS("Hours"),
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
}

/** How a monthly Custom recurrence anchors to the calendar. */
private enum class MonthlyOption {
    SAME_DAY,    // fires on the same date each month (e.g. the 15th)
    DAY_OF_WEEK, // fires on e.g. the "2nd Tuesday"
}

private val WEEKDAY_CODES  = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private val WEEKDAY_LABELS = listOf("M",  "T",  "W",  "T",  "F",  "Sa", "Su")
private val DAY_NAMES      = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
private val ORDINALS       = listOf(1, 2, 3, 4, -1)
private val ORDINAL_LABELS = listOf("1st", "2nd", "3rd", "4th", "Last")

/** Extracts the INTERVAL= value from an RRULE string; returns 1 when absent. */
private fun rruleInterval(rrule: String): Int =
    Regex("INTERVAL=(\\d+)").find(rrule)?.groupValues?.get(1)?.toIntOrNull() ?: 1

/** Builds an RFC 5545 RRULE string from UI selections. */
private fun buildRrule(
    mode: RecurrenceMode,
    selectedDays: Set<Int>,             // indices into WEEKDAY_CODES (used by WEEKLY / CUSTOM+Weeks)
    endCondition: EndCondition,
    count: Int,
    until: Long?,                       // epoch ms
    // ── Custom mode params (ignored for non-CUSTOM modes) ──────────────────
    customUnit: CustomUnit = CustomUnit.DAYS,
    customInterval: Int = 2,
    monthlyOption: MonthlyOption = MonthlyOption.SAME_DAY,
    monthlyOrdinal: Int = 1,            // 1–4 or -1 (Last)
    monthlyDayIndex: Int = 0,           // index into WEEKDAY_CODES
): String {
    // Shared end-condition appender
    fun StringBuilder.appendEnd() {
        when (endCondition) {
            EndCondition.FOREVER -> {}
            EndCondition.AFTER_COUNT -> append(";COUNT=${count.coerceAtLeast(1)}")
            EndCondition.UNTIL_DATE -> {
                if (until != null) {
                    val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    append(";UNTIL=${sdf.format(Date(until))}T000000Z")
                }
            }
        }
    }

    if (mode == RecurrenceMode.ONE_TIME) return "FREQ=DAILY;COUNT=1"

    if (mode == RecurrenceMode.CUSTOM) {
        val freq = when (customUnit) {
            CustomUnit.HOURS  -> "HOURLY"
            CustomUnit.DAYS   -> "DAILY"
            CustomUnit.WEEKS  -> "WEEKLY"
            CustomUnit.MONTHS -> "MONTHLY"
        }
        val sb = StringBuilder("FREQ=$freq;INTERVAL=${customInterval.coerceAtLeast(1)}")
        when (customUnit) {
            CustomUnit.WEEKS ->
                if (selectedDays.isNotEmpty())
                    sb.append(";BYDAY=${selectedDays.sorted().joinToString(",") { WEEKDAY_CODES[it] }}")
            CustomUnit.MONTHS ->
                if (monthlyOption == MonthlyOption.DAY_OF_WEEK) {
                    val ord = if (monthlyOrdinal == -1) "-1" else monthlyOrdinal.toString()
                    sb.append(";BYDAY=${ord}${WEEKDAY_CODES[monthlyDayIndex]}")
                }
            else -> {}
        }
        sb.appendEnd()
        return sb.toString()
    }

    // Quick-pick modes (DAILY / WEEKLY / MONTHLY)
    val freq = when (mode) {
        RecurrenceMode.DAILY   -> "DAILY"
        RecurrenceMode.WEEKLY  -> "WEEKLY"
        RecurrenceMode.MONTHLY -> "MONTHLY"
        else -> "DAILY" // unreachable
    }
    val sb = StringBuilder("FREQ=$freq")
    if (mode == RecurrenceMode.WEEKLY && selectedDays.isNotEmpty()) {
        sb.append(";BYDAY=${selectedDays.sorted().joinToString(",") { WEEKDAY_CODES[it] }}")
    }
    sb.appendEnd()
    return sb.toString()
}

/** Human-readable summary of an emitter's schedule. */
private fun scheduleDescription(emitter: TaskEmitter): String {
    val nextMs = emitter.nextEmission ?: return "Completed"
    val rrule  = emitter.rrule
    val n      = rruleInterval(rrule)
    val freq = when {
        rrule.contains("COUNT=1")      -> "One-time"
        rrule.contains("FREQ=HOURLY")  -> if (n == 1) "Hourly"   else "Every $n hours"
        rrule.contains("FREQ=DAILY")   -> if (n == 1) "Daily"    else "Every $n days"
        rrule.contains("FREQ=WEEKLY")  -> if (n == 1) "Weekly"   else "Every $n weeks"
        rrule.contains("FREQ=MONTHLY") -> if (n == 1) "Monthly"  else "Every $n months"
        else -> ""
    }
    val nextStr = formatNextEmission(nextMs)
    return if (freq.isEmpty()) nextStr else "$freq · $nextStr"
}

private fun formatNextEmission(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return when {
        epochMs <= now -> "Now"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            "Today at ${timeFmt.format(Date(epochMs))}"
        cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) ->
            "Tomorrow at ${timeFmt.format(Date(epochMs))}"
        else -> "${dateFmt.format(Date(epochMs))} at ${timeFmt.format(Date(epochMs))}"
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

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
    onToggleTasks: () -> Unit,
    onToggleEmitters: () -> Unit,
    onToggleCompleted: () -> Unit,
    onAddEmitter: (label: String, rrule: String, dtStart: Long) -> Unit,
    onUpdateEmitter: (TaskEmitter) -> Unit,
    onDeleteEmitter: (Long) -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Next")
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Report Issue") },
                                onClick = {
                                    showMenu = false
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Oddley/Next/issues/new"))
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Submit Log") },
                                onClick = {
                                    showMenu = false
                                    val log = AppLogger.readRecent(context)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Next App Debug Log (v${BuildConfig.VERSION_NAME})")
                                        putExtra(Intent.EXTRA_TEXT, log)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share log"))
                                },
                            )
                        }
                    }
                },
            )
        },
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
            onToggleTasks = onToggleTasks,
            onToggleEmitters = onToggleEmitters,
            onToggleCompleted = onToggleCompleted,
            onAddEmitter = onAddEmitter,
            onUpdateEmitter = onUpdateEmitter,
            onDeleteEmitter = onDeleteEmitter,
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
    onToggleTasks: () -> Unit,
    onToggleEmitters: () -> Unit,
    onToggleCompleted: () -> Unit,
    onAddEmitter: (label: String, rrule: String, dtStart: Long) -> Unit,
    onUpdateEmitter: (TaskEmitter) -> Unit,
    onDeleteEmitter: (Long) -> Unit,
) {
    val context = LocalContext.current
    val exactAlarmGranted = remember {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.canScheduleExactAlarms()
    }
    val lazyListState = rememberLazyListState()
    var showAddRow by remember { mutableStateOf(false) }

    // Emitter dialog state
    var showEmitterDialog by rememberSaveable { mutableStateOf(false) }
    var editingEmitter by remember { mutableStateOf<TaskEmitter?>(null) }

    // Local drag state — write to DB only on drag stop to prevent lurching.
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggedItems by remember { mutableStateOf<List<Task>?>(null) }
    val displayedActiveTasks = draggedItems ?: uiState.activeTasks

    LaunchedEffect(uiState.activeTasks) {
        if (draggingId == null) draggedItems = null
    }

    // Tasks header (index 0) = headerOffset 1
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val headerOffset = 1
        val fromIdx = from.index - headerOffset
        val toIdx = to.index - headerOffset
        val current = (draggedItems ?: uiState.activeTasks).toMutableList()
        if (fromIdx in current.indices && toIdx in current.indices) {
            current.add(toIdx, current.removeAt(fromIdx))
            draggedItems = current
        }
    }

    // Emitter dialog
    if (showEmitterDialog) {
        EmitterDialog(
            emitter = editingEmitter,
            onConfirm = { label, rrule, dtStart ->
                val editing = editingEmitter
                if (editing == null) {
                    onAddEmitter(label, rrule, dtStart)
                } else {
                    onUpdateEmitter(
                        editing.copy(
                            label = label,
                            rrule = rrule,
                            dtStart = dtStart,
                            // recompute nextEmission — EmitterRepository handles this
                            // when the caller calls updateEmitter; pass current for now
                            nextEmission = editing.nextEmission,
                        )
                    )
                }
                showEmitterDialog = false
                editingEmitter = null
            },
            onDismiss = {
                showEmitterDialog = false
                editingEmitter = null
            },
            onDelete = if (editingEmitter != null) {
                {
                    onDeleteEmitter(editingEmitter!!.id)
                    showEmitterDialog = false
                    editingEmitter = null
                }
            } else null,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
    ) {

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

        // ── Alarm permission warning ─────────────────────────────────────────
        if (uiState.emitters.isNotEmpty() && !exactAlarmGranted) {
            item(key = "alarm_permission_warning") {
                AlarmPermissionBanner(context = context)
            }
        }

        // ── Scheduled Tasks ───────────────────────────────────────────────────
        if (uiState.emitters.isEmpty()) {
            item(key = "scheduled_empty") {
                EmptySectionLabel(label = "No Scheduled Tasks")
            }
        } else {
            item(key = "scheduled_header") {
                CollapsibleSectionHeader(
                    title = "Scheduled Tasks",
                    count = uiState.emitters.size,
                    expanded = uiState.emittersExpanded,
                    onToggle = onToggleEmitters,
                )
            }
            if (uiState.emittersExpanded) {
                itemsIndexed(
                    items = uiState.emitters,
                    key = { _, emitter -> "emitter_${emitter.id}" },
                ) { _, emitter ->
                    EmitterRow(
                        emitter = emitter,
                        onEdit = {
                            editingEmitter = emitter
                            showEmitterDialog = true
                        },
                    )
                }
            }
        }

        item(key = "schedule_new_task_footer") {
            SectionFooterAction(
                label = "+ Schedule New Task",
                onClick = {
                    editingEmitter = null
                    showEmitterDialog = true
                },
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

// ── Section chrome ────────────────────────────────────────────────────────────

@Composable
private fun AlarmPermissionBanner(context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Scheduled tasks need Alarms permission",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Tap to grant — without it, tasks may fire late",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
            )
        }
    }
}

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

// ── Emitter row ───────────────────────────────────────────────────────────────

@Composable
private fun EmitterRow(emitter: TaskEmitter, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emitter.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = scheduleDescription(emitter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
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

// ── Emitter dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EmitterDialog(
    emitter: TaskEmitter?,            // null = create mode
    onConfirm: (label: String, rrule: String, dtStart: Long) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    // ── Initial state from existing emitter (edit) or defaults (create) ────────
    val nowCal = remember { Calendar.getInstance() }
    // Round up to next whole hour as a sensible default
    nowCal.apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    var label by rememberSaveable { mutableStateOf(emitter?.label ?: "") }

    // Date portion (epoch ms of midnight UTC of the chosen day, from DatePicker)
    var selectedDateMs by rememberSaveable {
        mutableLongStateOf(
            emitter?.dtStart
                ?: run {
                    // Midnight UTC of today
                    val c = Calendar.getInstance()
                    c.set(Calendar.HOUR_OF_DAY, 0)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    c.timeInMillis
                }
        )
    }

    // Time portion
    var selectedHour by rememberSaveable {
        mutableIntStateOf(emitter?.let {
            Calendar.getInstance().apply { timeInMillis = it.dtStart }
                .get(Calendar.HOUR_OF_DAY)
        } ?: nowCal.get(Calendar.HOUR_OF_DAY))
    }
    var selectedMinute by rememberSaveable {
        mutableIntStateOf(emitter?.let {
            Calendar.getInstance().apply { timeInMillis = it.dtStart }
                .get(Calendar.MINUTE)
        } ?: 0)
    }

    var recurrenceMode by rememberSaveable {
        mutableStateOf(
            when {
                emitter == null -> RecurrenceMode.ONE_TIME
                emitter.rrule.contains("COUNT=1") -> RecurrenceMode.ONE_TIME
                emitter.rrule.contains("FREQ=DAILY")   && rruleInterval(emitter.rrule) == 1 -> RecurrenceMode.DAILY
                emitter.rrule.contains("FREQ=WEEKLY")  && rruleInterval(emitter.rrule) == 1 -> RecurrenceMode.WEEKLY
                emitter.rrule.contains("FREQ=MONTHLY") && rruleInterval(emitter.rrule) == 1 -> RecurrenceMode.MONTHLY
                else -> RecurrenceMode.CUSTOM
            }
        )
    }

    // Weekly / Custom-weeks day selection (indices into WEEKDAY_CODES)
    var selectedDays by rememberSaveable {
        mutableStateOf(
            if (emitter != null && emitter.rrule.contains("BYDAY=")) {
                // Weekly BYDAY is comma-separated codes with no ordinal prefix (e.g. "MO,WE").
                // Monthly DAY_OF_WEEK BYDAY has an ordinal prefix (e.g. "2TU") — skip those.
                val byday = emitter.rrule.substringAfter("BYDAY=").substringBefore(";")
                byday.split(",")
                    .filter { it.first().isLetter() }   // skip ordinal entries like "2TU"
                    .mapNotNull { code -> WEEKDAY_CODES.indexOf(code).takeIf { it >= 0 } }
                    .toSet()
            } else setOf()
        )
    }

    // ── Custom recurrence state ────────────────────────────────────────────────
    var customUnit by rememberSaveable {
        mutableStateOf(
            when {
                emitter == null -> CustomUnit.DAYS
                emitter.rrule.contains("FREQ=HOURLY")  -> CustomUnit.HOURS
                emitter.rrule.contains("FREQ=WEEKLY")  -> CustomUnit.WEEKS
                emitter.rrule.contains("FREQ=MONTHLY") -> CustomUnit.MONTHS
                else -> CustomUnit.DAYS
            }
        )
    }
    var customIntervalText by rememberSaveable {
        mutableStateOf(
            emitter?.rrule?.let { r -> rruleInterval(r).takeIf { it > 1 }?.toString() } ?: "2"
        )
    }
    // Monthly sub-option: same calendar date vs. day-of-week-of-month
    val initialMonthlyByday = emitter?.rrule?.let { r ->
        Regex("BYDAY=(-?\\d+)([A-Z]{2})").find(r)
    }
    var monthlyOption by rememberSaveable {
        mutableStateOf(
            if (initialMonthlyByday != null) MonthlyOption.DAY_OF_WEEK else MonthlyOption.SAME_DAY
        )
    }
    var monthlyOrdinal by rememberSaveable {
        mutableIntStateOf(
            initialMonthlyByday?.groupValues?.get(1)?.toIntOrNull() ?: 1
        )
    }
    var monthlyDayIndex by rememberSaveable {
        mutableIntStateOf(
            initialMonthlyByday?.groupValues?.get(2)
                ?.let { WEEKDAY_CODES.indexOf(it).coerceAtLeast(0) } ?: 0
        )
    }

    var endCondition by rememberSaveable { mutableStateOf(EndCondition.FOREVER) }
    var countText by rememberSaveable { mutableStateOf("4") }
    var untilDateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }

    // Sub-picker visibility
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showUntilDatePicker by remember { mutableStateOf(false) }
    var showOrdinalPicker by remember { mutableStateOf(false) }
    var showMonthlyDayPicker by remember { mutableStateOf(false) }

    // ── DatePicker for first occurrence ───────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMs,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── TimePicker for first occurrence ───────────────────────────────────────
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    // ── Until date picker ─────────────────────────────────────────────────────
    if (showUntilDatePicker) {
        val untilPickerState = rememberDatePickerState(
            initialSelectedDateMillis = untilDateMs,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis > selectedDateMs
            }
        )
        DatePickerDialog(
            onDismissRequest = { showUntilDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    untilPickerState.selectedDateMillis?.let { untilDateMs = it }
                    showUntilDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showUntilDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = untilPickerState)
        }
    }

    // ── Ordinal picker (1st / 2nd / 3rd / 4th / Last) ────────────────────────
    if (showOrdinalPicker) {
        AlertDialog(
            onDismissRequest = { showOrdinalPicker = false },
            title = { Text("Repeat on the…") },
            text = {
                Column {
                    ORDINALS.forEachIndexed { i, ord ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { monthlyOrdinal = ord; showOrdinalPicker = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = monthlyOrdinal == ord,
                                onClick = { monthlyOrdinal = ord; showOrdinalPicker = false },
                            )
                            Text(ORDINAL_LABELS[i], style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOrdinalPicker = false }) { Text("Cancel") }
            },
        )
    }

    // ── Monthly day-of-week picker (Monday … Sunday) ──────────────────────────
    if (showMonthlyDayPicker) {
        AlertDialog(
            onDismissRequest = { showMonthlyDayPicker = false },
            title = { Text("Day of week") },
            text = {
                Column {
                    DAY_NAMES.forEachIndexed { i, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { monthlyDayIndex = i; showMonthlyDayPicker = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = monthlyDayIndex == i,
                                onClick = { monthlyDayIndex = i; showMonthlyDayPicker = false },
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthlyDayPicker = false }) { Text("Cancel") }
            },
        )
    }

    // ── Main dialog ───────────────────────────────────────────────────────────
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (emitter == null) "Schedule Task" else "Edit Schedule")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Task name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Date + Time row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        // DatePicker returns midnight UTC — read in UTC so US users
                        // see the correct date (not "yesterday").
                        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        Text(dateFmt.format(Date(selectedDateMs)))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val timeCal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                        }
                        Text(timeFmt.format(timeCal.time))
                    }
                }

                // Recurrence
                Text(
                    "Repeat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column {
                    RecurrenceMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { recurrenceMode = mode },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = recurrenceMode == mode,
                                onClick = { recurrenceMode = mode },
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Weekly day selector (quick-pick mode)
                if (recurrenceMode == RecurrenceMode.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAY_LABELS.forEachIndexed { index, dayLabel ->
                            FilterChip(
                                selected = index in selectedDays,
                                onClick = {
                                    selectedDays = if (index in selectedDays) {
                                        selectedDays - index
                                    } else {
                                        selectedDays + index
                                    }
                                },
                                label = { Text(dayLabel) },
                            )
                        }
                    }
                }

                // ── Custom recurrence options ─────────────────────────────────
                if (recurrenceMode == RecurrenceMode.CUSTOM) {
                    // "Every [N] [unit chips]" row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Every", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = customIntervalText,
                            onValueChange = { v ->
                                if (v.all { it.isDigit() } && v.length <= 3) customIntervalText = v
                            },
                            modifier = Modifier.width(64.dp),
                            singleLine = true,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CustomUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = customUnit == unit,
                                onClick = { customUnit = unit },
                                label = { Text(unit.label) },
                            )
                        }
                    }

                    // Weeks: day-of-week chips (reuse selectedDays)
                    if (customUnit == CustomUnit.WEEKS) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WEEKDAY_LABELS.forEachIndexed { index, dayLabel ->
                                FilterChip(
                                    selected = index in selectedDays,
                                    onClick = {
                                        selectedDays = if (index in selectedDays)
                                            selectedDays - index else selectedDays + index
                                    },
                                    label = { Text(dayLabel) },
                                )
                            }
                        }
                    }

                    // Months: same date vs. day-of-week-of-month
                    if (customUnit == CustomUnit.MONTHS) {
                        Text(
                            "Repeats on",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val dayOfMonth = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            .apply { timeInMillis = selectedDateMs }
                            .get(Calendar.DAY_OF_MONTH)
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { monthlyOption = MonthlyOption.SAME_DAY },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = monthlyOption == MonthlyOption.SAME_DAY,
                                    onClick = { monthlyOption = MonthlyOption.SAME_DAY },
                                )
                                Text(
                                    "Day $dayOfMonth of the month",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { monthlyOption = MonthlyOption.DAY_OF_WEEK },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = monthlyOption == MonthlyOption.DAY_OF_WEEK,
                                    onClick = { monthlyOption = MonthlyOption.DAY_OF_WEEK },
                                )
                                Text("Day of week", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (monthlyOption == MonthlyOption.DAY_OF_WEEK) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showOrdinalPicker = true }) {
                                    Text(ORDINAL_LABELS[ORDINALS.indexOf(monthlyOrdinal).coerceAtLeast(0)])
                                }
                                OutlinedButton(onClick = { showMonthlyDayPicker = true }) {
                                    Text(DAY_NAMES[monthlyDayIndex])
                                }
                            }
                        }
                    }
                }

                // End condition (only for repeating modes)
                if (recurrenceMode != RecurrenceMode.ONE_TIME) {
                    Text(
                        "Ends",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        EndCondition.entries.forEach { cond ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { endCondition = cond },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = endCondition == cond,
                                    onClick = { endCondition = cond },
                                )
                                Text(cond.label, style = MaterialTheme.typography.bodyMedium)
                                if (cond == EndCondition.AFTER_COUNT && endCondition == cond) {
                                    Spacer(Modifier.width(8.dp))
                                    BasicTextField(
                                        value = countText,
                                        onValueChange = { v ->
                                            if (v.all { it.isDigit() } && v.length <= 3) countText = v
                                        },
                                        modifier = Modifier.width(40.dp),
                                        textStyle = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                        maxLines = 1,
                                    )
                                    Text(" times", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (endCondition == EndCondition.UNTIL_DATE) {
                        OutlinedButton(
                            onClick = { showUntilDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            Text("Until ${dateFmt.format(Date(untilDateMs))}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete emitter",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = {
                        if (label.isBlank()) return@TextButton
                        // Build dtStart from selected date (UTC midnight) + local time.
                        // DatePicker returns midnight UTC — extract year/month/day in UTC
                        // so US users (UTC-) don't land on the day before.
                        val dtStart = run {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedDateMs
                            }
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, selectedHour)
                                set(Calendar.MINUTE, selectedMinute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }
                        val rrule = buildRrule(
                            mode = recurrenceMode,
                            selectedDays = selectedDays,
                            endCondition = if (recurrenceMode == RecurrenceMode.ONE_TIME)
                                EndCondition.FOREVER else endCondition,
                            count = countText.toIntOrNull() ?: 1,
                            until = untilDateMs,
                            customUnit = customUnit,
                            customInterval = customIntervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            monthlyOption = monthlyOption,
                            monthlyOrdinal = monthlyOrdinal,
                            monthlyDayIndex = monthlyDayIndex,
                        )
                        onConfirm(label.trim(), rrule, dtStart)
                    },
                    enabled = label.isNotBlank(),
                ) { Text("Save") }
            }
        },
    )
}
