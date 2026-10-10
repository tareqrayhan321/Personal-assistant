@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalmentor.app.presentation.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.personalmentor.app.domain.model.Connector
import com.personalmentor.app.domain.model.Project
import com.personalmentor.app.domain.model.RunMode
import com.personalmentor.app.domain.model.ScheduleRepeat
import com.personalmentor.app.domain.model.ScheduledTask
import java.time.LocalDate

private enum class EditorDialog { TIME, START, END, CONNECTORS, AGENT, PROJECT, CLOUD }

@Composable
internal fun ScheduleEditorSheet(
    initial: ScheduledTask,
    defaultModel: String,
    projects: List<Project>,
    cloud: CloudComputerUi,
    onSaveProject: (Project, (Long) -> Unit) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial.title) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var startDay by remember { mutableLongStateOf(initial.startEpochDay) }
    var neverEnds by remember { mutableStateOf(initial.endEpochDay == null) }
    var endDay by remember { mutableLongStateOf(initial.endEpochDay ?: LocalDate.now().plusMonths(1).toEpochDay()) }
    var prompt by remember { mutableStateOf(initial.prompt) }
    var skipConfirmations by remember { mutableStateOf(initial.skipConfirmations) }
    var runMode by remember { mutableStateOf(initial.runMode) }
    var connectors by remember { mutableStateOf(initial.connectors) }
    var agentModel by remember { mutableStateOf(initial.agentModel) }
    var projectId by remember { mutableStateOf(initial.projectId) }
    var cloudId by remember { mutableStateOf(initial.cloudComputerId) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    val usesStartDate = repeat != ScheduleRepeat.DAILY && repeat != ScheduleRepeat.WEEKDAYS
    val effectiveStart = if (usesStartDate) startDay else LocalDate.now().toEpochDay()
    val endOk = neverEnds || endDay >= effectiveStart
    val canSave = prompt.isNotBlank() && endOk

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    if (initial.id == 0L) "New scheduled task" else "Edit scheduled task",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Title")
                FieldCard { PlainField(title, { title = it }, "AI News Summary", singleLine = true) }

                SectionLabel("Schedule")
                FieldCard {
                    DropdownRow("Repeat", repeat.label, ScheduleRepeat.entries, { it.label }) { repeat = it }
                    if (usesStartDate) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SettingRow(if (repeat == ScheduleRepeat.ONCE) "Date" else "Starts", formatDay(startDay)) { dialog = EditorDialog.START }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow("Time", formatTime(hour, minute)) { dialog = EditorDialog.TIME }
                    if (repeat != ScheduleRepeat.ONCE) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SwitchRow("Never ends", neverEnds) { neverEnds = it }
                        if (!neverEnds) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            SettingRow("Ends on", formatDay(endDay)) { dialog = EditorDialog.END }
                        }
                    }
                }
                when {
                    !endOk -> HintText("The end date must not be before the start.", isError = true)
                    repeat == ScheduleRepeat.WEEKLY -> HintText("Repeats every ${weekdayName(startDay)}.")
                    repeat == ScheduleRepeat.MONTHLY ->
                        HintText("Repeats on day ${LocalDate.ofEpochDay(startDay).dayOfMonth} of each month (last day in shorter months).")
                }

                SectionLabel("Prompt")
                FieldCard {
                    PlainField(prompt, { prompt = it }, "Summarize AI industry news", singleLine = false, minHeight = 140)
                }

                SectionLabel("Approval requests")
                FieldCard { SwitchRow("Skip confirmations", skipConfirmations) { skipConfirmations = it } }
                HintText(
                    if (skipConfirmations) "The task runs without asking before sending or publishing."
                    else "Approval is required before sending and publishing.",
                )

                SectionLabel("Advanced settings")
                FieldCard {
                    DropdownRow("Run options", runMode.label, RunMode.entries, { it.label }) { runMode = it }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow("Connectors", connectors.joinToString(", ") { it.label }.ifEmpty { "None" }) { dialog = EditorDialog.CONNECTORS }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow("Agent", agentModel ?: defaultModel) { dialog = EditorDialog.AGENT }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow("Project", projects.firstOrNull { it.id == projectId }?.name ?: "None") { dialog = EditorDialog.PROJECT }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow("Cloud Computer", cloud.computers.firstOrNull { it.id == cloudId }?.name ?: "None") { dialog = EditorDialog.CLOUD }
                }
                Box(Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            prompt = prompt.trim(),
                            repeat = repeat,
                            hour = hour,
                            minute = minute,
                            startEpochDay = effectiveStart,
                            endEpochDay = if (neverEnds || repeat == ScheduleRepeat.ONCE) null else endDay,
                            skipConfirmations = skipConfirmations,
                            runMode = runMode,
                            connectors = connectors,
                            agentModel = agentModel?.trim()?.takeIf { it.isNotEmpty() },
                            projectId = projectId?.takeIf { id -> projects.any { it.id == id } },
                            cloudComputerId = cloudId?.takeIf { id -> cloud.computers.any { it.id == id } },
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().heightIn(min = 52.dp),
            ) { Text("Save") }
        }
    }

    when (dialog) {
        EditorDialog.TIME -> {
            val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
            AlertDialog(
                onDismissRequest = { dialog = null },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        hour = state.hour
                        minute = state.minute
                        dialog = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        EditorDialog.START -> DayPickerDialog(startDay, onPick = { startDay = it; dialog = null }, onDismiss = { dialog = null })
        EditorDialog.END -> DayPickerDialog(endDay, onPick = { endDay = it; dialog = null }, onDismiss = { dialog = null })
        EditorDialog.CONNECTORS -> ConnectorsDialog(connectors, onDone = { connectors = it; dialog = null }, onDismiss = { dialog = null })
        EditorDialog.AGENT -> AgentDialog(agentModel, defaultModel, onDone = { agentModel = it; dialog = null }, onDismiss = { dialog = null })
        EditorDialog.PROJECT -> ProjectDialog(
            projects = projects,
            selectedId = projectId,
            onSelect = { projectId = it; dialog = null },
            onSave = onSaveProject,
            onDelete = onDeleteProject,
            onDismiss = { dialog = null },
        )
        EditorDialog.CLOUD -> CloudComputerDialog(
            cloud = cloud,
            selectedId = cloudId,
            onSelect = { cloudId = it; dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun ProjectDialog(
    projects: List<Project>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onSave: (Project, (Long) -> Unit) -> Unit,
    onDelete: (Project) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Project?>(null) }
    val form = editing
    if (form == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Project") },
            text = {
                Column {
                    ProjectChoiceRow("None", selected = selectedId == null, onClick = { onSelect(null) }, onEdit = null)
                    projects.forEach { p ->
                        ProjectChoiceRow(p.name, selected = selectedId == p.id, onClick = { onSelect(p.id) }, onEdit = { editing = p })
                    }
                    TextButton(onClick = { editing = Project(name = "", instructions = "") }) { Text("New project") }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    } else {
        var name by remember(form.id) { mutableStateOf(form.name) }
        var instructions by remember(form.id) { mutableStateOf(form.instructions) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (form.id == 0L) "New project" else "Edit project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Instructions for every task in this project") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (form.id != 0L) {
                        TextButton(onClick = {
                            onDelete(form)
                            if (selectedId == form.id) onSelect(null)
                            editing = null
                        }) { Text("Delete project", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(form.copy(name = name, instructions = instructions)) { id -> onSelect(id) }
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ProjectChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (onEdit != null) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit project") }
        }
    }
}

@Composable
private fun DayPickerDialog(initialDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun ConnectorsDialog(initial: Set<Connector>, onDone: (Set<Connector>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connectors") },
        text = {
            Column {
                Connector.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (c in picked) picked - c else picked + c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c in picked, onCheckedChange = null)
                        Text(c.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Text(
                    "To-do and reminder tools are always available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AgentDialog(initial: String?, defaultModel: String, onDone: (String?) -> Unit, onDismiss: () -> Unit) {
    var model by remember { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Model for this task. Leave empty to use the model from Settings ($defaultModel).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text("Model name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(model.trim().takeIf { it.isNotEmpty() }) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

@Composable
private fun HintText(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun FieldCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean, minHeight: Int = 0) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> DropdownRow(label: String, current: String, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(label, current) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = {
                    onSelect(option)
                    open = false
                })
            }
        }
    }
}
