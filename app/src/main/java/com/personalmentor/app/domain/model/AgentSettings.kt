package com.personalmentor.app.domain.model

/** How often the agent must ask the user before acting. */
enum class ApprovalMode(val label: String, val help: String) {
    ALL("Ask before every action", "Every browser click/typing and every GitHub change needs your OK."),
    SENSITIVE(
        "Ask for sensitive actions",
        "Asks before GitHub changes and before browser actions that look irreversible (buy, pay, delete, send, submit…).",
    ),
    NONE("Never ask (risky)", "The agent acts on its own. A malicious web page could misuse this."),
}

/** Settings of Agent Mode, kept apart from [LlmSettings] (which is tied to the LLM provider). */
data class AgentSettings(
    val githubToken: String = "",
    val approvalMode: ApprovalMode = ApprovalMode.SENSITIVE,
)
