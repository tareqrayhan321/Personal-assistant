package com.personalmentor.app.presentation

import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.model.Sender
import com.personalmentor.app.domain.repository.ChatRepository
import com.personalmentor.app.domain.repository.ReportRepository
import com.personalmentor.app.domain.usecase.ClearChatUseCase
import com.personalmentor.app.domain.usecase.FakeScheduler
import com.personalmentor.app.domain.usecase.FakeTaskRepository
import com.personalmentor.app.domain.usecase.ObserveMessagesUseCase
import com.personalmentor.app.domain.usecase.ReportMessageUseCase
import com.personalmentor.app.domain.usecase.RescheduleRemindersUseCase
import com.personalmentor.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeChatRepository : ChatRepository {
    val messages = AssistantMode.entries.associateWith { MutableStateFlow(emptyList<ChatMessage>()) }
    val sent = mutableListOf<Pair<String, AssistantMode>>()
    val cleared = mutableListOf<AssistantMode>()

    /** When set, sendMessage suspends until it is completed. */
    var gate: CompletableDeferred<Result<Unit>>? = null

    override fun observeMessages(mode: AssistantMode): Flow<List<ChatMessage>> = messages.getValue(mode)

    override suspend fun sendMessage(text: String, mode: AssistantMode): Result<Unit> {
        sent += text to mode
        return gate?.await() ?: Result.success(Unit)
    }

    override suspend fun clear(mode: AssistantMode) { cleared += mode }
}

private class FakeReportRepository : ReportRepository {
    val reports = mutableListOf<Triple<ChatMessage, ReportReason, String>>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun report(message: ChatMessage, reason: ReportReason, note: String): Result<Unit> {
        reports += Triple(message, reason, note)
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val repo = FakeChatRepository()
    private val reportRepo = FakeReportRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MainViewModel(
        ObserveMessagesUseCase(repo),
        SendMessageUseCase(repo),
        ClearChatUseCase(repo),
        RescheduleRemindersUseCase(FakeTaskRepository(), FakeScheduler()),
        ReportMessageUseCase(reportRepo),
    )

    /** Keeps uiState (WhileSubscribed) active for the duration of the test. */
    private fun TestScope.started(): MainViewModel {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private fun msg(text: String, mode: AssistantMode) = ChatMessage(1, text, Sender.USER, mode, 0L)

    @Test fun startsInTaskModeWithNothingToSend() = runTest {
        val s = started().uiState.value
        assertEquals(AssistantMode.TASK, s.mode)
        assertFalse(s.canSend)
    }

    @Test fun typingEnablesSend() = runTest {
        val vm = started()
        vm.onInputChange("hello")
        assertTrue(vm.uiState.value.canSend)
        vm.onInputChange("   ")
        assertFalse(vm.uiState.value.canSend)
    }

    @Test fun sendTrimsClearsInputAndUsesCurrentMode() = runTest {
        val vm = started()
        vm.onModeChange(AssistantMode.MENTOR)
        vm.onInputChange("  hi there  ")
        vm.onSend()
        runCurrent()
        assertEquals(listOf("hi there" to AssistantMode.MENTOR), repo.sent)
        assertEquals("", vm.uiState.value.input)
    }

    @Test fun blankInputIsNotSent() = runTest {
        val vm = started()
        vm.onInputChange("   ")
        vm.onSend()
        runCurrent()
        assertTrue(repo.sent.isEmpty())
    }

    @Test fun loadingFlagFollowsTheRequestAndBlocksASecondSend() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        repo.gate = gate
        val vm = started()
        vm.onInputChange("one")
        vm.onSend()
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)

        vm.onInputChange("two")
        vm.onSend()
        runCurrent()
        assertEquals(1, repo.sent.size)

        gate.complete(Result.success(Unit))
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun failureShowsTheErrorUntilItIsDismissed() = runTest {
        repo.gate = CompletableDeferred(Result.failure(IllegalStateException("boom")))
        val vm = started()
        vm.onInputChange("x")
        vm.onSend()
        runCurrent()
        assertEquals("boom", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        vm.onErrorShown()
        assertNull(vm.uiState.value.error)
    }

    @Test fun failureWithoutMessageFallsBackToGenericText() = runTest {
        repo.gate = CompletableDeferred(Result.failure(RuntimeException()))
        val vm = started()
        vm.onInputChange("x")
        vm.onSend()
        runCurrent()
        assertEquals("Something went wrong. Please try again.", vm.uiState.value.error)
    }

    @Test fun eachModeShowsItsOwnConversation() = runTest {
        repo.messages.getValue(AssistantMode.TASK).value = listOf(msg("task chat", AssistantMode.TASK))
        repo.messages.getValue(AssistantMode.MENTOR).value = listOf(msg("mentor chat", AssistantMode.MENTOR))
        val vm = started()
        assertEquals("task chat", vm.uiState.value.messages.single().text)
        vm.onModeChange(AssistantMode.MENTOR)
        runCurrent()
        assertEquals(AssistantMode.MENTOR, vm.uiState.value.mode)
        assertEquals("mentor chat", vm.uiState.value.messages.single().text)
    }

    private fun reply(text: String = "answer") = ChatMessage(2, text, Sender.ASSISTANT, AssistantMode.TASK, 0L)

    @Test fun reportingAnAssistantReplySendsItWithTheTrimmedNote() = runTest {
        val vm = started()
        vm.onReport(reply(), ReportReason.OFFENSIVE, "  not ok  ")
        runCurrent()
        val (message, reason, note) = reportRepo.reports.single()
        assertEquals("answer", message.text)
        assertEquals(ReportReason.OFFENSIVE, reason)
        assertEquals("not ok", note)
        assertNull(vm.uiState.value.error)
    }

    @Test fun reportFailureIsShownAsAnError() = runTest {
        reportRepo.result = Result.failure(java.io.IOException("offline"))
        val vm = started()
        vm.onReport(reply(), ReportReason.OTHER, "")
        runCurrent()
        assertEquals("offline", vm.uiState.value.error)
    }

    @Test fun userMessagesCannotBeReported() = runTest {
        val vm = started()
        vm.onReport(msg("mine", AssistantMode.TASK), ReportReason.OTHER, "")
        runCurrent()
        assertTrue(reportRepo.reports.isEmpty())
        assertEquals("Only assistant replies can be reported", vm.uiState.value.error)
    }

    @Test fun clearChatClearsOnlyTheCurrentMode() = runTest {
        val vm = started()
        vm.onModeChange(AssistantMode.MENTOR)
        vm.onClearChat()
        runCurrent()
        assertEquals(listOf(AssistantMode.MENTOR), repo.cleared)
    }
}
