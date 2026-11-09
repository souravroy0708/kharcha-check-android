package com.kharchacheck.app.read

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Text recognition behind one small interface, so the engine can be swapped (e.g. for ML Kit) in one file. */
interface OcrEngine {
    enum class Layout { RECEIPT, TABLE }
    suspend fun read(bitmap: Bitmap, layout: Layout): String
    fun stop()
    suspend fun release()
}

/**
 * Tesseract, fully offline. The English model ships inside the app (assets/tessdata) and is copied once
 * into private storage. No network, no Google services, no telemetry.
 */
class TesseractOcr(private val context: Context) : OcrEngine {
    private val lock = Mutex()
    @Volatile private var api: TessBaseAPI? = null

    private fun ensure(): TessBaseAPI {
        api?.let { return it }
        val base = File(context.noBackupFilesDir, "tesseract")
        val dir = File(base, "tessdata").apply { mkdirs() }
        val model = File(dir, "eng.traineddata")
        val expected = runCatching { context.assets.openFd("tessdata/eng.traineddata").use { it.length } }.getOrDefault(-1L)
        if (!model.exists() || (expected > 0 && model.length() != expected)) {
            val tmp = File(dir, "eng.traineddata.tmp")
            context.assets.open("tessdata/eng.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            model.delete(); tmp.renameTo(model)
        }
        val t = TessBaseAPI()
        if (!t.init(base.absolutePath, "eng", TessBaseAPI.OEM_LSTM_ONLY)) { t.recycle(); error("The reading engine couldn't start") }
        api = t
        return t
    }

    override suspend fun read(bitmap: Bitmap, layout: OcrEngine.Layout): String = lock.withLock {
        withContext(Dispatchers.Default) {
            val t = ensure()
            t.setPageSegMode(if (layout == OcrEngine.Layout.TABLE) TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK else TessBaseAPI.PageSegMode.PSM_AUTO)
            t.setVariable("preserve_interword_spaces", "1")
            t.setImage(bitmap)
            val text = t.getUTF8Text().orEmpty()
            t.clear()
            text
        }
    }

    override fun stop() { api?.stop() }

    /** Frees the native engine (tens of MB) after a reading session. */
    override suspend fun release() = lock.withLock { api?.recycle(); api = null }
}
