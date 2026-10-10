package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.macro.FakeDevice
import com.personalmentor.app.domain.macro.InMemoryMacroRepository
import com.personalmentor.app.domain.macro.MacroExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroToolExecutorTest {
    private val repo = InMemoryMacroRepository()
    private val device = FakeDevice()
    private val approver = RecordingApprover()
    private val tools = MacroToolExecutor(repo, MacroExecutor(repo, device), approver)

    private fun call(tool: String, args: String): JsonObject = runBlocking {
        Json.parseToJsonElement(tools.execute(tool, Json.parseToJsonElement(args).jsonObject)).jsonObject
    }

    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean

    private val validCreate = """{"name":"Low battery","triggers":[{"type":"battery_below","params":{"level":15}}],
        "actions":[{"type":"notify","params":{"title":"Battery","text":"Only {battery}% left"}}]}"""

    @Test fun createAsksFirstAndStoresTheMacro() {
        assertTrue(call("macro_create", validCreate).ok())
        assertEquals(ApprovalLevel.SENSITIVE, approver.asked.single().level)
        assertEquals("15", repo.findByName("Low battery")!!.triggers.single().params["level"])
    }

    @Test fun declinedCreateStoresNothing() {
        approver.answer = false
        assertFalse(call("macro_create", validCreate).ok())
        assertTrue(repo.macros.value.isEmpty())
    }

    @Test fun invalidParametersAreRejectedBeforeAsking() {
        val bad = validCreate.replace("15", "500")
        val r = call("macro_create", bad)
        assertFalse(r.ok())
        assertTrue(approver.asked.isEmpty())
    }

    @Test fun unknownTypesAreRejected() {
        val r = call("macro_create", """{"name":"x","triggers":[{"type":"teleport"}],"actions":[{"type":"toast","params":{"text":"a"}}]}""")
        assertFalse(r.ok())
    }

    @Test fun runExecutesAfterApproval() {
        call("macro_create", """{"name":"Hello","triggers":[{"type":"manual"}],"actions":[{"type":"toast","params":{"text":"hi"}}]}""")
        approver.asked.clear()
        assertTrue(call("macro_run", """{"name":"hello"}""").ok())
        assertEquals(listOf("toast:hi"), device.calls)
        assertEquals(1, approver.asked.size)
    }

    @Test fun deleteNeedsApprovalAndRemovesTheMacro() {
        call("macro_create", """{"name":"Temp","triggers":[{"type":"manual"}],"actions":[{"type":"toast","params":{"text":"a"}}]}""")
        assertTrue(call("macro_delete", """{"name":"Temp"}""").ok())
        assertTrue(repo.macros.value.isEmpty())
    }
}
