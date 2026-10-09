package com.personalmentor.app.domain.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GitHubToolExecutorTest {
    private val api = FakeGitHubApi()
    private val approver = RecordingApprover()
    private val executor = GitHubToolExecutor(api, approver)

    private fun call(tool: String, args: String): JsonObject = runBlocking {
        Json.parseToJsonElement(executor.execute(tool, Json.parseToJsonElement(args).jsonObject)).jsonObject
    }

    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean
    private fun JsonObject.error() = getValue("error").jsonPrimitive.content
    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    @Test fun repoWithDotDotPartsIsRejectedBeforeAnyRequest() {
        val r = call("github_read_file", """{"repo":"../..","path":"x"}""")
        assertFalse(r.ok())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun pathTraversalIsRejectedBeforeAnyRequest() {
        val r = call("github_read_file", """{"repo":"me/app","path":"a/../../user"}""")
        assertFalse(r.ok())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun readFileDecodesTheContent() {
        api.reply("GET", "repos/me/app/contents/README.md", 200, """{"type":"file","sha":"abc","size":6,"content":"${b64("héllo")}"}""")
        val r = call("github_read_file", """{"repo":"me/app","path":"README.md"}""")
        assertTrue(r.ok())
        assertEquals("héllo", r.getValue("content").jsonPrimitive.content)
        assertEquals("abc", r.getValue("sha").jsonPrimitive.content)
    }

    @Test fun writeFileCreatesANewFileAfterApproval() {
        api.reply("GET", "repos/me/app/contents/docs/a.txt", 404, """{"message":"Not Found"}""")
        api.reply("PUT", "repos/me/app/contents/docs/a.txt", 201, """{"commit":{"sha":"c1","html_url":"https://github.com/me/app/commit/c1"}}""")
        val r = call("github_write_file", """{"repo":"me/app","path":"docs/a.txt","content":"hi","message":"add a"}""")
        assertTrue(r.ok())
        assertEquals("c1", r.getValue("commit_sha").jsonPrimitive.content)
        val put = api.calls.single { it.method == "PUT" }
        val body = put.body!!
        assertNull(body["sha"])
        assertEquals(b64("hi"), body.getValue("content").jsonPrimitive.content)
        assertEquals(ApprovalLevel.SENSITIVE, approver.asked.single().level)
    }

    @Test fun writeFileUpdatesWithTheExistingSha() {
        api.reply("GET", "repos/me/app/contents/a.txt", 200, """{"type":"file","sha":"old"}""")
        api.reply("PUT", "repos/me/app/contents/a.txt", 200, """{"commit":{"sha":"c2"}}""")
        val r = call("github_write_file", """{"repo":"me/app","path":"a.txt","content":"x","message":"edit","branch":"feat/x"}""")
        assertTrue(r.ok())
        val body = api.calls.single { it.method == "PUT" }.body!!
        assertEquals("old", body.getValue("sha").jsonPrimitive.content)
        assertEquals("feat/x", body.getValue("branch").jsonPrimitive.content)
    }

    @Test fun declinedApprovalNeverWrites() {
        approver.answer = false
        api.reply("GET", "repos/me/app/contents/a.txt", 404)
        val r = call("github_write_file", """{"repo":"me/app","path":"a.txt","content":"x","message":"m"}""")
        assertFalse(r.ok())
        assertTrue(r.error().contains("declined"))
        assertTrue(api.calls.none { it.method == "PUT" })
    }

    @Test fun createBranchStartsFromTheDefaultBranch() {
        api.reply("GET", "repos/me/app", 200, """{"default_branch":"main"}""")
        api.reply("GET", "repos/me/app/git/ref/heads/main", 200, """{"object":{"sha":"s1"}}""")
        api.reply("POST", "repos/me/app/git/refs", 201, """{}""")
        val r = call("github_create_branch", """{"repo":"me/app","name":"feat/x"}""")
        assertTrue(r.ok())
        val body = api.calls.single { it.method == "POST" }.body!!
        assertEquals("refs/heads/feat/x", body.getValue("ref").jsonPrimitive.content)
        assertEquals("s1", body.getValue("sha").jsonPrimitive.content)
    }

    @Test fun mergeMethodIsValidated() {
        val r = call("github_merge_pull_request", """{"repo":"me/app","number":3,"method":"yolo"}""")
        assertFalse(r.ok())
        assertTrue(approver.asked.isEmpty())
    }

    @Test fun githubErrorsAreReportedWithTheirStatus() {
        api.reply("GET", "repos/me/app/issues", 401, """{"message":"Bad credentials"}""")
        val r = call("github_list_issues", """{"repo":"me/app"}""")
        assertFalse(r.ok())
        assertTrue(r.error().contains("401"))
        assertTrue(r.error().contains("Bad credentials"))
    }

    @Test fun issuesListSkipsPullRequests() {
        api.reply(
            "GET", "repos/me/app/issues", 200,
            """[{"number":1,"title":"bug","state":"open"},{"number":2,"title":"pr","state":"open","pull_request":{}}]""",
        )
        val r = call("github_list_issues", """{"repo":"me/app"}""")
        assertTrue(r.ok())
        assertNotNull(r["issues"])
        assertEquals(1, (r.getValue("issues") as kotlinx.serialization.json.JsonArray).size)
    }
}
