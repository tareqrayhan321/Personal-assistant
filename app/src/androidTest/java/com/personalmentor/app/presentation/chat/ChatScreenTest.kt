package com.personalmentor.app.presentation.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.model.Sender
import com.personalmentor.app.presentation.ChatUiState
import com.personalmentor.app.presentation.theme.PersonalMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule val rule = createComposeRule()

    private val conversation = listOf(
        ChatMessage(1, "What is RAG?", Sender.USER, AssistantMode.MENTOR, 0L),
        ChatMessage(2, "Retrieval-augmented generation.", Sender.ASSISTANT, AssistantMode.MENTOR, 0L),
    )

    private data class Report(val message: ChatMessage, val reason: ReportReason, val note: String)

    private fun show(
        state: ChatUiState = ChatUiState(mode = AssistantMode.MENTOR, messages = conversation),
        onReport: (ChatMessage, ReportReason, String) -> Unit = { _, _, _ -> },
        onErrorShown: () -> Unit = {},
    ) = rule.setContent {
        PersonalMentorTheme(dynamicColor = false) {
            ChatScreen(
                uiState = state,
                onInputChange = {}, onSend = {}, onModeChange = {}, onClearChat = {},
                onOpenTasks = {}, onOpenKnowledge = {}, onOpenSettings = {},
                onReport = onReport, onErrorShown = onErrorShown,
            )
        }
    }

    @Test fun messagesAreShown() {
        show()
        rule.onNodeWithText("What is RAG?").assertIsDisplayed()
        rule.onNodeWithText("Retrieval-augmented generation.").assertIsDisplayed()
    }

    @Test fun onlyAssistantRepliesCanBeReported() {
        show()
        rule.onAllNodesWithText("Report").assertCountEquals(1)
    }

    @Test fun reportDialogSendsReasonNoteAndTheReportedReply() {
        val sent = mutableListOf<Report>()
        show(onReport = { m, r, n -> sent += Report(m, r, n) })

        rule.onNodeWithText("Report").performClick()
        rule.onNodeWithText("Report this response").assertIsDisplayed()
        rule.onAllNodes(isSelectable())[1].performClick() // "Wrong or misleading"
        rule.onNodeWithText("Note (optional)").performTextInput("made it up")
        rule.onNodeWithText("Send").performClick()

        val report = sent.single()
        assertEquals(2L, report.message.id)
        assertEquals(ReportReason.INACCURATE, report.reason)
        assertEquals("made it up", report.note)
        rule.onAllNodesWithText("Report this response").assertCountEquals(0)
    }

    @Test fun cancellingTheReportDialogSendsNothing() {
        val sent = mutableListOf<Report>()
        show(onReport = { m, r, n -> sent += Report(m, r, n) })
        rule.onNodeWithText("Report").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(sent.isEmpty())
        rule.onAllNodesWithText("Report this response").assertCountEquals(0)
    }

    @Test fun knowledgeBaseButtonOnlyInMentorMode() {
        show(ChatUiState(mode = AssistantMode.TASK, messages = emptyList()))
        rule.onAllNodesWithContentDescription("Knowledge base").assertCountEquals(0)
    }

    @Test fun knowledgeBaseButtonShownInMentorMode() {
        show(ChatUiState(mode = AssistantMode.MENTOR, messages = emptyList()))
        rule.onAllNodesWithContentDescription("Knowledge base").assertCountEquals(1)
    }

    @Test fun errorIsShownOnceAndAcknowledged() {
        var shown = 0
        show(ChatUiState(mode = AssistantMode.TASK, error = "boom"), onErrorShown = { shown++ })
        rule.onNodeWithText("boom").assertIsDisplayed()
        rule.waitUntil(3_000) { shown == 1 }
    }
}
