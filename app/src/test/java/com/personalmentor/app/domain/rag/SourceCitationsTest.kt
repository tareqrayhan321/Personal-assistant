package com.personalmentor.app.domain.rag

import com.personalmentor.app.domain.model.RetrievedChunk
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceCitationsTest {

    private fun chunk(name: String, ordinal: Int) = RetrievedChunk(1, name, ordinal, "text", 0.5f)

    private val sources = listOf(chunk("a.md", 0), chunk("b.txt", 4), chunk("c.md", 1))

    @Test fun findsSingleAndGroupedMarkers() {
        assertEquals(listOf(1, 3), SourceCitations.citedNumbers("See [1] and also [3].", 3))
        assertEquals(listOf(1, 2), SourceCitations.citedNumbers("Both [1, 2] agree.", 3))
    }

    @Test fun ignoresOutOfRangeAndDuplicates() {
        assertEquals(listOf(2), SourceCitations.citedNumbers("[2] [2] [9] [0]", 3))
    }

    @Test fun footerListsOnlyCitedSources() {
        val footer = SourceCitations.footer("Answer [2].", sources)
        assertEquals("\n\nSources:\n[2] b.txt (part 5)", footer)
    }

    @Test fun noCitationMeansNoFooter() {
        assertEquals("", SourceCitations.footer("General answer.", sources))
    }
}
