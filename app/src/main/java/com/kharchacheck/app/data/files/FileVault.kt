package com.kharchacheck.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted file storage for receipts, statements and the profile photo.
 * Each file: 12-byte IV + AES-256-GCM ciphertext, key held by Android Keystore (non-exportable).
 * Lives in noBackupFilesDir: never copied by cloud backup, never visible to other apps.
 */
class FileVault(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val alias = "kharcha_files_v1"

    class TooLargeException(val limitMb: Int) : Exception("File is larger than $limitMb MB")
    class UnsupportedTypeException : Exception("Only photos, PDFs and CSV statement files can be attached")

    data class Stored(val fileName: String, val mime: String, val sizeBytes: Long, val originalName: String)

    /**
     * Copies a picked file in, encrypted. Large photos are downscaled (screenshots stay readable)
     * to keep storage small. PDFs are kept exactly as they are.
     */
    suspend fun import(uri: Uri, maxMb: Int = 25, imageMaxPx: Int = 2200, fileName: String? = null): Stored = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: guessMime(uri)
        if (!(mime.startsWith("image/") || mime == "application/pdf" || mime in SHEETS)) throw UnsupportedTypeException()
        val original = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()

        val limit = maxMb * 1_048_576L
        val raw = cr.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf); if (n < 0) break
                total += n
                if (total > limit) throw TooLargeException(maxMb)
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("Couldn't read the file")

        var bytes = raw
        var outMime = mime
        if (mime.startsWith("image/")) {
            shrink(raw, imageMaxPx)?.let { bytes = it; outMime = "image/webp" }
        }
        val name = fileName ?: (UUID.randomUUID().toString() + ".bin")
        write(name, bytes)
        Stored(name, outMime, bytes.size.toLong(), original)
    }

    suspend fun read(fileName: String): ByteArray = withContext(Dispatchers.IO) {
        val blob = File(dir, fileName).readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        cipher.doFinal(blob, 12, blob.size - 12)
    }

    fun exists(fileName: String) = File(dir, fileName).exists()
    fun delete(fileName: String) { File(dir, fileName).delete() }
    /** Re-saves a file (encrypted) with new contents, e.g. a screenshot shrunk to WebP. */
    suspend fun replace(fileName: String, plain: ByteArray) = withContext(Dispatchers.IO) { write(fileName, plain) }
    /** Bytes on disk for everything in the vault (attachments + profile photo). */
    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
    fun wipe() { dir.listFiles()?.forEach { it.delete() } }

    // ---- Restore staging: files from a backup land here first and only replace the real vault once the
    // whole backup has been read and applied, so a damaged backup can never wipe the current files.
    private val staging = File(context.noBackupFilesDir, "vault_restore")
    fun beginRestore() { staging.deleteRecursively(); staging.mkdirs() }
    fun stage(fileName: String, plain: ByteArray) {
        require(Regex("""[A-Za-z0-9._-]{1,80}""").matches(fileName)) { "Bad file name in backup" }
        write(fileName, plain, staging)
    }
    fun commitRestore() {
        wipe()
        staging.listFiles()?.forEach { f -> val dest = File(dir, f.name); if (!f.renameTo(dest)) { f.copyTo(dest, overwrite = true); f.delete() } }
        staging.deleteRecursively()
    }
    fun abortRestore() { staging.deleteRecursively() }

    private fun write(fileName: String, plain: ByteArray, into: File = dir) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val tmp = File(into, "$fileName.tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(plain))
        val dest = File(into, fileName)
        dest.delete()
        if (!tmp.renameTo(dest)) { tmp.delete(); error("Couldn't save the file") }
    }

    private fun guessMime(uri: Uri): String {
        val p = uri.toString().lowercase()
        return when {
            p.endsWith(".pdf") -> "application/pdf"
            p.endsWith(".png") -> "image/png"
            p.endsWith(".jpg") || p.endsWith(".jpeg") -> "image/jpeg"
            p.endsWith(".webp") -> "image/webp"
            p.endsWith(".csv") -> "text/csv"
            else -> "application/octet-stream"
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    companion object {
        const val PROFILE = "profile.bin"
        /** Statement downloads from netbanking. CSV/TSV can be read; Excel files are kept for reference. */
        val SHEETS = setOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "application/csv",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

        /**
         * Re-encodes a photo or screenshot as WebP (quality 80) within [maxPx]. A phone screenshot saved as PNG
         * is often 0.5 to 1.5 MB; as WebP it is usually 80 to 250 KB and still sharp enough to read and OCR.
         * Returns null when the image can't be decoded or the result wouldn't be at least 10% smaller.
         */
        fun shrink(bytes: ByteArray, maxPx: Int): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth; val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            val bmp = decodeSampled(bytes, maxPx) ?: return null
            val scale = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            val out = ByteArrayOutputStream().use { o -> scaled.compress(format, 80, o); o.toByteArray() }
            return out.takeIf { it.isNotEmpty() && it.size < bytes.size * 0.9 }
        }

        /** Memory-safe decode for thumbnails and viewers. */
        fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
