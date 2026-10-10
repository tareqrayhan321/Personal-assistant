@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalmentor.app.presentation.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmentor.app.domain.model.Project
import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.model.ScheduledTask
import com.personalmentor.app.domain.model.TaskRun

private const val TAB_RUNS = 0
private const val TAB_SCHEDULED = 1

/** Body of Task Mode: Runs / Scheduled tabs plus the editor sheet. */
@Composable
fun ScheduledTasksScreen(
    newScheduleRequested: Boolean,
    onNewScheduleConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledTasksViewModel = hiltViewModel(),
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val computers by viewModel.computers.collectAsStateWithLifecycle()
    ScheduledTasksContent(
        schedules = schedules,
        projects = projects,
        cloud = CloudComputerUi(computers, viewModel::onSaveComputer, viewModel::onDeleteComputer, viewModel::onTestComputer),
        onSaveProject = viewModel::onSaveProject,
        onDeleteProject = viewModel::onDeleteProject,
        runs = runs,
        defaultModel = viewModel.defaultModel,
        newScheduleRequested = newScheduleRequested,
        onNewScheduleConsumed = onNewScheduleConsumed,
        onSave = viewModel::onSave,
        onToggle = viewModel::onToggle,
        onDelete = viewModel::onDelete,
        onRunNow = viewModel::onRunNow,
        onDeleteRun = viewModel::onDeleteRun,
        modifier = modifier,
    )
}

@Composable
internal fun ScheduledTasksContent(
    schedules: List<ScheduledTask>,
    projects: List<Project>,
    cloud: CloudComputerUi,
    onSaveProject: (Project, (Long) -> Unit) -> Unit,
    onDeleteProject: (Project) -> Unit,
    runs: List<TaskRun>,
    defaultModel: String,
    newScheduleRequested: Boolean,
    onNewScheduleConsumed: () -> Unit,
    onSave: (ScheduledTask) -> Unit,
    onToggle: (ScheduledTask, Boolean) -> Unit,
    onDelete: (ScheduledTask) -> Unit,
    onRunNow: (ScheduledTask) -> Unit,
    onDeleteRun: (TaskRun) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_SCHEDULED) }
    var editor by remember { mutableStateOf<ScheduledTask?>(null) }
    var deleting by remember { mutableStateOf<ScheduledTask?>(null) }
    var viewingRunId by remember { mutableStateOf<Long?>(null) }
    val startNew = { editor = ScheduledTask(title = "", prompt = "") }

    LaunchedEffect(newScheduleRequested) {
        if (newScheduleRequested) {
            tab = TAB_SCHEDULED
            startNew()
            onNewScheduleConsumed()
        }
    }

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Runs", "Scheduled").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        if (tab == TAB_RUNS) {
            if (runs.isEmpty()) {
                EmptyTab(text = "No runs yet", action = null)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(runs, key = { it.id }) { RunCard(it, onClick = { viewingRunId = it.id }, onDelete = { onDeleteRun(it) }) }
                }
            }
        } else {
            if (schedules.isEmpty()) {
                EmptyTab(text = "No scheduled tasks yet", action = startNew)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(schedules, key = { it.id }) { task ->
                        ScheduleCard(
                            task = task,
                            onClick = { editor = task },
                            onToggle = { onToggle(task, it) },
                            onRun = {
                                onRunNow(task)
                                tab = TAB_RUNS
                            },
                            onDelete = { deleting = task },
                        )
                    }
                }
            }
        }
    }

    editor?.let { initial ->
        ScheduleEditorSheet(
            initial = initial,
            defaultModel = defaultModel,
            projects = projects,
            cloud = cloud,
            onSaveProject = onSaveProject,
            onDeleteProject = onDeleteProject,
            onDismiss = { editor = null },
            onSave = {
                onSave(it)
                editor = null
            },
        )
    }

    // Looked up by id so an open run updates live while it finishes.
    runs.firstOrNull { it.id == viewingRunId }?.let { run ->
        RunDetailSheet(
            run = run,
            onDismiss = { viewingRunId = null },
            onDelete = {
                onDeleteRun(run)
                viewingRunId = null
            },
        )
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete schedule?") },
            text = { Text("\"${task.title}\" and its run history will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(task)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyTab(text: String, action: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                FilledTonalButton(onClick = action) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("New schedule", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    task: ScheduledTask,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(scheduleSummary(task), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                task.nextRunAt?.takeIf { task.enabled }?.let {
                    Text("Next run: ${formatMillis(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    task.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = task.enabled, onCheckedChange = onToggle, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, contentDescription = "Run now") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete schedule") }
        }
    }
}

@Composable
private fun RunCard(run: TaskRun, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(run.taskTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${run.status.label} · ${formatMillis(run.startedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (run.status == RunStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val preview = run.error?.takeIf { run.status == RunStatus.FAILED } ?: run.output
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete run") }
        }
    }
}
