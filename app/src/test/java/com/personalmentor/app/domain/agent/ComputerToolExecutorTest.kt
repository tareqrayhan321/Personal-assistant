package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.computer.ComputerApi
import com.personalmentor.app.domain.computer.ComputerException
import com.personalmentor.app.domain.model.CloudComputer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerToolExecutorTest {
    private class FakeComputerApi : ComputerApi {
        data class Call(val endpoint: String, val body: JsonObject?, val timeoutSeconds: Int)

        val calls = mutableListOf<Call>()
        var failWith: String? = null

        override suspend fun call(computer: CloudComputer, endpoint: String, body: JsonObject?, timeoutSeconds: Int): JsonObject {
            calls += Call(endpoint, body, timeoutSeconds)
            failWith?.let { throw ComputerException(it) }
            return buildJsonObject {
                put("ok", true)
                put("stdout", "hi")
                put("exit_code", 0)
            }
        }
    }

    private val api = FakeComputerApi()
    private val approver = RecordingApprover()
    private val executor = ComputerToolExecutor(api, approver, Json)
    private val computer = CloudComputer(id = 1, name = "Sandbox", url = "https://box.example", token = "t")

    private fun run(tool: String, args: String): JsonObject =
        Json.parseToJsonElement(runBlocking { executor.execute(tool, args, computer) }).jsonObject

    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean

    @Test fun execAsksForApprovalThenRunsTheCommand() {
        val r = run(ComputerTools.EXEC, """{"command":"ls -la","timeout_sec":30}""")
        assertTrue(r.ok())
        assertEquals("hi", r.getValue("stdout").jsonPrimitive.content)
        assertEquals(ApprovalLevel.SENSITIVE, approver.asked.single().level)
        assertTrue(approver.asked.single().detail.contains("ls -la"))
        assertEquals("exec", api.calls.single().endpoint)
        assertEquals(45, api.calls.single().timeoutSeconds)
    }

    @Test fun declinedCommandNeverReachesTheServer() {
        approver.answer = false
        val r = run(ComputerTools.EXEC, """{"command":"rm -rf x"}""")
        assertFalse(r.ok())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun timeoutIsClamped() {
        run(ComputerTools.EXEC, """{"command":"sleep 1","timeout_sec":99999}""")
        assertEquals(600, api.calls.single().body!!.getValue("timeout_sec").jsonPrimitive.content.toInt())
    }

    @Test fun readsAndListingsDoNotAsk() {
        run(ComputerTools.READ_FILE, """{"path":"a.txt"}""")
        run(ComputerTools.LIST_FILES, """{}""")
        assertTrue(approver.asked.isEmpty())
        assertEquals(listOf("read_file", "list_files"), api.calls.map { it.endpoint })
    }

    @Test fun writeAsksFirst() {
        val r = run(ComputerTools.WRITE_FILE, """{"path":"a.txt","content":"hello"}""")
        assertTrue(r.ok())
        assertEquals(1, approver.asked.size)
        assertEquals("write_file", api.calls.single().endpoint)
    }

    @Test fun missingArgumentsAreReported() {
        assertFalse(run(ComputerTools.EXEC, "{}").ok())
        assertFalse(run(ComputerTools.WRITE_FILE, """{"path":"a"}""").ok())
        assertTrue(api.calls.isEmpty())
    }

    @Test fun serverErrorsBecomeToolFailures() {
        api.failWith = "Could not reach the cloud computer"
        val r = run(ComputerTools.READ_FILE, """{"path":"a"}""")
        assertFalse(r.ok())
        assertTrue(r.getValue("error").jsonPrimitive.content.contains("Could not reach"))
    }

    @Test fun unknownToolFails() {
        assertFalse(run("computer_reboot", "{}").ok())
    }
}
