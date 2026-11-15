package com.kharchacheck.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kharchacheck.app.container
import com.kharchacheck.app.domain.NudgeSlot
import com.kharchacheck.app.domain.Nudges
import com.kharchacheck.app.domain.Subscriptions
import com.kharchacheck.app.domain.TaxBucket
import com.kharchacheck.app.domain.TaxSaver
import com.kharchacheck.app.domain.Wishlist
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Three daily nudges (morning: invest with purpose; afternoon: avoid unnecessary spending; evening: a saving trick).
 * Personal ones first (built from the user's own entries, with cooldowns), otherwise a unique item from the slot's pool.
 * Hidden entries are never used, and lock-screen text never shows amounts.
 */
class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val slot = NudgeSlot.entries.getOrNull(inputData.getInt(KEY_SLOT, -1)) ?: return Result.success()
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.onboarded || !s.morningTip || slot.name in s.nudgeSlotsOff || s.wisdomSeed == 0L) return Result.success()
        val today = LocalDate.now()
        val txns = c.finance.txns.first().filterNot { it.id in s.hiddenIds }
        val cats = c.finance.categories.first().associateBy { it.id }
        val subs = Subscriptions.detect(txns, cats, today).filter { it.key !in s.subsCancelled }
        val taxRoom = if (s.taxRegime == "OLD") {
            val fy = TaxSaver.fyStart(today)
            TaxSaver.summary(txns, cats, today, s.taxAssign, TaxBucket.entries.associateWith { s.taxManual["$fy|${it.name}"] ?: 0L },
                s.taxSlabPct, s.selfSenior, s.parentsSenior).possibleMoreTaxPaise
        } else 0L
        val ctx = Nudges.context(txns, cats, today, c.finance.goals.first(), s.goalPlans, s.monthlyBudgetPaise,
            wishlistReady = s.wishlist.count { Wishlist.ready(it, today) }, subsYearlyPaise = subs.sumOf { it.yearlyPaise },
            subsUnreviewed = subs.count { it.key !in s.subsKept }, taxRoomPaise = taxRoom)
        val n = Nudges.pick(slot, ctx, s.wisdomSeed + slot.ordinal, s.nudgeLog)
        Notifier.post(applicationContext, 2300 + slot.ordinal, Notifier.CH_TIPS, n.title, n.body, n.route, publicTitle = "${slot.emoji} ${slot.label}")
        if (!n.key.startsWith("s:")) c.settings.logNudge(n.key.substringBefore(':'), today)
        return Result.success()
    }

    companion object {
        private const val KEY_SLOT = "slot"
        private fun name(slot: NudgeSlot) = "nudge-${slot.name.lowercase()}"

        fun schedule(ctx: Context, slot: NudgeSlot, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<NudgeWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_SLOT to slot.ordinal)).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(name(slot), ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        }

        /** (Re)schedules all enabled slots; also removes the old single morning-tip job from earlier versions. */
        fun scheduleAll(ctx: Context, times: Map<NudgeSlot, Pair<Int, Int>>, off: Set<String>) {
            WorkManager.getInstance(ctx).cancelUniqueWork("morning-tip")
            NudgeSlot.entries.forEach { slot ->
                if (slot.name in off) cancel(ctx, slot) else times[slot]?.let { (h, m) -> schedule(ctx, slot, h, m) }
            }
        }
        fun cancel(ctx: Context, slot: NudgeSlot) = WorkManager.getInstance(ctx).cancelUniqueWork(name(slot))
        fun cancelAll(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork("morning-tip"); NudgeSlot.entries.forEach { cancel(ctx, it) } }
    }
}
