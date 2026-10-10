package com.personalmentor.app.domain.computer

object CloudComputerUrl {
    private val HTTPS = Regex("^https://[^\\s/?#]+(/[^\\s?#]*)?$", RegexOption.IGNORE_CASE)
    private val LOCAL_HTTP = Regex("^http://(localhost|10\\.0\\.2\\.2)(:\\d+)?(/[^\\s?#]*)?$", RegexOption.IGNORE_CASE)

    /** The URL without a trailing slash, or null if it is not acceptable (the app only talks https, plus local dev hosts). */
    fun normalize(raw: String): String? {
        val url = raw.trim().trimEnd('/')
        return url.takeIf { HTTPS.matches(it) || LOCAL_HTTP.matches(it) }
    }
}
