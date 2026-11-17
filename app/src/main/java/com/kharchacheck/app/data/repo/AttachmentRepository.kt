package com.kharchacheck.app.data.repo

import android.net.Uri
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.AttachmentKind
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.db.toEntity
import com.kharchacheck.app.data.db.toModel
import com.kharchacheck.app.data.files.FileVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/** Receipts, UPI screenshots, bank and card statements, and the profile photo. All encrypted on device. */
class AttachmentRepository(private val db: KharchaDatabase, private val vault: FileVault) {

    val all: Flow<List<Attachment>> = db.attachments().observe().map { l -> l.map { it.toModel() } }

    /** Receipt or UPI screenshot. Not linked until the entry is saved. */
    suspend fun addReceipt(uri: Uri): Attachment {
        val s = vault.import(uri, maxMb = 15)
        val a = Attachment(kind = AttachmentKind.RECEIPT, mime = s.mime, fileName = s.fileName, originalName = s.originalName, sizeBytes = s.sizeBytes)
        return a.copy(id = db.attachments().insert(a.toEntity()))
    }

    suspend fun addStatement(uri: Uri, kind: AttachmentKind, month: YearMonth, label: String): Attachment {
        val s = vault.import(uri, maxMb = 25, imageMaxPx = 3000)
        val a = Attachment(kind = kind, month = month, label = label.trim(), mime = s.mime, fileName = s.fileName,
            originalName = s.originalName, sizeBytes = s.sizeBytes)
        return a.copy(id = db.attachments().insert(a.toEntity()))
    }

    suspend fun link(ids: List<Long>, txnId: Long) { if (ids.isNotEmpty()) db.attachments().link(ids, txnId) }

    suspend fun setKind(a: Attachment, kind: AttachmentKind, month: YearMonth?) = db.attachments().setKind(a.id, kind.name, month?.toString())

    suspend fun remove(a: Attachment) { db.attachments().delete(a.id); vault.delete(a.fileName) }

    suspend fun bytes(a: Attachment): ByteArray = vault.read(a.fileName)

    /** Removes receipts left behind by entries that were never saved or were deleted (after 24 hours). */
    suspend fun cleanupOrphans() {
        db.attachments().orphans(System.currentTimeMillis() - 86_400_000L).forEach { e -> db.attachments().delete(e.id); vault.delete(e.fileName) }
    }

    // ---- Profile photo ----
    suspend fun setProfilePhoto(uri: Uri) { vault.import(uri, maxMb = 20, imageMaxPx = 512, fileName = FileVault.PROFILE) }
    suspend fun profilePhoto(): ByteArray? = if (vault.exists(FileVault.PROFILE)) runCatching { vault.read(FileVault.PROFILE) }.getOrNull() else null
    fun removeProfilePhoto() = vault.delete(FileVault.PROFILE)

    suspend fun wipe() { db.attachments().clear(); vault.wipe() }
}
