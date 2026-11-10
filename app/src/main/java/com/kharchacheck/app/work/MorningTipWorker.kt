package com.kharchacheck.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kharchacheck.app.container
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.DailyWisdom
import com.kharchacheck.app.domain.OnlineSpending
import com.kharchacheck.app.domain.ReportEngine
import com.kharchacheck.app.domain.WisdomKind
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Morning money tip: the same card as Home (one of 100, never repeated for 100 days), with its
 * "try today" action. On "cut a leak" days it adds the user's own biggest online leak this month.
 */
class MorningTipWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.onboarded || !s.morningTip || s.wisdomSeed == 0L) return Result.success()
        val today = LocalDate.now()
        val w = DailyWisdom.forDate(today, s.wisdomSeed)
        var text = "${w.line}\n\nTry today: ${w.doToday}"
        if (w.kind == WisdomKind.CUT) {
            val txns = c.finance.txns.first().filterNot { it.id in s.hiddenIds }  // hidden entries never reach notifications
            val cats = c.finance.categories.first().associateBy { it.id }
            val r = ReportEngine.build(YearMonth.from(today), txns, cats)
            OnlineSpending.biggestLeak(r.categories)?.let { leak ->
                text += "\n\nYour biggest online leak this month: ${leak.category.emoji} ${leak.category.name}, ${Money.format(leak.paise)} so far."
            }
        }
        Notifier.post(applicationContext, 2300, Notifier.CH_TIPS, "${w.emoji} Today's money tip", text, "tip", publicTitle = "Today's money tip")
        return Result.success()
    }

    companion object {
        private const val NAME = "morning-tip"
        fun schedule(ctx: Context, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<MorningTipWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes().coerceAtLeast(1), TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        }
        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
