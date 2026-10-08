package com.personalmentor.app.presentation.tasks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.presentation.theme.PersonalMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TasksContentTest {
    @get:Rule val rule = createComposeRule()

    private val soon = System.currentTimeMillis() + 3_600_000
    private val open = TodoTask(id = 1, title = "Call Sam", notes = "about the invoice", remindAt = soon, repeat = Repeat.DAILY)
    private val done = TodoTask(id = 2, title = "Buy milk", isDone = true)

    private fun show(
        tasks: List<TodoTask>,
        banner: Boolean = false,
        onBack: () -> Unit = {},
        onAllow: () -> Unit = {},
        onToggle: (TodoTask) -> Unit = {},
        onDelete: (TodoTask) -> Unit = {},
    ) = rule.setContent {
        PersonalMentorTheme(dynamicColor = false) { TasksContent(tasks, banner, onBack, onAllow, onToggle, onDelete) }
    }

    @Test fun emptyStatePointsToTheAssistant() {
        show(emptyList())
        rule.onNodeWithText("No tasks yet.", substring = true).assertIsDisplayed()
    }

    @Test fun rowShowsTitleNotesAndRepeatingReminder() {
        show(listOf(open))
        rule.onNodeWithText("Call Sam").assertIsDisplayed()
        rule.onNodeWithText("about the invoice").assertIsDisplayed()
        rule.onNodeWithText("· Daily", substring = true).assertIsDisplayed()
    }

    @Test fun oneOffReminderHasNoRepeatSuffix() {
        show(listOf(open.copy(repeat = Repeat.NONE)))
        rule.onNodeWithText("Reminder:", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("· Daily", substring = true).assertCountEquals(0)
    }

    @Test fun checkboxReflectsDoneState() {
        show(listOf(open, done))
        rule.onAllNodes(isToggleable())[0].assertIsOff()
        rule.onAllNodes(isToggleable())[1].assertIsOn()
    }

    @Test fun togglingAndDeletingReportTheRightTask() {
        val toggled = mutableListOf<TodoTask>()
        val deleted = mutableListOf<TodoTask>()
        show(listOf(open, done), onToggle = { toggled += it }, onDelete = { deleted += it })
        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onAllNodesWithContentDescription("Delete task")[0].performClick()
        assertEquals(listOf(done), toggled)
        assertEquals(listOf(open), deleted)
    }

    @Test fun exactAlarmBannerOnlyWhenRequested() {
        show(listOf(open), banner = false)
        rule.onAllNodesWithText("Exact alarms are off", substring = true).assertCountEquals(0)
    }

    @Test fun exactAlarmBannerOffersToAllow() {
        var allowed = 0
        show(listOf(open), banner = true, onAllow = { allowed++ })
        rule.onNodeWithText("Exact alarms are off", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(1, allowed)
    }

    @Test fun backButtonCallsBack() {
        var back = 0
        show(emptyList(), onBack = { back++ })
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }
}
