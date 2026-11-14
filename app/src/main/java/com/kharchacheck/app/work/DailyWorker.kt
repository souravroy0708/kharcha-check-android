package com.kharchacheck.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kharchacheck.app.container
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.AnalyticsEngine
import com.kharchacheck.app.domain.Gamification
import com.kharchacheck.app.widget.KharchaWidget
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Runs once a day at the user's reminder time, fully offline:
 *  - "Log today" nudge only if nothing was logged today (and protects the streak)
 *  - on the 1st, "your last month's report is ready"
 */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.onboarded) return Result.success()
        val today = LocalDate.now()
        val added = c.finance.generateRecurring(today)
        if (added > 0) Notifier.post(applicationContext, 2003, Notifier.CH_REMINDERS, "🔁 $added repeating ${if (added == 1) "entry" else "entries"} added",
            "Rent, EMIs, SIPs or subscriptions due today. Tap to confirm or edit.", "inbox")
        val txns = c.finance.txns.first().filterNot { it.id in s.hiddenIds }  // hidden entries never reach notifications
        val cats = c.finance.categories.first().associateBy { it.id }

        if (today.dayOfMonth == 1) {
            val last = YearMonth.from(today).minusMonths(1)
            if (txns.any { it.month == last }) {
                Notifier.post(applicationContext, 2001, Notifier.CH_REMINDERS,
                    "Your ${last.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} report is ready 📊",
                    "See where it went, which leaks to cut, and set one target for this month.", "report")
            }
        }
        val g = Gamification.build(txns, cats, emptyList(), s.noSpendDays, s.challengesWon, s.monthlyBudgetPaise, today)
        KharchaWidget.push(applicationContext, KharchaWidget.State(true, g.logStreak, g.loggedToday, s.wisdomSeed))
        // Budget alerts (catches crossings from statement imports too). Each threshold once per month.
        run {
            val month = YearMonth.from(today)
            val report = com.kharchacheck.app.domain.ReportEngine.build(month, txns, cats)
            val budgets = c.finance.budgets.first()
            val sent = mutableSetOf<String>()
            val lines = mutableListOf<String>()
            report.categories.forEach { line ->
                val limit = budgets[line.category.id] ?: return@forEach
                val lvl = com.kharchacheck.app.domain.BudgetAlerts.level(line.paise, limit) ?: return@forEach
                val key = "$month|${line.category.id}|$lvl"
                if (key !in s.budgetAlertsSent) { sent += key; lines += com.kharchacheck.app.domain.BudgetAlerts.message("${line.category.emoji} ${line.category.name}", line.paise, limit, lvl) }
            }
            com.kharchacheck.app.domain.BudgetAlerts.level(report.spendPaise, s.monthlyBudgetPaise)?.let { lvl ->
                val key = "$month|all|$lvl"
                if (key !in s.budgetAlertsSent) { sent += key; lines += com.kharchacheck.app.domain.BudgetAlerts.message("monthly", report.spendPaise, s.monthlyBudgetPaise, lvl) }
            }
            if (lines.isNotEmpty()) {
                Notifier.post(applicationContext, 2400, Notifier.CH_REMINDERS, if (lines.size == 1) "Budget check" else "${lines.size} budgets need attention",
                    lines.joinToString("\n"), "report", publicTitle = "Budget check")
                c.settings.markBudgetAlerts(sent, month.toString())
            }
        }

        // Goal SIP reminders: on each goal's chosen day, if this month's contribution isn't done yet.
        run {
            val goals = c.finance.goals.first().filter { !it.archived && !it.done }
            goals.forEach { g ->
                val plan = s.goalPlans[g.id] ?: return@forEach
                if (plan.sipDay == 0 || plan.sipDay.coerceAtMost(today.lengthOfMonth()) != today.dayOfMonth) return@forEach
                val math = com.kharchacheck.app.domain.GoalPlanner.plan(plan, g.savedPaise)
                val need = if (plan.stepUp) math.sipStepUpStartPaise else math.sipFlatPaise
                val doneThisMonth = txns.filter { it.goalId == g.id && it.month == YearMonth.from(today) }.sumOf { it.amountPaise }
                if (need > 0 && doneThisMonth < need) Notifier.post(applicationContext, 2500 + (g.id % 50).toInt(), Notifier.CH_REMINDERS,
                    "${g.emoji} Invest ${Money.format(need - doneThisMonth)} for ${g.name}",
                    "Your monthly step towards ${g.name}. Invest it, then tap \"Add money\" on the goal so your progress stays right.", "goals",
                    publicTitle = "Goal reminder")
            }
        }

        // Bill reminders: N days before, the day before, on the day, and once when a day late.
        com.kharchacheck.app.domain.Bills.remindToday(c.tools.bills(), today).forEachIndexed { i, b ->
            val amount = if (b.bill.amountPaise > 0) " (${Money.format(b.bill.amountPaise)})" else ""
            val whenText = when { b.daysLeft < 0 -> "was due yesterday"; b.daysLeft == 0L -> "is due today"; b.daysLeft == 1L -> "is due tomorrow"; else -> "is due in ${b.daysLeft} days" }
            Notifier.post(applicationContext, 2100 + i, Notifier.CH_REMINDERS, "${b.bill.emoji} ${b.bill.name} $whenText$amount",
                if (b.daysLeft < 0) "Pay it today to avoid late fees and interest, then mark it paid." else "Tap to mark it paid once done. Paying on time avoids late fees.",
                "bills", publicTitle = "A bill is due soon")
        }
        // Monday digest: last week vs your usual, and where most of it went.
        if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
            val w = AnalyticsEngine.lastWeek(txns, cats, today)
            if (w.spendPaise > 0) {
                val vs = w.deltaPct?.let { if (it <= -5) "${-it}% less than your usual week 🎉" else if (it >= 5) "$it% more than your usual week" else "about your usual" } ?: ""
                val top = w.topCategory?.let { " Most went to ${it.emoji} ${it.name} (${Money.format(w.topPaise)})." } ?: ""
                Notifier.post(applicationContext, 2004, Notifier.CH_REMINDERS, "Your week in money 📊",
                    "You spent ${Money.format(w.spendPaise)} last week${if (vs.isNotEmpty()) ", $vs" else ""}.$top", "report",
                    publicTitle = "Your weekly summary is ready")
            }
        }
        if (s.reminder) {
            if (!g.loggedToday) {
                val text = if (g.logStreak > 0) "Keep your ${g.logStreak}-day streak 🔥 Add today's spends, or mark it a no-spend day."
                else "Takes 5 seconds. Add today's spends, or mark it a no-spend day 🌿"
                Notifier.post(applicationContext, 2002, Notifier.CH_REMINDERS, "Aaj ka hisaab?", text, "add")
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily"

        fun schedule(ctx: Context, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        }
    }
}
