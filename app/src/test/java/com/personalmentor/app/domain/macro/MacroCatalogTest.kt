package com.personalmentor.app.domain.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroCatalogTest {
    private fun item(type: String, vararg params: Pair<String, String>) = MacroItem(type, mapOf(*params))

    @Test fun typeIdsAreUnique() {
        assertEquals(MacroCatalog.all.size, MacroCatalog.all.map { it.type }.toSet().size)
    }

    @Test fun everyItemWithoutRequiredTextStartsValid() {
        MacroCatalog.all.filter { d -> d.params.all { it.optional || it.default.isNotEmpty() } }.forEach { def ->
            assertNull(def.type, MacroCatalog.validate(MacroCatalog.newItem(def), def.section))
        }
    }

    @Test fun numbersAreRangeChecked() {
        assertNotNull(MacroCatalog.validate(item("battery_below", "level" to "150")))
        assertNotNull(MacroCatalog.validate(item("battery_below", "level" to "abc")))
        assertNull(MacroCatalog.validate(item("battery_below", "level" to "15")))
    }

    @Test fun urlsMustBeHttps() {
        assertNotNull(MacroCatalog.validate(item("open_url", "url" to "http://example.com")))
        assertNull(MacroCatalog.validate(item("open_url", "url" to "https://example.com")))
    }

    @Test fun fileNamesCannotEscapeTheFolder() {
        assertNotNull(MacroCatalog.validate(item("write_file", "file" to "../secret", "text" to "x")))
        assertNotNull(MacroCatalog.validate(item("write_file", "file" to "a/b.txt", "text" to "x")))
        assertNull(MacroCatalog.validate(item("write_file", "file" to "notes.txt", "text" to "x")))
    }

    @Test fun timesAreChecked() {
        assertNotNull(MacroCatalog.validate(item("time_of_day", "time" to "25:00", "days" to MacroCatalog.DAYS_ALL)))
        assertNull(MacroCatalog.validate(item("time_of_day", "time" to "7:05", "days" to MacroCatalog.DAYS_ALL)))
    }

    @Test fun anItemCannotBeUsedInTheWrongSection() {
        assertTrue(MacroCatalog.validate(item("toast", "text" to "hi"), MacroSection.TRIGGER)!!.contains("action"))
    }

    @Test fun summaryShowsTheValues() {
        assertEquals("Battery Level Below: 20", MacroCatalog.summary(item("battery_below", "level" to "20")))
        assertEquals("Screen On", MacroCatalog.summary(item("screen_on")))
    }

    @Test fun variableNamesAreChecked() {
        assertNotNull(MacroCatalog.validate(item("ask_ai", "prompt" to "hi", "variable" to "bad name")))
        assertNull(MacroCatalog.validate(item("ask_ai", "prompt" to "hi", "variable" to "answer")))
    }

    @Test fun aiAndMacroDroidSpecificCategoriesExistForActions() {
        assertTrue("AI" in MacroCatalog.categories(MacroSection.ACTION))
        assertTrue("MacroDroid Specific" in MacroCatalog.categories(MacroSection.ACTION))
        assertTrue("MacroDroid Specific" in MacroCatalog.categories(MacroSection.TRIGGER))
        assertTrue("MacroDroid Specific" in MacroCatalog.categories(MacroSection.CONSTRAINT))
    }
}
