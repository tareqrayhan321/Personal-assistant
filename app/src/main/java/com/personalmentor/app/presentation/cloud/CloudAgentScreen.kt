package com.personalmentor.app.presentation.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmentor.app.domain.cloud.CloudConnection
import com.personalmentor.app.domain.cloud.CloudJob

@Composable
fun CloudAgentScreen(
    onBack: () -> Unit,
    viewModel: CloudAgentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CloudAgentContent(
        state = state,
        onBack = onBack,
        onEnabled = viewModel::onEnabled,
        onUrl = viewModel::onUrl,
        onToken = viewModel::onToken,
        onSave = viewModel::onSave,
        onForget = viewModel::onForget,
        onTest = viewModel::onTest,
        onRefresh = viewModel::refresh,
        onPrompt = viewModel::onPrompt,
        onSubmit = viewModel::onSubmit,
        onOpen = viewModel::onOpen,
        onCancel = viewModel::onCancel,
        onDismissDetail = viewModel::onDismissDetail,
        onErrorShown = viewModel::onErrorShown,
    )
}

internal fun connectionText(c: CloudConnection): String = when (c) {
    CloudConnection.Off -> "Off. The app works fully on this phone."
    CloudConnection.NotConfigured -> "Enter the address and owner token, then Save."
    CloudConnection.Unchecked -> "Not checked yet."
    CloudConnection.Checking -> "Checking…"
    CloudConnection.Connected -> "Connected."
    is CloudConnection.Failed -> c.message
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloudAgentContent(
    state: CloudUiState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onForget: () -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit,
    onPrompt: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpen: (CloudJob) -> Unit,
    onCancel: (CloudJob) -> Unit,
    onDismissDetail: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    val form = state.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Optional. Your own free Cloudflare Worker for jobs that can also be started from Telegram. " +
                    "Reminders, chat and your knowledge base never need it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Use the cloud agent", modifier = Modifier.weight(1f))
                Switch(checked = form.enabled, onCheckedChange = onEnabled)
            }
            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = onUrl,
                label = { Text("Address (https://…workers.dev)") },
                isError = form.baseUrl.isNotBlank() && form.normalizedUrl == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.ownerToken,
                onValueChange = onToken,
                label = { Text("Owner token") },
                isError = form.ownerToken.isNotBlank() && !form.tokenValid,
                supportingText = { Text("At least 32 characters. Stored only on this phone.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onForget) { Text("Turn off and forget token") }
                Button(onClick = onSave, enabled = state.canSave) { Text("Save") }
            }

            val failed = state.connection is CloudConnection.Failed
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (state.connection == CloudConnection.Checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    connectionText(state.connection),
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = if (state.connection == CloudConnection.Checking) 8.dp else 0.dp),
                )
                TextButton(onClick = onTest, enabled = state.canUseCloud && state.connection != CloudConnection.Checking) { Text("Test connection") }
            }

            if (state.canUseCloud) {
                HorizontalDivider()
                Text("New job", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPrompt,
                    label = { Text("What should the cloud agent do?") },
                    supportingText = { Text("${state.prompt.length}/2000") },
                    isError = state.prompt.length > 2000,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onSubmit,
                        enabled = state.prompt.isNotBlank() && state.prompt.length <= 2000 && !state.submitting,
                    ) { Text("Create job") }
                }
                Text(
                    "Jobs wait on the server until the cloud AI step is enabled; nothing runs on a schedule you did not set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Recent jobs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (state.loadingJobs) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onRefresh, enabled = !state.loadingJobs) { Text("Refresh") }
                }
                if (state.jobs.isEmpty() && !state.loadingJobs) {
                    Text("No jobs yet.", style = MaterialTheme.typography.bodyMedium)
                }
                state.jobs.forEach { job ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(job) }.padding(vertical = 6.dp)) {
                        Text("${job.shortId}  ·  ${job.status.wire}", style = MaterialTheme.typography.bodyLarge)
                        job.errorCode?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    state.detail?.let { job ->
        AlertDialog(
            onDismissRequest = onDismissDetail,
            title = { Text("${job.shortId}  ·  ${job.status.wire}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Attempts ${job.attempts}/${job.maxAttempts}", style = MaterialTheme.typography.bodySmall)
                    job.prompt?.let { Text(it) }
                    job.errorCode?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
                    job.result?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = onDismissDetail) { Text("Close") } },
            dismissButton = if (job.status.isCancellable) {
                { TextButton(onClick = { onCancel(job) }) { Text("Cancel job") } }
            } else null,
        )
    }
}
