package com.kharchacheck.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kharchacheck.app.container
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.work.Notifier
import kotlinx.coroutines.launch

/** Wakes only when an SMS arrives. No background service, no polling. Does nothing unless the user opted in. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts.first().displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val at = parts.first().timestampMillis
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                if (!c.settings.current().smsCapture) return@launch
                val t = c.finance.captureFromSms(sender, body, at) ?: return@launch
                val what = t.payee.ifBlank { "a payment" }
                Notifier.post(context, (t.id % 100_000).toInt() + 3000, Notifier.CH_CAPTURE,
                    "${Money.format(t.amountPaise)} · $what", "Captured from your bank SMS. Tap to check the category and confirm.", "inbox",
                    publicTitle = "New entry to review")
            } finally {
                pending.finish()
            }
        }
    }
}
