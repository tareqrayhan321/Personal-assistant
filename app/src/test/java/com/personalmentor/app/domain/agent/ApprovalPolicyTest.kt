package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.model.ApprovalMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalPolicyTest {
    private fun gate(mode: ApprovalMode) = ApprovalGate(FakeAgentSettings(mode))

    @Test fun skipConfirmationsNeverAsks() = runTest {
        val gate = gate(ApprovalMode.ALL)
        val ok = withContext(RunApprovalPolicy(skipConfirmations = true)) {
            gate.confirm("t", "d", ApprovalLevel.SENSITIVE)
        }
        assertTrue(ok)
        assertNull(gate.pending.value)
    }

    @Test fun scheduledRunAsksForSensitiveActionsEvenWhenGlobalModeIsNever() = runTest {
        val gate = gate(ApprovalMode.NONE)
        var waiting = false
        val answer = async {
            withContext(RunApprovalPolicy(skipConfirmations = false) { waiting = true }) {
                gate.confirm("Merge", "PR #3", ApprovalLevel.SENSITIVE)
            }
        }
        runCurrent()
        assertTrue(waiting)
        assertNotNull(gate.pending.value)
        gate.resolve(false)
        assertFalse(answer.await())
    }

    @Test fun scheduledRunDoesNotAskForRoutineActions() = runTest {
        val gate = gate(ApprovalMode.SENSITIVE)
        val ok = withContext(RunApprovalPolicy(skipConfirmations = false)) {
            gate.confirm("t", "d", ApprovalLevel.ROUTINE)
        }
        assertTrue(ok)
        assertNull(gate.pending.value)
    }
}
