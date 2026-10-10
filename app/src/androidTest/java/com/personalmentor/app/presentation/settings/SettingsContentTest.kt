package com.personalmentor.app.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.personalmentor.app.domain.model.LlmSettings
import com.personalmentor.app.presentation.theme.PersonalMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule val rule = createComposeRule()

    private val saved = LlmSettings("https://api.openai.com/", "", "gpt-4o-mini", "text-embedding-3-small")

    private class Calls {
        var preset: ProviderPreset? = null
        var model: String? = null
        var saves = 0
        var resets = 0
    }

    private fun show(form: LlmSettings = saved, calls: Calls = Calls()) = rule.setContent {
        PersonalMentorTheme(dynamicColor = false) {
            SettingsContent(
                state = SettingsUiState(form = form, saved = saved),
                onBack = {},
                onPreset = { calls.preset = it },
                onBaseUrl = {}, onApiKey = {},
                onModel = { calls.model = it },
                onEmbeddingModel = {},
                onSave = { calls.saves++ },
                onReset = { calls.resets++ },
            )
        }
    }

    @Test fun saveIsDisabledUntilSomethingChanges() {
        show()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun saveIsEnabledForAValidChange() {
        show(form = saved.copy(model = "gpt-4o"))
        rule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test fun invalidUrlShowsAnErrorAndBlocksSaving() {
        show(form = saved.copy(baseUrl = "not a url"))
        rule.onNodeWithText("Enter a valid http(s) URL").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun blankModelBlocksSaving() {
        show(form = saved.copy(model = ""))
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun presetsSaveAndResetAreForwarded() {
        val calls = Calls()
        show(form = saved.copy(model = "gpt-4o"), calls = calls)
        rule.onNodeWithText("OpenRouter").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Reset to defaults").performClick()
        assertEquals("OpenRouter", calls.preset?.label)
        assertEquals(1, calls.saves)
        assertEquals(1, calls.resets)
    }

    @Test fun customEndpointStillAllowsTypingAModelName() {
        val calls = Calls()
        show(form = saved.copy(baseUrl = "http://10.0.2.2:11434/", model = "llama3.1"), calls = calls)
        rule.onNodeWithText("Chat model").performTextReplacement("llama3.2")
        assertEquals("llama3.2", calls.model)
    }

    @Test fun knownProviderPicksAModelFromTheList() {
        val calls = Calls()
        rule.setContent {
            PersonalMentorTheme(dynamicColor = false) {
                SettingsContent(
                    state = SettingsUiState(
                        form = saved, saved = saved,
                        models = ModelListState(items = listOf("gpt-4o-mini", "gpt-4o")),
                    ),
                    onBack = {}, onPreset = {}, onBaseUrl = {}, onApiKey = {},
                    onModel = { calls.model = it }, onEmbeddingModel = {}, onSave = {}, onReset = {},
                )
            }
        }
        rule.onNodeWithText("Chat model").performClick()
        rule.onNodeWithText("gpt-4o").performClick()
        assertEquals("gpt-4o", calls.model)
    }
}
