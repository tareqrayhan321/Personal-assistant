@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalmentor.app.presentation.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.model.TaskRun

/** Full result of one run: status, timing, the model's output or the error, with Copy and Delete. */
@Composable
internal fun RunDetailSheet(run: TaskRun, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val failed = run.status == RunStatus.FAILED
    val body = if (failed) run.error.orEmpty() else run.output
    val timing = buildString {
        append(run.status.label).append(" · ").append(formatMillis(run.startedAt))
        run.finishedAt?.let { append(" · took ").append(formatDuration(it - run.startedAt)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(run.taskTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                timing,
                style = MaterialTheme.typography.labelLarge,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                when {
                    body.isNotBlank() -> SelectionContainer {
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    run.status == RunStatus.RUNNING -> Text("Still running…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text("No output.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (failed && run.output.isNotBlank()) {
                    Text("Partial output", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                    SelectionContainer { Text(run.output, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(body.ifBlank { run.output })) },
                    enabled = body.isNotBlank() || run.output.isNotBlank(),
                ) { Text("Copy") }
                TextButton(onClick = onDelete, enabled = run.status != RunStatus.RUNNING) { Text("Delete") }
            }
        }
    }
}
