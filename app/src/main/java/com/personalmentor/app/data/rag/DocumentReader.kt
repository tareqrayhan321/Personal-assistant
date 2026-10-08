package com.personalmentor.app.data.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** Reads user-picked documents (txt, md, csv, json, pdf, docx) through the Storage Access Framework. */
class DocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdf: PdfTextExtractor,
) {

    suspend fun displayName(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "document"
    }

    suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val bytes = readBytes(Uri.parse(uriString))
        val text = when (FileKind.detect(bytes)) {
            FileKind.PDF -> pdf.extract(bytes)
            FileKind.ZIP -> DocxTextExtractor.extract(bytes)
            FileKind.TEXT -> {
                if (bytes.size > MAX_TEXT_BYTES) throw IOException("Text files can be at most 2 MB")
                // Other binary formats contain NUL bytes early on.
                if (bytes.take(4096).any { it == 0.toByte() }) throw IOException(UNSUPPORTED_FILE_MESSAGE)
                String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            }
        }
        if (text.length > MAX_CHARS) throw IOException("The document is too long (over 2,000,000 characters)")
        text
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_FILE_BYTES) throw IOException("File is larger than 20 MB")
            }
            out.toByteArray()
        } ?: throw IOException("Cannot open file")

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_CHARS = 2_000_000
    }
}
