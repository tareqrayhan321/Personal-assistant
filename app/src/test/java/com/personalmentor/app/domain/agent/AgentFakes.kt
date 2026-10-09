package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.browser.BrowserController
import com.personalmentor.app.domain.browser.BrowserPage
import com.personalmentor.app.domain.browser.PageElement
import com.personalmentor.app.domain.github.GitHubApi
import com.personalmentor.app.domain.github.GitHubResponse
import com.personalmentor.app.domain.model.AgentSettings
import com.personalmentor.app.domain.model.ApprovalMode
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Answers every question with [answer] and remembers what was asked. */
class RecordingApprover(var answer: Boolean = true) : ActionApprover {
    data class Asked(val title: String, val detail: String, val level: ApprovalLevel)

    val asked = mutableListOf<Asked>()

    override suspend fun confirm(title: String, detail: String, level: ApprovalLevel): Boolean {
        asked += Asked(title, detail, level)
        return answer
    }
}

class FakeGitHubApi : GitHubApi {
    data class Call(val method: String, val path: String, val query: Map<String, String>, val body: JsonObject?)

    val calls = mutableListOf<Call>()
    private val responses = mutableMapOf<String, GitHubResponse>()

    /** Registers the answer for `METHOD path`, e.g. `reply("GET", "repos/me/app/contents/a.txt", ...)`. */
    fun reply(method: String, path: String, code: Int, json: String? = null) {
        responses["$method $path"] = GitHubResponse(code, json?.let { Json.parseToJsonElement(it) })
    }

    override suspend fun call(method: String, segments: List<String>, query: Map<String, String>, body: JsonObject?): GitHubResponse {
        val path = segments.joinToString("/")
        calls += Call(method, path, query, body)
        return responses["$method $path"] ?: GitHubResponse(404, null)
    }
}

class FakeBrowser : BrowserController {
    var elements: Map<Int, PageElement> = emptyMap()
    val actions = mutableListOf<String>()

    private fun page() = BrowserPage(url = "https://shop.example/cart", title = "Cart", elements = elements.values.toList())

    override suspend fun open(url: String): BrowserPage { actions += "open $url"; return page() }
    override suspend fun read(): BrowserPage = page()
    override suspend fun click(id: Int): BrowserPage { actions += "click $id"; return page() }
    override suspend fun type(id: Int, text: String, submit: Boolean): BrowserPage { actions += "type $id $text $submit"; return page() }
    override suspend fun select(id: Int, option: String): BrowserPage { actions += "select $id $option"; return page() }
    override suspend fun scroll(direction: String): BrowserPage { actions += "scroll $direction"; return page() }
    override suspend fun back(): BrowserPage { actions += "back"; return page() }
    override fun elementInfo(id: Int): PageElement? = elements[id]
    override fun currentUrl(): String? = "https://shop.example/cart"
}

class FakeAgentSettings(mode: ApprovalMode) : AgentSettingsRepository {
    private val state = MutableStateFlow(AgentSettings(approvalMode = mode))
    override val settings: StateFlow<AgentSettings> = state
    override fun save(settings: AgentSettings) { state.value = settings }
}
