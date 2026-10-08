package com.personalmentor.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmSettingsTest {
    private fun s(url: String, key: String) = LlmSettings(url, key, "m", "e")

    @Test fun noKeyOnDefaultEndpointUsesDemo() {
        assertEquals(true, s("https://api.openai.com/", "").useFake)
        assertEquals(true, s(" https://api.openai.com ", " ").useFake)
    }

    @Test fun keyOrCustomEndpointUsesRealLlm() {
        assertEquals(false, s("https://api.openai.com/", "sk-x").useFake)
        assertEquals(false, s("http://10.0.2.2:11434/", "").useFake)
    }
}
