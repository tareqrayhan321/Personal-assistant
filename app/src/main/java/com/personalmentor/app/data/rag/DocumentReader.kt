package com.personalmentor.app.data.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Reads user-picked plain-text files (txt, md, csv, json, ...) through the Storage Access Framework. */
class DocumentReader @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun displayName(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "document"
    }

    suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) throw IOException("File is larger than 2 MB")
            }
            out.toByteArray()
        } ?: throw IOException("Cannot open file")

        // Binary formats (PDF, DOCX, images) contain NUL bytes early on.
        if (bytes.take(4096).any { it == 0.toByte() }) {
            throw IOException("Not a plain-text file. Only text formats (txt, md, csv, json) are supported.")
        }
        String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
