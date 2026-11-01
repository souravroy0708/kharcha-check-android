package com.kharchacheck.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Local notification scheduler via WorkManager (Spec Page 4, 9 & 13):
 * 1. Weekly glance: One Sunday notification with this week's total and how it compares with usual.
 * 2. Month-end review: A reminder on the last day of the month to complete the 3-minute review.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val db = LocalDb.get(applicationContext)
        val today = LocalDate.now()
        val ym = YearMonth.from(today)

        val allExpenses = db.allTransactions().filter { it.type == TxType.EXPENSE }

        // 1. Last day of the month -> Month-end review prompt
        if (prefs.monthEndReminderEnabled && today.dayOfMonth == ym.lengthOfMonth()) {
            val monthSpent = allExpenses.filter { it.yearMonth == ym }.sumOf { it.amountPaise }
            showNotification(
                id = 1001,
                title = "Month-end Kharcha Check is ready",
                body = "This month's spend is ${Money.format(monthSpent)}. Take 3 minutes to see where it went and pick one change.",
            )
        }
        // 2. Sunday -> Weekly glance notification
        else if (prefs.weeklyReminderEnabled && today.dayOfWeek == DayOfWeek.SUNDAY) {
            val weekStart = today.minusDays(6)
            val thisWeekPaise = allExpenses.filter { !it.localDate.isBefore(weekStart) }.sumOf { it.amountPaise }
            val prevWeekPaise = allExpenses.filter {
                it.localDate.isBefore(weekStart) && !it.localDate.isBefore(weekStart.minusDays(7))
            }.sumOf { it.amountPaise }
            val cmp = if (prevWeekPaise > 0) {
                val diff = thisWeekPaise - prevWeekPaise
                if (diff <= 0) "${Money.format(-diff)} less than last week" else "${Money.format(diff)} more than last week"
            } else {
                "Tap to view your weekly category breakdown"
            }
            showNotification(
                id = 1002,
                title = "Weekly glance: ${Money.format(thisWeekPaise)} spent",
                body = "$cmp. Neutral facts, 100% on your phone.",
            )
        }
        return Result.success()
    }

    private fun showNotification(id: Int, title: String, body: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "kharcha_check_reminders"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Weekly & Month-End Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try {
            nm.notify(id, notif)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val WORK_NAME = "kharcha_check_daily_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
