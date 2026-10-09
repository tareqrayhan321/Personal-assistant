package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.browser.PageElement
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

class BrowserToolExecutorTest {
    private val browser = FakeBrowser()
    private val approver = RecordingApprover()
    private val executor = BrowserToolExecutor(browser, approver, Json { ignoreUnknownKeys = true; encodeDefaults = true })

    private fun call(tool: String, args: String): JsonObject = runBlocking {
        Json.parseToJsonElement(executor.execute(tool, Json.parseToJsonElement(args).jsonObject)).jsonObject
    }

    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean
    private fun JsonObject.error() = getValue("error").jsonPrimitive.content

    @Test fun passwordFieldsAreNeverFilled() {
        browser.elements = mapOf(1 to PageElement(1, "input", type = "password", secret = true))
        val r = call("browser_type", """{"id":1,"text":"hunter2"}""")
        assertFalse(r.ok())
        assertTrue(r.error().contains("password"))
        assertTrue(browser.actions.isEmpty())
        assertTrue(approver.asked.isEmpty())
    }

    @Test fun aSensitiveClickIsAskedAboutAndStopsWhenDeclined() {
        browser.elements = mapOf(1 to PageElement(1, "button", text = "Place order"))
        approver.answer = false
        val r = call("browser_click", """{"id":1}""")
        assertFalse(r.ok())
        assertEquals(ApprovalLevel.SENSITIVE, approver.asked.single().level)
        assertTrue(browser.actions.isEmpty())
    }

    @Test fun anOrdinaryClickIsRoutineAndGoesThrough() {
        browser.elements = mapOf(1 to PageElement(1, "a", text = "Next page", href = "https://shop.example/p2"))
        val r = call("browser_click", """{"id":1}""")
        assertTrue(r.ok())
        assertEquals(ApprovalLevel.ROUTINE, approver.asked.single().level)
        assertEquals(listOf("click 1"), browser.actions)
    }

    @Test fun submittingATypedValueIsSensitive() {
        browser.elements = mapOf(1 to PageElement(1, "input", type = "text", text = "Search"))
        call("browser_type", """{"id":1,"text":"shoes","submit":true}""")
        assertEquals(ApprovalLevel.SENSITIVE, approver.asked.single().level)
    }

    @Test fun anUnknownIdAsksForAFreshRead() {
        val r = call("browser_click", """{"id":42}""")
        assertFalse(r.ok())
        assertTrue(r.error().contains("browser_read"))
        assertTrue(browser.actions.isEmpty())
    }

    @Test fun pageResultsCarryTheUntrustedNotice() {
        val r = call("browser_open", """{"url":"https://shop.example"}""")
        assertTrue(r.ok())
        assertTrue(r.getValue("notice").jsonPrimitive.content.contains("untrusted"))
    }

    @Test fun sensitiveWordsAreRecognisedInEnglishAndBengali() {
        assertTrue(SensitiveActions.isSensitive(PageElement(1, "button", text = "Delete account")))
        assertTrue(SensitiveActions.isSensitive(PageElement(1, "button", text = "অর্ডার করুন")))
        assertTrue(SensitiveActions.isSensitive(PageElement(1, "input", type = "submit", text = "Go")))
        assertFalse(SensitiveActions.isSensitive(PageElement(1, "a", text = "Read more")))
    }
}
