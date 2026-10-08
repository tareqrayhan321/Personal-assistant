package com.personalmentor.app.data.rag

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** What a picked file really is, judged by its first bytes (file names and MIME types are unreliable). */
enum class FileKind {
    PDF, ZIP, TEXT;

    companion object {
        fun detect(bytes: ByteArray): FileKind = when {
            bytes.size >= 5 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() && bytes[4] == '-'.code.toByte() -> PDF
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
                bytes[2] == 3.toByte() && bytes[3] == 4.toByte() -> ZIP
            else -> TEXT
        }
    }
}

const val UNSUPPORTED_FILE_MESSAGE = "Unsupported file type. Supported: txt, md, csv, json, pdf, docx."

/** Plain-text extraction from .docx (body text only: no headers, footers, footnotes or text boxes). */
object DocxTextExtractor {
    private const val MAX_XML_BYTES = 50 * 1024 * 1024

    private val token = Regex(
        "<w:p[ >/]|</w:p>|<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab\\s*/>|<w:br\\b[^>]*/>|<w:cr\\s*/>"
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

    fun extract(bytes: ByteArray): String {
        val xml = readDocumentXml(bytes) ?: throw IOException(UNSUPPORTED_FILE_MESSAGE)
        val text = xmlToText(xml)
        if (text.isBlank()) throw IOException("The document contains no text.")
        return text
    }

    internal fun xmlToText(xml: String): String {
        val out = StringBuilder()
        for (m in token.findAll(xml)) {
            val t = m.value
            when {
                t.startsWith("</w:p") -> out.append("\n\n")
                t.startsWith("<w:p") -> Unit
                t.startsWith("<w:tab") -> out.append(' ')
                t.startsWith("<w:br") || t.startsWith("<w:cr") -> out.append('\n')
                else -> out.append(decode(m.groupValues[1]))
            }
        }
        return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun decode(s: String): String = entity.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> {
                val cp = if (e.startsWith("#x")) e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
            }
        }
    }

    private fun readDocumentXml(bytes: ByteArray): String? = try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "word/document.xml") entry = zip.nextEntry
            if (entry == null) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_XML_BYTES) throw IOException("The document is too large to read.")
            }
            out.toString(Charsets.UTF_8.name())
        }
    } catch (e: java.util.zip.ZipException) {
        throw IOException("Not a valid .docx file.", e)
    }
}
