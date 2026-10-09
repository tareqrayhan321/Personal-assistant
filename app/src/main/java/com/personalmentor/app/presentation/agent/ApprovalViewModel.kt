package com.personalmentor.app.presentation.agent

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.personalmentor.app.domain.agent.ApprovalGate
import com.personalmentor.app.domain.agent.ApprovalRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the agent's pending "may I do this?" question to the UI. */
@HiltViewModel
class ApprovalViewModel @Inject constructor(
    private val gate: ApprovalGate,
) : ViewModel() {
    val pending: StateFlow<ApprovalRequest?> = gate.pending

    fun resolve(approved: Boolean) = gate.resolve(approved)
}

/** Tapping outside the dialog counts as "Deny". */
@Composable
fun ApprovalDialog(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(request.title) },
        text = {
            Text(
                text = request.detail,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { Button(onClick = onApprove) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Deny") } },
    )
}
