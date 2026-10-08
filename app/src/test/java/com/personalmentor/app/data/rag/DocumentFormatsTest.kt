package com.personalmentor.app.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentFormatsTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun docx(body: String) = zip(
        "[Content_Types].xml" to "<Types/>",
        "word/document.xml" to """<?xml version="1.0"?><w:document xmlns:w="x"><w:body>$body</w:body></w:document>""",
    )

    private fun p(vararg runs: String) = "<w:p><w:pPr><w:pStyle w:val=\"A\"/></w:pPr>" + runs.joinToString("") { "<w:r>$it</w:r>" } + "</w:p>"
    private fun t(s: String) = "<w:t xml:space=\"preserve\">$s</w:t>"

    // ---- file kind ----

    @Test fun detectsPdfZipAndText() {
        assertEquals(FileKind.PDF, FileKind.detect("%PDF-1.7 rest".toByteArray()))
        assertEquals(FileKind.ZIP, FileKind.detect(byteArrayOf(0x50, 0x4B, 3, 4, 0)))
        assertEquals(FileKind.TEXT, FileKind.detect("# notes".toByteArray()))
        assertEquals(FileKind.TEXT, FileKind.detect(ByteArray(0)))
        assertEquals(FileKind.TEXT, FileKind.detect("%PD".toByteArray()))
    }

    // ---- docx ----

    @Test fun paragraphsAreSeparatedByBlankLines() {
        val text = DocxTextExtractor.extract(docx(p(t("First paragraph.")) + p(t("Second "), t("paragraph, two runs."))))
        assertEquals("First paragraph.\n\nSecond paragraph, two runs.", text)
    }

    @Test fun bengaliTextSurvives() {
        val text = DocxTextExtractor.extract(docx(p(t("আমার সোনার বাংলা।"))))
        assertEquals("আমার সোনার বাংলা।", text)
    }

    @Test fun entitiesAreDecoded() {
        val text = DocxTextExtractor.extract(docx(p(t("A &amp; B &lt;c&gt; &quot;d&quot; &#65;&#x42;"))))
        assertEquals("A & B <c> \"d\" AB", text)
    }

    @Test fun tabsAndLineBreaksBecomeWhitespace() {
        val text = DocxTextExtractor.extract(docx(p(t("a"), "<w:tab/>", t("b"), "<w:br/>", t("c"))))
        assertEquals("a b\nc", text)
    }

    @Test fun deletedTrackedTextAndFormattingTagsAreIgnored() {
        val text = DocxTextExtractor.extract(docx(p("<w:delText>gone</w:delText>", t("kept"))))
        assertEquals("kept", text)
    }

    @Test fun tableCellsKeepTheirText() {
        val table = "<w:tbl><w:tr><w:tc>${p(t("Name"))}</w:tc><w:tc>${p(t("Qty"))}</w:tc></w:tr></w:tbl>"
        val text = DocxTextExtractor.extract(docx(table))
        assertTrue(text.contains("Name") && text.contains("Qty"))
    }

    @Test fun emptyDocumentIsRejected() {
        val e = runCatching { DocxTextExtractor.extract(docx(p())) }.exceptionOrNull()
        assertTrue(e is IOException)
        assertEquals("The document contains no text.", e?.message)
    }

    @Test fun otherZipFilesAreUnsupported() {
        val e = runCatching { DocxTextExtractor.extract(zip("xl/workbook.xml" to "<x/>")) }.exceptionOrNull()
        assertEquals(UNSUPPORTED_FILE_MESSAGE, e?.message)
    }

    @Test fun corruptZipIsAnIoError() {
        val e = runCatching { DocxTextExtractor.extract(byteArrayOf(0x50, 0x4B, 3, 4, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9)) }.exceptionOrNull()
        assertTrue(e is IOException)
    }
}
