package com.personalmentor.app.presentation.knowledge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.personalmentor.app.domain.model.IndexStatus
import com.personalmentor.app.domain.model.KnowledgeDocument
import com.personalmentor.app.presentation.theme.PersonalMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KnowledgeContentTest {
    @get:Rule val rule = createComposeRule()

    private fun doc(id: Long, name: String, status: IndexStatus, done: Int = 0, total: Int = 0, error: String? = null) =
        KnowledgeDocument(id, name, 1000, total, done, status, error, 0L)

    private fun show(
        docs: List<KnowledgeDocument>,
        onAdd: () -> Unit = {},
        onDelete: (Long) -> Unit = {},
        onBack: () -> Unit = {},
    ) = rule.setContent {
        PersonalMentorTheme(dynamicColor = false) { KnowledgeContent(docs, onBack, onAdd, onDelete) }
    }

    @Test fun emptyStateExplainsWhatToDo() {
        show(emptyList())
        rule.onNodeWithText("No documents yet.", substring = true).assertIsDisplayed()
    }

    @Test fun eachStatusIsRendered() {
        show(
            listOf(
                doc(1, "notes.md", IndexStatus.INDEXING, done = 3, total = 10),
                doc(2, "book.txt", IndexStatus.READY, done = 12, total = 12),
                doc(3, "scan.pdf", IndexStatus.FAILED, error = "Not a plain-text file"),
            )
        )
        rule.onNodeWithText("Indexing 3/10").assertIsDisplayed()
        rule.onNodeWithText("Ready · 12 chunks").assertIsDisplayed()
        rule.onNodeWithText("Failed: Not a plain-text file").assertIsDisplayed()
    }

    @Test fun deleteAsksForConfirmationThenReportsTheId() {
        val deleted = mutableListOf<Long>()
        show(listOf(doc(7, "notes.md", IndexStatus.READY, 1, 1)), onDelete = { deleted += it })
        rule.onNodeWithContentDescription("Remove notes.md").performClick()
        rule.onNodeWithText("Remove document?").assertIsDisplayed()
        assertTrue(deleted.isEmpty())
        rule.onNodeWithText("Remove").performClick()
        assertEquals(listOf(7L), deleted)
    }

    @Test fun cancellingTheDeleteDialogKeepsTheDocument() {
        val deleted = mutableListOf<Long>()
        show(listOf(doc(7, "notes.md", IndexStatus.READY, 1, 1)), onDelete = { deleted += it })
        rule.onNodeWithContentDescription("Remove notes.md").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(deleted.isEmpty())
        rule.onAllNodesWithText("Remove document?").assertCountEquals(0)
    }

    @Test fun addAndBackButtonsCallBack() {
        var added = 0
        var back = 0
        show(emptyList(), onAdd = { added++ }, onBack = { back++ })
        rule.onNodeWithContentDescription("Add files").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, added)
        assertEquals(1, back)
    }
}
