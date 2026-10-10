package com.personalmentor.app.presentation.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.personalmentor.app.domain.computer.CloudComputerUrl
import com.personalmentor.app.domain.model.CloudComputer

/** Pick the cloud computer of a task, or create / edit / delete one (name, https URL, access token). */
@Composable
internal fun CloudComputerDialog(
    cloud: CloudComputerUi,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CloudComputer?>(null) }
    val form = editing
    if (form == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cloud Computer") },
            text = {
                Column {
                    ProjectChoiceRow("None", selected = selectedId == null, onClick = { onSelect(null) }, onEdit = null)
                    cloud.computers.forEach { c ->
                        ProjectChoiceRow(c.name, selected = selectedId == c.id, onClick = { onSelect(c.id) }, onEdit = { editing = c })
                    }
                    TextButton(onClick = { editing = CloudComputer(name = "", url = "https://", token = "") }) { Text("New cloud computer") }
                    Text(
                        "A server you run yourself (see cloud-computer/README.md). The task can run commands and edit files there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    } else {
        var name by remember(form.id) { mutableStateOf(form.name) }
        var url by remember(form.id) { mutableStateOf(form.url) }
        var token by remember(form.id) { mutableStateOf(form.token) }
        var testing by remember(form.id) { mutableStateOf(false) }
        var testResult by remember(form.id) { mutableStateOf<String?>(null) }
        val urlValid = CloudComputerUrl.normalize(url) != null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (form.id == 0L) "New cloud computer" else "Edit cloud computer") },
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
                        value = url,
                        onValueChange = { url = it; testResult = null },
                        singleLine = true,
                        label = { Text("Server URL") },
                        isError = url.isNotBlank() && !urlValid,
                        supportingText = { if (url.isNotBlank() && !urlValid) Text("Use an https:// address.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; testResult = null },
                        singleLine = true,
                        label = { Text("Access token") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        enabled = urlValid && token.isNotBlank() && !testing,
                        onClick = {
                            testing = true
                            testResult = null
                            cloud.onTest(form.copy(name = name, url = url, token = token)) {
                                testing = false
                                testResult = it
                            }
                        },
                    ) { Text(if (testing) "Testing…" else "Test connection") }
                    testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (form.id != 0L) {
                        TextButton(onClick = {
                            cloud.onDelete(form)
                            if (selectedId == form.id) onSelect(null)
                            editing = null
                        }) { Text("Delete cloud computer", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && token.isNotBlank() && urlValid,
                    onClick = {
                        cloud.onSave(form.copy(name = name, url = url, token = token)) { id -> onSelect(id) }
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
