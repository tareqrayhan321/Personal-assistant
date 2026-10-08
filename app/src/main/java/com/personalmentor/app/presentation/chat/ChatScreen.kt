package com.personalmentor.app.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.model.Sender
import com.personalmentor.app.presentation.ChatUiState
import com.personalmentor.app.presentation.theme.PersonalMentorTheme
import java.text.DateFormat
import java.util.Date

private val UserBubbleShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
private val AssistantBubbleShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onModeChange: (AssistantMode) -> Unit,
    onClearChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenSettings: () -> Unit,
    onReport: (ChatMessage, ReportReason, String) -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var reporting by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    // The list is reversed (index 0 = newest, anchored to the bottom), so a streaming reply grows upward.
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                mode = uiState.mode,
                onModeChange = onModeChange,
                onClearChat = onClearChat,
                onOpenTasks = onOpenTasks,
                onOpenKnowledge = onOpenKnowledge,
                onOpenSettings = onOpenSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInputBar(
                value = uiState.input,
                mode = uiState.mode,
                canSend = uiState.canSend,
                onValueChange = onInputChange,
                onSend = onSend,
            )
        },
    ) { padding ->
        if (uiState.messages.isEmpty()) {
            EmptyState(uiState.mode, Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.messages.asReversed(), key = { it.id }) { MessageBubble(it, onReport = { reporting = it }) }
            }
        }
    }

    reporting?.let { message ->
        ReportDialog(
            onDismiss = { reporting = null },
            onSend = { reason, note ->
                onReport(message, reason, note)
                reporting = null
            },
        )
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (ReportReason, String) -> Unit) {
    var reason by remember { mutableStateOf(ReportReason.OFFENSIVE) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this response") },
        text = {
            Column {
                Text(
                    "The response and your note are sent to the developer to improve safety.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ReportReason.entries.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, note) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    mode: AssistantMode,
    onModeChange: (AssistantMode) -> Unit,
    onClearChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = { Text("Personal Mentor") },
                actions = {
                    if (mode == AssistantMode.MENTOR) {
                        IconButton(onClick = onOpenKnowledge) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Knowledge base")
                        }
                    }
                    IconButton(onClick = onOpenTasks) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Open tasks")
                    }
                    IconButton(onClick = onClearChat) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear conversation")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val modes = AssistantMode.entries
                modes.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == mode,
                        onClick = { onModeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(item.label) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onReport: () -> Unit) {
    val isUser = message.sender == Sender.USER
    // Empty assistant reply = still waiting for the first streamed token.
    if (!isUser && message.text.isBlank()) {
        TypingIndicator()
        return
    }
    val container = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val time = remember(message.timestamp) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            shape = if (isUser) UserBubbleShape else AssistantBubbleShape,
            color = container,
            contentColor = content,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp).alpha(0.7f),
                )
            }
        }
        if (!isUser) {
            TextButton(onClick = onReport) {
                Text("Report", style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.7f))
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(shape = AssistantBubbleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { index ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = index * 150),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    mode: AssistantMode,
    canSend: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (mode == AssistantMode.TASK) "Add a task or set a reminder…" else "Ask your mentor…")
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun EmptyState(mode: AssistantMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = when (mode) {
                AssistantMode.TASK -> "Task Mode\nTell me what you need done or remind you about."
                AssistantMode.MENTOR -> "Mentor Mode\nAsk me anything from your knowledge base."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    PersonalMentorTheme(dynamicColor = false) {
        ChatScreen(
            uiState = ChatUiState(
                messages = listOf(
                    ChatMessage(1, "Remind me to call mom at 6 PM", Sender.USER, AssistantMode.TASK),
                    ChatMessage(2, "Done. I'll remind you at 6 PM.", Sender.ASSISTANT, AssistantMode.TASK),
                ),
                input = "Hello",
            ),
            onInputChange = {}, onSend = {}, onModeChange = {},
            onClearChat = {}, onOpenTasks = {}, onOpenKnowledge = {}, onOpenSettings = {}, onReport = { _, _, _ -> }, onErrorShown = {},
        )
    }
}
