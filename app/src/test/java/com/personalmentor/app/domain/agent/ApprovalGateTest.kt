package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.model.ApprovalMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalGateTest {
    private fun gate(mode: ApprovalMode) = ApprovalGate(FakeAgentSettings(mode))

    @Test fun neverModeDoesNotAsk() = runTest {
        val gate = gate(ApprovalMode.NONE)
        assertTrue(gate.confirm("t", "d", ApprovalLevel.SENSITIVE))
        assertNull(gate.pending.value)
    }

    @Test fun sensitiveModeSkipsRoutineActions() = runTest {
        val gate = gate(ApprovalMode.SENSITIVE)
        assertTrue(gate.confirm("t", "d", ApprovalLevel.ROUTINE))
        assertNull(gate.pending.value)
    }

    @Test fun allModeAsksEvenForRoutineActions() = runTest {
        val gate = gate(ApprovalMode.ALL)
        val answer = async { gate.confirm("t", "d", ApprovalLevel.ROUTINE) }
        runCurrent()
        assertNotNull(gate.pending.value)
        gate.resolve(true)
        assertTrue(answer.await())
        assertNull(gate.pending.value)
    }

    @Test fun decliningReturnsFalse() = runTest {
        val gate = gate(ApprovalMode.SENSITIVE)
        val answer = async { gate.confirm("Merge", "PR #3", ApprovalLevel.SENSITIVE) }
        runCurrent()
        gate.resolve(false)
        assertFalse(answer.await())
    }

    @Test fun noAnswerCountsAsDeclined() = runTest {
        val gate = gate(ApprovalMode.SENSITIVE)
        val answer = async { gate.confirm("t", "d", ApprovalLevel.SENSITIVE) }
        runCurrent()
        advanceTimeBy(ApprovalGate.TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(answer.await())
        assertNull(gate.pending.value)
    }
}
