package com.kharchacheck.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.kharchacheck.app.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart edition: optional SMS reading, off until the user turns it on in Settings.
 * Only bank-looking senders are parsed; message text is never stored, only the parsed fields.
 * Everything captured lands in the Review inbox, where every field can be changed.
 */
object SmsSupport {
    const val available = true
    val permissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    data class ImportResult(val scanned: Int, val added: Int)

    fun hasPermission(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun importInbox(context: Context, days: Int): ImportResult = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext ImportResult(0, 0)
        val since = System.currentTimeMillis() - days * 86_400_000L
        val repo = context.container.finance
        var scanned = 0; var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(iA) ?: continue
                val body = c.getString(iB) ?: continue
                if (repo.captureFromSms(sender, body, c.getLong(iD)) != null) added++
            }
        }
        ImportResult(scanned, added)
    }
}
