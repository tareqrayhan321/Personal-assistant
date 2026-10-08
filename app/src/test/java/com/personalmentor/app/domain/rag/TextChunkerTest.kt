package com.personalmentor.app.domain.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test fun blankTextGivesNoChunks() {
        assertTrue(TextChunker.chunk("  \n\n  ").isEmpty())
    }

    @Test fun shortTextIsOneChunk() {
        assertEquals(listOf("Hello world."), TextChunker.chunk("Hello world."))
    }

    @Test fun chunksStayNearTheLimitAndKeepEverySentence() {
        val sentences = (1..60).map { "Sentence number $it is here." }
        val chunks = TextChunker.chunk(sentences.joinToString(" "), maxChars = 200, overlapChars = 40)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue("chunk too long: ${it.length}", it.length <= 200 + 40 + 1) }
        val joined = chunks.joinToString(" ")
        sentences.forEach { assertTrue(it, joined.contains(it)) }
    }

    @Test fun consecutiveChunksOverlap() {
        val text = (1..40).joinToString(" ") { "word$it." }
        val chunks = TextChunker.chunk(text, maxChars = 100, overlapChars = 30)
        for (i in 1 until chunks.size) {
            val tailWord = chunks[i - 1].split(' ').last()
            assertTrue("chunk $i should start with overlap from the previous one", chunks[i].contains(tailWord))
        }
    }

    @Test fun bengaliDandaIsASentenceBoundary() {
        val sentence = "আমি বাংলায় গান গাই।"
        val text = List(30) { sentence }.joinToString(" ")
        val chunks = TextChunker.chunk(text, maxChars = 120, overlapChars = 0)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.endsWith("।")) }
    }

    @Test fun textWithoutSpacesIsHardSplit() {
        val chunks = TextChunker.chunk("x".repeat(2500), maxChars = 1000, overlapChars = 0)
        assertEquals(listOf(1000, 1000, 500), chunks.map { it.length })
    }

    @Test fun hardSplitNeverCutsASurrogatePair() {
        val chunks = TextChunker.chunk("😀".repeat(600), maxChars = 101, overlapChars = 0)
        chunks.forEach { assertTrue(it.length % 2 == 0) }
    }
}
