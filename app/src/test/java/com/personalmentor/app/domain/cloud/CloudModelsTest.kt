package com.personalmentor.app.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelsTest {
    private val token = "t".repeat(32)
    private val ok = CloudSettings(enabled = true, baseUrl = "https://agent.example.workers.dev/", ownerToken = token)

    @Test fun statusParsingAndFlags() {
        assertEquals(CloudJobStatus.RUNNING, CloudJobStatus.fromWire("running"))
        assertEquals(CloudJobStatus.FAILED, CloudJobStatus.fromWire("something-new"))
        assertEquals(CloudJobStatus.FAILED, CloudJobStatus.fromWire(null))
        assertTrue(CloudJobStatus.PENDING.isCancellable && CloudJobStatus.RUNNING.isCancellable)
        assertFalse(CloudJobStatus.SUCCEEDED.isCancellable)
        assertTrue(CloudJobStatus.CANCELLED.isFinished && CloudJobStatus.FAILED.isFinished)
        assertFalse(CloudJobStatus.PENDING.isFinished)
    }

    @Test fun cloudIsOffByDefault() {
        val s = CloudSettings()
        assertFalse(s.enabled)
        assertEquals(CloudErrorKind.DISABLED, s.blocker()?.kind)
    }

    @Test fun usableSettingsHaveNoBlocker() {
        assertNull(ok.blocker())
        assertTrue(ok.configured)
    }

    @Test fun disabledWinsEvenWhenEverythingElseIsFilledIn() = assertEquals(CloudErrorKind.DISABLED, ok.copy(enabled = false).blocker()?.kind)

    @Test fun badUrlOrShortTokenIsNotConfigured() {
        assertEquals(CloudErrorKind.NOT_CONFIGURED, ok.copy(baseUrl = "http://evil.example").blocker()?.kind)
        assertEquals(CloudErrorKind.NOT_CONFIGURED, ok.copy(baseUrl = "").blocker()?.kind)
        assertEquals(CloudErrorKind.NOT_CONFIGURED, ok.copy(ownerToken = "short").blocker()?.kind)
        assertEquals(CloudErrorKind.NOT_CONFIGURED, ok.copy(ownerToken = " ".repeat(40)).blocker()?.kind)
    }

    @Test fun localDevelopmentUrlIsAccepted() = assertNull(ok.copy(baseUrl = "http://10.0.2.2:8787").blocker())

    @Test fun httpErrorsBecomeReadableKinds() {
        assertEquals(CloudErrorKind.UNAUTHORIZED, CloudAgentErrors.from(401, null, null).kind)
        assertEquals(CloudErrorKind.RATE_LIMITED, CloudAgentErrors.from(429, "rate_limited", null).kind)
        assertEquals(CloudErrorKind.SERVER_OFF, CloudAgentErrors.from(503, "cloud_disabled", null).kind)
        assertEquals(CloudErrorKind.SERVER, CloudAgentErrors.from(503, "not_configured", null).kind)
        assertEquals(CloudErrorKind.SERVER, CloudAgentErrors.from(500, "internal", "boom").kind)
        assertEquals(CloudErrorKind.NOT_FOUND, CloudAgentErrors.from(404, null, null).kind)
        assertEquals(CloudErrorKind.CONFLICT, CloudAgentErrors.from(409, null, null).kind)
        assertEquals(CloudErrorKind.INVALID, CloudAgentErrors.from(400, "invalid_input", "payload.prompt must be 1-2000 characters").kind)
    }

    @Test fun quotaErrorSaysNothingWasChargedAndWhenItResets() {
        val e = CloudAgentErrors.from(503, "quota_exceeded", null)
        assertEquals(CloudErrorKind.QUOTA, e.kind)
        assertTrue(e.message!!.contains("00:00 UTC"))
        assertTrue(e.message!!.contains("Nothing was charged"))
    }

    @Test fun serverMessagesAreTrimmedAndInternalsAreNotEchoedForServerErrors() {
        assertEquals(200, CloudAgentErrors.from(400, null, "x".repeat(500)).message!!.length)
        assertFalse(CloudAgentErrors.from(500, null, "stack trace here").message!!.contains("stack trace"))
        assertNotNull(CloudAgentErrors.network(null).message)
    }

    @Test fun shortIdIsEightCharacters() {
        val job = CloudJob("abcdef01-2345-6789-abcd-ef0123456789", "ai_prompt", CloudJobStatus.PENDING, 0, 3, null, 1, 1)
        assertEquals("abcdef01", job.shortId)
    }

    @Test fun submissionKeyIsStableForARetriedPromptUntilAccepted() {
        var n = 0
        val keys = SubmissionKeys { "k${++n}" }
        val first = keys.keyFor("hello")
        assertEquals(first, keys.keyFor("hello")) // retry after a timeout
        assertNotEquals(first, keys.keyFor("different")) // new text, new job
        val third = keys.keyFor("different")
        keys.accepted()
        assertNotEquals(third, keys.keyFor("different")) // sent again on purpose after success: a new job
    }
}
