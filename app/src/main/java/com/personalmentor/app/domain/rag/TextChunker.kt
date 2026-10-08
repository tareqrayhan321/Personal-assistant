package com.personalmentor.app.domain.rag

/**
 * Splits text into overlapping chunks that respect paragraph and sentence boundaries
 * (including the Bengali danda "।").
 */
object TextChunker {
    const val DEFAULT_MAX_CHARS = 900
    const val DEFAULT_OVERLAP = 150

    private val sentenceBreak = Regex("(?<=[.!?।॥])\\s+")
    private val paragraphBreak = Regex("\\n\\s*\\n")

    fun chunk(
        text: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
        overlapChars: Int = DEFAULT_OVERLAP,
    ): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        require(overlapChars in 0 until maxChars) { "overlapChars must be in [0, maxChars)" }

        val units = text.replace("\r\n", "\n").replace('\r', '\n')
            .split(paragraphBreak)
            .flatMap { paragraph ->
                val p = paragraph.trim().replace(Regex("[ \\t]*\\n[ \\t]*"), " ")
                when {
                    p.isEmpty() -> emptyList()
                    p.length <= maxChars -> listOf(p)
                    else -> p.split(sentenceBreak).filter { it.isNotBlank() }.flatMap { hardSplit(it.trim(), maxChars) }
                }
            }

        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        for (unit in units) {
            if (cur.isNotEmpty() && cur.length + 1 + unit.length > maxChars) {
                chunks += cur.toString().trim()
                val tail = overlapTail(cur.toString(), overlapChars)
                cur.clear()
                cur.append(tail)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(unit)
        }
        if (cur.isNotBlank()) chunks += cur.toString().trim()
        return chunks
    }

    /** Cuts an over-long unit at whitespace (or at [max] if there is none), never inside a surrogate pair. */
    private fun hardSplit(unit: String, max: Int): List<String> {
        if (unit.length <= max) return listOf(unit)
        val out = mutableListOf<String>()
        var rest = unit
        while (rest.length > max) {
            var cut = rest.lastIndexOf(' ', max)
            if (cut < max / 2) {
                cut = max
                if (rest[cut - 1].isHighSurrogate()) cut -= 1
            }
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun overlapTail(s: String, n: Int): String {
        if (n <= 0 || s.length <= n) return ""
        var start = s.length - n
        val space = s.indexOf(' ', start)
        if (space != -1 && space + 1 < s.length) start = space + 1
        return s.substring(start)
    }
}
