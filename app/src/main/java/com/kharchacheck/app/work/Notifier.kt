package com.kharchacheck.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kharchacheck.app.MainActivity
import com.kharchacheck.app.R

/** Local notifications only. No push service, no server. Amounts are hidden on the lock screen. */
object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_TIPS = "tips"
    const val EXTRA_OPEN = "open"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Reminders & monthly report", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Daily logging nudge, streak saver and month-end report"
        })
        nm.createNotificationChannel(NotificationChannel(CH_TIPS, "Daily money nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Up to three a day: invest with purpose (morning), avoid unnecessary spending (afternoon), a saving trick (evening)"
        })
        // SMS reading was removed: clean up its old notification channel on phones that had it.
        nm.deleteNotificationChannel("captured")
    }

    fun canPost(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, channel: String, title: String, text: String, open: String, publicTitle: String = "Kharcha Check") {
        if (!canPost(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, open).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(publicTitle).build()
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }
}
