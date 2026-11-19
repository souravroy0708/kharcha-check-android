package com.kharchacheck.app.data

import android.content.Context
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.repo.AttachmentRepository
import com.kharchacheck.app.data.repo.WealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything Kharcha Check keeps on the phone, measured and kept small.
 *
 * What takes space, in the order it usually grows:
 *  1. Screenshots and statement files (encrypted vault). Images are saved as WebP; old ones can be shrunk.
 *  2. The fund-price list for offline search (gzipped, ~0.5 MB, only if you track mutual funds).
 *  3. The text-reading model for screenshots (~4 MB, fixed, needed for offline reading).
 *  4. Your entries (SQLCipher database). Tiny: about 100 bytes per entry, so 10 entries a day is ~0.4 MB a year.
 *
 * Nothing here needs the internet.
 */
class StorageManager(
    private val context: Context,
    private val db: KharchaDatabase,
    private val files: AttachmentRepository,
    private val wealth: WealthRepository,
) {
    data class Usage(
        val databaseBytes: Long,
        val filesBytes: Long,
        val receipts: Int,
        val statements: Int,
        val pricesBytes: Long,
        val readerBytes: Long,
        val tempBytes: Long,
        val entries: Int,
    ) {
        val totalBytes: Long get() = databaseBytes + filesBytes + pricesBytes + readerBytes + tempBytes
    }

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        val all = db.attachments().all()
        Usage(
            databaseBytes = databaseBytes(),
            filesBytes = files.vaultBytes(),
            receipts = all.count { it.kind == "RECEIPT" || it.kind == "OTHER" || it.kind == "TRIP_PHOTO" },
            statements = all.count { it.kind != "RECEIPT" && it.kind != "OTHER" && it.kind != "TRIP_PHOTO" },
            pricesBytes = wealth.cacheBytes,
            readerBytes = sizeOf(File(context.noBackupFilesDir, "tesseract")),
            tempBytes = sizeOf(context.cacheDir),
            entries = runCatching { db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrDefault(0),
        )
    }

    /**
     * Folds the write-ahead log into the database and, when at least 10% of the file is free pages (after deletes,
     * a restore or a migration), rebuilds it with VACUUM. Returns bytes freed. [force] always rebuilds.
     */
    suspend fun compact(force: Boolean = false): Long = withContext(Dispatchers.IO) {
        val before = databaseBytes()
        val w = db.openHelper.writableDatabase
        checkpoint(w)
        val free = pragma(w, "freelist_count")
        val pages = pragma(w, "page_count")
        if (force || (free >= 32 && free * 10 >= pages)) {
            runCatching { w.execSQL("VACUUM") }
            checkpoint(w)
        }
        (before - databaseBytes()).coerceAtLeast(0)
    }

    /** Temporary files: decrypted copies made for "Open in another app", image previews, half-finished imports. */
    suspend fun clearTemp(): Long = withContext(Dispatchers.IO) {
        val before = sizeOf(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        (before - sizeOf(context.cacheDir)).coerceAtLeast(0)
    }

    suspend fun shrinkImages(): Long = files.shrinkOldImages()

    suspend fun removeStatementFiles(olderThanMonths: Int): Int =
        files.removeStatementFilesBefore(System.currentTimeMillis() - olderThanMonths * 30L * 86_400_000L)

    suspend fun clearFundPrices() = wealth.clearCache()

    /** Quiet daily upkeep from the background worker: small, safe steps only. */
    suspend fun dailyUpkeep() {
        runCatching { compact(force = false) }
        runCatching { File(context.cacheDir, "share").deleteRecursively() }
    }

    private fun databaseBytes(): Long {
        val f = context.getDatabasePath(KharchaDatabase.NAME)
        return f.length() + File(f.path + "-wal").length() + File(f.path + "-shm").length() + File(f.path + "-journal").length()
    }

    private fun checkpoint(w: androidx.sqlite.db.SupportSQLiteDatabase) {
        runCatching { w.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
    }

    private fun pragma(w: androidx.sqlite.db.SupportSQLiteDatabase, name: String): Long =
        runCatching { w.query("PRAGMA $name").use { if (it.moveToFirst()) it.getLong(0) else 0L } }.getOrDefault(0L)

    private fun sizeOf(f: File): Long = if (!f.exists()) 0L else if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
