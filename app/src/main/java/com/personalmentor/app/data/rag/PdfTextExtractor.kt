package com.personalmentor.app.data.rag

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text extraction from PDFs that contain a text layer (PdfBox-Android). Scanned or image-only PDFs
 * have no text layer and are rejected: there is no OCR for Bengali on-device.
 */
@Singleton
class PdfTextExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    fun extract(bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context)
        try {
            PDDocument.load(bytes).use { doc ->
                val stripper = PDFTextStripper().apply { paragraphEnd = "\n\n"; pageEnd = "\n\n" }
                val text = stripper.getText(doc).replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
                if (text.length < MIN_TEXT_CHARS) {
                    throw IOException("This PDF has no readable text (it may be scanned). OCR is not supported.")
                }
                return text
            }
        } catch (e: InvalidPasswordException) {
            throw IOException("This PDF is password-protected.", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("This PDF could not be read.", e)
        }
    }

    private companion object {
        const val MIN_TEXT_CHARS = 20
    }
}
