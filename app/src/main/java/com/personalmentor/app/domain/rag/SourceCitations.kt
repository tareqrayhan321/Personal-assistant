package com.personalmentor.app.domain.rag

import com.personalmentor.app.domain.model.RetrievedChunk

object SourceCitations {
    private val marker = Regex("\\[(\\d+(?:\\s*,\\s*\\d+)*)]")

    /** 1-based source numbers the answer actually cites (e.g. "[1]", "[2, 3]"), ascending, limited to 1..[count]. */
    fun citedNumbers(answer: String, count: Int): List<Int> =
        marker.findAll(answer)
            .flatMap { it.groupValues[1].split(',').map(String::trim) }
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..count }
            .distinct()
            .sorted()
            .toList()

    /** "Sources:" footer listing only the cited sources; empty when the answer cites none. */
    fun footer(answer: String, sources: List<RetrievedChunk>): String {
        val cited = citedNumbers(answer, sources.size)
        if (cited.isEmpty()) return ""
        return cited.joinToString(separator = "\n", prefix = "\n\nSources:\n") { n ->
            val s = sources[n - 1]
            "[$n] ${s.documentName} (part ${s.ordinal + 1})"
        }
    }
}
