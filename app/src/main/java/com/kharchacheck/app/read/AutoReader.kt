package com.kharchacheck.app.read

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.AttachmentKind
import com.kharchacheck.app.data.files.FileVault
import com.kharchacheck.app.domain.read.ReceiptParser
import com.kharchacheck.app.domain.read.StatementParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.coroutines.coroutineContext

/**
 * Reads screenshots and statements on the phone. Results are suggestions the user reviews before saving.
 *
 *  - Spreadsheets (.xlsx, HTML ".xls", CSV/TSV): parsed directly, the most accurate path.
 *  - Digital PDFs: exact text via PdfBox; password-protected files work on every Android version.
 *  - Scanned PDF pages and photos: OCR (Tesseract), page by page.
 */
class AutoReader(private val context: Context, private val ocr: OcrEngine) {

    sealed interface Outcome {
        data class Rows(val result: StatementParser.Result) : Outcome
        data class NeedsPassword(val wrong: Boolean) : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun receipt(bytes: ByteArray): ReceiptParser.Result? = try {
        val bmp = withContext(Dispatchers.Default) { FileVault.decodeSampled(bytes, 2400) }
        if (bmp == null) null else ReceiptParser.parse(ocr.read(prepare(bmp), OcrEngine.Layout.RECEIPT))
    } finally { ocr.release() }

    suspend fun statement(a: Attachment, bytes: ByteArray, password: String?, onProgress: (page: Int, total: Int) -> Unit): Outcome {
        val month = a.month ?: YearMonth.now()
        val card = a.kind == AttachmentKind.CARD_STATEMENT
        return try {
            when {
                a.isSheet -> withContext(Dispatchers.Default) {
                    try { Outcome.Rows(StatementParser.fromSpreadsheet(bytes, month, card)) }
                    catch (e: StatementParser.NotReadableException) { Outcome.Failed(e.message.orEmpty()) }
                }
                a.isImage -> {
                    onProgress(1, 1)
                    val bmp = withContext(Dispatchers.Default) { FileVault.decodeSampled(bytes, 3200) }
                    if (bmp == null) Outcome.Failed("This image couldn't be opened.")
                    else Outcome.Rows(StatementParser.fromLines(ocr.read(prepare(bmp), OcrEngine.Layout.TABLE).lines(), month, card.takeIf { it }))
                }
                else -> pdf(bytes, password, month, card, onProgress)
            }
        } finally { ocr.release() }
    }

    private suspend fun pdf(bytes: ByteArray, password: String?, month: YearMonth, card: Boolean, onProgress: (Int, Int) -> Unit): Outcome {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            withContext(Dispatchers.IO) { PDDocument.load(bytes, password ?: "") }
        } catch (e: InvalidPasswordException) {
            return Outcome.NeedsPassword(wrong = password != null)
        } catch (e: Exception) {
            return Outcome.Failed("This PDF couldn't be opened. If your bank offers a CSV or Excel download, attach that instead.")
        }
        doc.use { d ->
            val total = minOf(d.numberOfPages, 30)
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val renderer = PDFRenderer(d)

            suspend fun ocrPage(i: Int): List<String> {
                val bmp = withContext(Dispatchers.IO) { renderer.renderImage(i, 3f, ImageType.RGB) }
                val prepared = prepare(bmp)
                val out = ocr.read(prepared, OcrEngine.Layout.TABLE).lines()
                if (prepared !== bmp) prepared.recycle()
                bmp.recycle()
                return out
            }

            val lines = mutableListOf<String>()
            var textPages = 0
            for (i in 0 until total) {
                coroutineContext.ensureActive()
                onProgress(i + 1, total)
                val text = withContext(Dispatchers.IO) {
                    runCatching { stripper.startPage = i + 1; stripper.endPage = i + 1; stripper.getText(d) }.getOrDefault("")
                }
                if (readable(text)) { lines += text.lines(); textPages++ } else lines += ocrPage(i)
            }
            val first = StatementParser.fromLines(lines, month, card.takeIf { it })
            if (first.rows.isNotEmpty() || textPages == 0) return Outcome.Rows(first)

            // Text came out but nothing parsed (fonts without proper character maps): read the pages visually instead.
            val ocrLines = mutableListOf<String>()
            for (i in 0 until total) { coroutineContext.ensureActive(); onProgress(i + 1, total); ocrLines += ocrPage(i) }
            return Outcome.Rows(StatementParser.fromLines(ocrLines, month, card.takeIf { it }))
        }
    }

    /** True when a page's extracted text looks like real content rather than an empty scanned page. */
    private fun readable(text: String): Boolean = text.count(Char::isLetter) >= 40 && text.any(Char::isDigit)

    /** Greyscale, invert dark-mode screenshots, and upscale small images: all help OCR accuracy. */
    private suspend fun prepare(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val scale = if (src.width < 1000) 2f else 1f
        val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
        val dark = meanLuminance(src) < 110
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        if (dark) cm.postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).apply { scale(scale, scale); drawBitmap(src, 0f, 0f, paint) }
        out
    }

    private fun meanLuminance(b: Bitmap): Int {
        val stepX = maxOf(1, b.width / 40); val stepY = maxOf(1, b.height / 40)
        var sum = 0L; var n = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val c = b.getPixel(x, y)
                sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)).toInt(); n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 255 else (sum / n).toInt()
    }
}
