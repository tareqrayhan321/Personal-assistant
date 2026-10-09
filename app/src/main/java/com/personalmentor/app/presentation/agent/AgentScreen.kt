package com.personalmentor.app.presentation.agent

import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmentor.app.data.browser.BrowserUiState
import com.personalmentor.app.domain.model.ApprovalMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    onBack: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browser by viewModel.browserState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Browser") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("GitHub & Safety") })
            }
            if (tab == 0) {
                BrowserTab(
                    state = browser,
                    onGo = viewModel::go,
                    onBack = viewModel::goBack,
                    onForward = viewModel::goForward,
                    onReload = viewModel::reload,
                    onClearData = viewModel::clearBrowsingData,
                    attach = viewModel::attachBrowser,
                    detach = viewModel::detachBrowser,
                )
            } else {
                GitHubSafetyContent(
                    state = state,
                    onToken = viewModel::onToken,
                    onSaveToken = viewModel::onSaveToken,
                    onTest = viewModel::onTestGitHub,
                    onApprovalMode = viewModel::onApprovalMode,
                )
            }
        }
    }
}

/** The same WebView the agent drives: watch it work, sign in to sites yourself, or take over. */
@Composable
private fun BrowserTab(
    state: BrowserUiState,
    onGo: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onClearData: () -> Unit,
    attach: (Context) -> WebView,
    detach: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    LaunchedEffect(state.url) { if (state.url.isNotEmpty()) address = state.url }
    BackHandler(enabled = state.canGoBack) { onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
            }
            IconButton(onClick = onForward, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo(address) }),
            )
        }
        if (state.loading) {
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }
        AndroidView(
            factory = { context -> attach(context) },
            onRelease = { detach() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sign in here yourself. The agent never types passwords.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearData) { Text("Clear data") }
        }
    }
}

@Composable
internal fun GitHubSafetyContent(
    state: AgentUiState,
    onToken: (String) -> Unit,
    onSaveToken: () -> Unit,
    onTest: () -> Unit,
    onApprovalMode: (ApprovalMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("GitHub", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use a fine-grained personal access token limited to the repositories the agent may change, with " +
                "Contents, Issues and Pull requests set to read and write. The token stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = onToken,
            label = { Text("GitHub token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveToken, enabled = state.tokenDirty) { Text("Save") }
            OutlinedButton(onClick = onTest, enabled = state.token.isNotBlank()) { Text("Save & test") }
        }
        when (val check = state.github) {
            GitHubCheck.Idle -> Unit
            GitHubCheck.Checking -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            is GitHubCheck.Connected -> Text("Connected as ${check.login}", style = MaterialTheme.typography.bodyMedium)
            is GitHubCheck.Failed -> Text(
                check.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()
        Text("Approvals", style = MaterialTheme.typography.titleSmall)
        ApprovalMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = state.approvalMode == mode, role = Role.RadioButton, onClick = { onApprovalMode(mode) }),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = state.approvalMode == mode, onClick = null)
                Column {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mode.help,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
