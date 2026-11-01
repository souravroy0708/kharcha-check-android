package com.kharchacheck.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receiver-based live SMS capture (Spec Page 4, 5 & 12: "Receiver-based capture only; no foreground service; no polling").
 * Data minimisation (Spec Page 6): Stores only the parsed fields and the SMS id, never the raw message body.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        val db = LocalDb.get(context)
        val rules = db.rules()
        val recent = db.allTransactions().take(15)
        val smsId = "live_${timestamp}_${sender.hashCode()}"
        if (db.hasSmsId(smsId)) return

        when (val outcome = SmsParser.parse(sender, fullBody, timestamp, smsId, rules, recent)) {
            is SmsParser.ParseOutcome.Matched -> {
                val p = outcome.parsed
                val acctId = db.resolveAccountForSms(p.accountLast4, p.bankHeader)
                db.insertOrUpdateTransaction(
                    TransactionEntry(
                        amountPaise = p.amountPaise,
                        type = p.type,
                        epochMillis = p.epochMillis,
                        accountId = acctId,
                        mode = p.mode,
                        merchantName = p.normalizedMerchant,
                        categoryId = p.categoryId,
                        source = TxSource.SMS,
                        smsId = p.smsId,
                        refNo = p.refNo,
                        confidence = p.confidence.score,
                        reviewState = p.reviewState,
                    )
                )
            }
            is SmsParser.ParseOutcome.UnrecognisedBankSms -> {
                db.logUnrecognisedSms(outcome.sender, outcome.maskedTemplate)
            }
            is SmsParser.ParseOutcome.Ignored -> Unit
        }
    }
}

/**
 * One-time SMS inbox backfill for the last 30, 90, or 180 days (Spec Page 4, 5 & 9).
 */
object SmsBackfill {

    data class BackfillResult(val scanned: Int, val imported: Int, val reviewTrayCount: Int)

    suspend fun run(context: Context, daysBack: Int): BackfillResult = withContext(Dispatchers.IO) {
        val db = LocalDb.get(context)
        val rules = db.rules()
        val cutoffMillis = System.currentTimeMillis() - daysBack.toLong() * 24L * 3600L * 1000L
        var scanned = 0
        var imported = 0
        var reviewCount = 0

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(cutoffMillis.toString())
            context.contentResolver.query(uri, projection, selection, selectionArgs, "date ASC")?.use { c: Cursor ->
                val recentWindow = ArrayList<TransactionEntry>(db.allTransactions().take(30))
                while (c.moveToNext()) {
                    scanned++
                    val id = "inbox_${c.getLong(0)}"
                    if (db.hasSmsId(id)) continue
                    val address = c.getString(1) ?: continue
                    val body = c.getString(2) ?: continue
                    val dateMillis = c.getLong(3)

                    when (val outcome = SmsParser.parse(address, body, dateMillis, id, rules, recentWindow)) {
                        is SmsParser.ParseOutcome.Matched -> {
                            val p = outcome.parsed
                            val acctId = db.resolveAccountForSms(p.accountLast4, p.bankHeader)
                            val entry = TransactionEntry(
                                amountPaise = p.amountPaise,
                                type = p.type,
                                epochMillis = p.epochMillis,
                                accountId = acctId,
                                mode = p.mode,
                                merchantName = p.normalizedMerchant,
                                categoryId = p.categoryId,
                                source = TxSource.SMS,
                                smsId = p.smsId,
                                refNo = p.refNo,
                                confidence = p.confidence.score,
                                reviewState = p.reviewState,
                            )
                            db.insertOrUpdateTransaction(entry)
                            recentWindow.add(0, entry)
                            if (recentWindow.size > 30) recentWindow.removeAt(recentWindow.lastIndex)
                            imported++
                            if (p.reviewState == ReviewState.NEEDS_REVIEW) reviewCount++
                        }
                        is SmsParser.ParseOutcome.UnrecognisedBankSms -> {
                            db.logUnrecognisedSms(outcome.sender, outcome.maskedTemplate)
                        }
                        is SmsParser.ParseOutcome.Ignored -> Unit
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } catch (_: Exception) {
        }
        BackfillResult(scanned, imported, reviewCount)
    }
}
