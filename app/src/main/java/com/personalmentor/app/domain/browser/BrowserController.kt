package com.personalmentor.app.domain.browser

import kotlinx.serialization.Serializable

/** One clickable / editable thing on the page, numbered for the model. Ids are only valid for the snapshot they came from. */
@Serializable
data class PageElement(
    val id: Int,
    val tag: String,
    val type: String? = null,
    val text: String = "",
    val href: String? = null,
    val value: String? = null,
    val checked: Boolean? = null,
    /** Password, card or one-time-code field: the agent must never fill these. */
    val secret: Boolean? = null,
)

@Serializable
data class BrowserPage(
    val url: String,
    val title: String = "",
    val text: String = "",
    val elements: List<PageElement> = emptyList(),
    val scrollY: Int = 0,
    val pageHeight: Int = 0,
    /** Set when the main page failed to load (offline, TLS error, blocked cleartext…). */
    val error: String? = null,
)

class BrowserException(message: String) : Exception(message)

/** The in-app browser the agent drives. All calls return the page as it is after the action. */
interface BrowserController {
    suspend fun open(url: String): BrowserPage
    suspend fun read(): BrowserPage
    suspend fun click(id: Int): BrowserPage
    suspend fun type(id: Int, text: String, submit: Boolean): BrowserPage
    suspend fun select(id: Int, option: String): BrowserPage

    /** [direction]: "up", "down", "top" or "bottom". */
    suspend fun scroll(direction: String): BrowserPage
    suspend fun back(): BrowserPage

    /** Element from the latest snapshot, or null if the id is stale/unknown. */
    fun elementInfo(id: Int): PageElement?
    fun currentUrl(): String?
}
