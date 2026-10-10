package com.personalmentor.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmentor.app.BuildConfig
import com.personalmentor.app.data.remote.ProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBack = onBack,
        onPreset = viewModel::onPreset,
        onBaseUrl = viewModel::onBaseUrl,
        onApiKey = viewModel::onApiKey,
        onModel = viewModel::onModel,
        onEmbeddingModel = viewModel::onEmbeddingModel,
        onSave = viewModel::onSave,
        onReset = viewModel::onReset,
        onRefreshModels = viewModel::refreshModels,
    )
}

/** Pick a chat model from the provider's list (free ones only where the provider has them). No typing needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    selected: String,
    models: ModelListState,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && models.items.isNotEmpty() }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Chat model") },
            placeholder = { Text(if (models.loading) "Loading models…" else "Tap refresh to load models") },
            isError = selected.isBlank(),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.items.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = models.note.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (models.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (models.loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRefresh) { Text("Refresh models") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onPreset: (ProviderPreset) -> Unit,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onModel: (String) -> Unit,
    onEmbeddingModel: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onRefreshModels: () -> Unit = {},
) {
    val form = state.form
    val providerKind = state.provider

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Provider", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PROVIDER_PRESETS.forEach { p ->
                    val kind = ProviderKind.of(p.baseUrl)
                    val selected = if (kind == ProviderKind.OTHER) {
                        providerKind == ProviderKind.OTHER && form.baseUrl.trim() == p.baseUrl
                    } else {
                        providerKind == kind
                    }
                    FilterChip(selected = selected, onClick = { onPreset(p) }, label = { Text(p.label) })
                }
            }
            Text(
                "Any OpenAI-compatible endpoint works. Cleartext http is allowed only for 10.0.2.2 and localhost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = onBaseUrl,
                label = { Text("Base URL") },
                isError = !state.baseUrlValid,
                supportingText = if (state.baseUrlValid) null else ({ Text("Enter a valid http(s) URL") }),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.apiKey,
                onValueChange = onApiKey,
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (providerKind == ProviderKind.OTHER) {
                OutlinedTextField(
                    value = form.model,
                    onValueChange = onModel,
                    label = { Text("Chat model") },
                    isError = form.model.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ModelPicker(
                    selected = form.model,
                    models = state.models,
                    onSelect = onModel,
                    onRefresh = onRefreshModels,
                )
            }
            OutlinedTextField(
                value = form.embeddingModel,
                onValueChange = onEmbeddingModel,
                label = { Text("Embedding model") },
                isError = form.embeddingModel.isBlank(),
                supportingText = { Text("After changing it, re-add your knowledge-base documents.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) { Text("Reset to defaults") }
                Button(onClick = onSave, enabled = state.canSave) { Text("Save") }
            }

            if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) }) { Text("Privacy policy") }
            }
        }
    }
}
