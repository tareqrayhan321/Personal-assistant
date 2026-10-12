package com.personalmentor.app.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Runs a suspend block to completion; the fake API never really suspends, so this is enough here. */
private fun <T> sync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return out!!.getOrThrow()
}

private class FakeApi : CloudAgentApi {
    val calls = mutableListOf<String>()
    val keysSeen = mutableListOf<String>()
    var failNext: CloudAgentException? = null

    private fun job(status: CloudJobStatus = CloudJobStatus.PENDING) = CloudJob("11111111-2222-3333-4444-555555555555", "ai_prompt", status, 0, 3, null, 1, 1)
    private fun gate(name: String) {
        calls += name
        failNext?.let { failNext = null; throw it }
    }

    override suspend fun health(settings: CloudSettings) = gate("health")
    override suspend fun createJob(settings: CloudSettings, prompt: String, idempotencyKey: String): CloudJob {
        keysSeen += idempotencyKey
        gate("create")
        return job()
    }
    override suspend fun listJobs(settings: CloudSettings, limit: Int): List<CloudJob> { gate("list"); return listOf(job()) }
    override suspend fun getJob(settings: CloudSettings, id: String): CloudJob { gate("get"); return job() }
    override suspend fun cancelJob(settings: CloudSettings, id: String): CloudJob { gate("cancel"); return job(CloudJobStatus.CANCELLED) }
}

class CloudJobServiceTest {
    private val on = CloudSettings(true, "https://agent.example.workers.dev", "t".repeat(32))
    private var current = on
    private val api = FakeApi()
    private var n = 0
    private val service = CloudJobService(api, { current }, SubmissionKeys { "key${++n}" })

    private fun expectKind(kind: CloudErrorKind, block: suspend () -> Unit) {
        try {
            sync(block)
            fail("expected $kind")
        } catch (e: CloudAgentException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test fun nothingIsSentWhileCloudIsOffOrIncomplete() {
        for (s in listOf(CloudSettings(), on.copy(enabled = false), on.copy(baseUrl = "nope"), on.copy(ownerToken = "x"))) {
            current = s
            expectKind(s.blocker()!!.kind) { service.checkConnection() }
            expectKind(s.blocker()!!.kind) { service.submit("hello") }
            expectKind(s.blocker()!!.kind) { service.recent() }
            expectKind(s.blocker()!!.kind) { service.get("id") }
            expectKind(s.blocker()!!.kind) { service.cancel("id") }
        }
        assertTrue("no network call may happen", api.calls.isEmpty())
    }

    @Test fun submitTrimsAndSends() {
        val job = sync { service.submit("  hello  ") }
        assertEquals(CloudJobStatus.PENDING, job.status)
        assertEquals(listOf("create"), api.calls)
    }

    @Test fun badPromptsNeverReachTheServer() {
        expectKind(CloudErrorKind.INVALID) { service.submit("   ") }
        expectKind(CloudErrorKind.INVALID) { service.submit("x".repeat(2001)) }
        assertTrue(api.calls.isEmpty())
        sync { service.submit("x".repeat(2000)) } // the limit itself is fine
    }

    @Test fun aFailedSubmitIsRetriedWithTheSameKeyAndASuccessfulOneStartsFresh() {
        api.failNext = CloudAgentErrors.network("timeout")
        expectKind(CloudErrorKind.NETWORK) { service.submit("same text") }
        sync { service.submit("same text") }
        assertEquals(api.keysSeen[0], api.keysSeen[1]) // server can dedupe the retry
        sync { service.submit("same text") } // user sends it again on purpose
        assertTrue(api.keysSeen[2] != api.keysSeen[1])
    }

    @Test fun serverErrorsPassThroughUnchanged() {
        api.failNext = CloudAgentErrors.from(503, "quota_exceeded", null)
        expectKind(CloudErrorKind.QUOTA) { service.recent() }
    }

    @Test fun readAndCancelGoThrough() {
        assertEquals(1, sync { service.recent() }.size)
        assertEquals(CloudJobStatus.CANCELLED, sync { service.cancel("id") }.status)
        sync { service.get("id") }
        sync { service.checkConnection() }
        assertEquals(listOf("list", "cancel", "get", "health"), api.calls)
    }
}
