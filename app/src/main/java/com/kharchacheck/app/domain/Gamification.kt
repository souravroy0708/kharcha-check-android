package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth

/** One day on the habit grid. */
data class DayCell(val date: LocalDate, val active: Boolean, val leak: Boolean, val noSpend: Boolean, val spendPaise: Long)

data class Level(val number: Int, val name: String, val minXp: Int)

data class Badge(val id: String, val emoji: String, val name: String, val how: String)

data class BadgeStatus(val badge: Badge, val earned: Boolean)

data class GameState(
    val xp: Int,
    val level: Level,
    val nextLevel: Level?,
    val logStreak: Int,
    val bestLogStreak: Int,
    val leakFreeStreak: Int,
    val bestLeakFreeStreak: Int,
    val last21: List<DayCell>,
    val loggedToday: Boolean,
    val badges: List<BadgeStatus>,
) {
    val levelProgress: Float
        get() = nextLevel?.let { ((xp - level.minXp).toFloat() / (it.minXp - level.minXp)).coerceIn(0f, 1f) } ?: 1f
    val earnedCount: Int get() = badges.count { it.earned }
}

enum class ChallengeKind { AVOID_CATEGORIES, AVOID_LEAKS, LOG_EVERY_DAY, NO_SPEND_DAYS }

data class Challenge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val days: Int,
    val kind: ChallengeKind,
    val categoryKeys: Set<String> = emptySet(),
    val target: Int = 0,
)

enum class DayMark { OK, FAIL, TODAY, NEUTRAL, FUTURE }
enum class ChallengeStatus { ACTIVE, WON, FAILED }

data class ChallengeProgress(val challenge: Challenge, val start: LocalDate, val marks: List<DayMark>, val status: ChallengeStatus, val count: Int) {
    val end: LocalDate get() = start.plusDays(challenge.days - 1L)
    val doneDays: Int get() = marks.count { it == DayMark.OK }
}

/**
 * Habit and reward engine, inspired by Bas 1 Minute's streaks and 21-day grid.
 * Everything is derived from the user's own entries, so it can't drift or be lost: no separate score table.
 */
object Gamification {

    val levels = listOf(
        Level(1, "Naya Shuruaat", 0),
        Level(2, "Hisaab Rakhne Wala", 100),
        Level(3, "Smart Spender", 300),
        Level(4, "Bachat Starter", 600),
        Level(5, "Leak Hunter", 1000),
        Level(6, "Budget Boss", 1600),
        Level(7, "Saving Star", 2500),
        Level(8, "Investor Mindset", 4000),
        Level(9, "Paisa Pro", 6000),
        Level(10, "Kharcha Champion", 9000),
    )

    val badges = listOf(
        Badge("first_step", "👣", "First Hisaab", "Log your first entry"),
        Badge("streak_7", "🔥", "Week Warrior", "Log 7 days in a row"),
        Badge("streak_21", "🗓️", "21-Day Habit", "Log 21 days in a row"),
        Badge("streak_60", "🦸", "Hisaab Hero", "Log 60 days in a row"),
        Badge("leak_free_7", "🔧", "Leak Plugger", "7 logged days in a row with zero leaks"),
        Badge("no_spend_5", "🌿", "Zero Day Club", "5 no-spend days in one month"),
        Badge("first_invest", "🌱", "Future Self", "Log your first save or invest entry"),
        Badge("saver_20", "💪", "20% Saver", "Finish a month saving 20% or more of income"),
        Badge("rule_503020", "🥇", "Golden Rule", "Finish a month within 50/30/20"),
        Badge("under_budget", "🎯", "Budget Boss", "Finish a month under your budget"),
        Badge("leak_cut", "✂️", "Leak Cutter", "Cut leaks by 30% vs the month before"),
        Badge("goal_done", "🏁", "Goal Getter", "Complete a savings goal"),
        Badge("logger_100", "💯", "Century", "Log 100 entries"),
        Badge("challenge_won", "🏆", "Challenger", "Win a challenge"),
    )

    val challenges = listOf(
        Challenge("no_delivery_7", "🛵", "No food delivery week", "7 days without Swiggy or Zomato. Cook, carry or eat out with friends instead.", 7, ChallengeKind.AVOID_CATEGORIES, setOf("food_delivery")),
        Challenge("junk_free_10", "🥗", "Junk-free 10", "10 days with no junk food, snacks, cold drinks or sweets bought outside.", 10, ChallengeKind.AVOID_CATEGORIES, setOf("junk_food", "cold_drinks", "sweets_desserts")),
        Challenge("qc_detox_7", "⚡", "Quick-commerce detox", "7 days without 10-minute impulse orders. Make one planned grocery list instead.", 7, ChallengeKind.AVOID_CATEGORIES, setOf("quick_commerce")),
        Challenge("leak_free_7", "🔧", "Leak-free week", "7 days with zero spending in any Leak category.", 7, ChallengeKind.AVOID_LEAKS),
        Challenge("chai_home_14", "🫖", "Chai at home, 14 days", "Two weeks of no paid chai or coffee outside. Small cups, big savings.", 14, ChallengeKind.AVOID_CATEGORIES, setOf("chai_coffee")),
        Challenge("no_impulse_14", "🛍️", "No impulse buys", "14 days with no impulse shopping. Use the 30-day wishlist instead.", 14, ChallengeKind.AVOID_CATEGORIES, setOf("impulse_shopping", "quick_commerce", "regret_misc")),
        Challenge("log_21", "🗓️", "21-day hisaab", "Log at least one entry, or mark a no-spend day, every day for 21 days.", 21, ChallengeKind.LOG_EVERY_DAY),
        Challenge("zero_days_4", "🌿", "4 zero days", "Have 4 no-spend days within the next 14 days.", 14, ChallengeKind.NO_SPEND_DAYS, target = 4),
    )

    fun challenge(id: String?): Challenge? = challenges.firstOrNull { it.id == id }

    fun build(
        txns: List<Txn>,
        categories: Map<Long, Category>,
        goals: List<Goal>,
        noSpendDays: Set<Long>,
        challengesWon: Int,
        budgetPaise: Long,
        today: LocalDate = LocalDate.now(),
    ): GameState {
        fun bucket(t: Txn) = categories[t.categoryId]?.bucket ?: Bucket.WANT
        val byDate = txns.groupBy { it.date }
        val activeDays = (byDate.keys + noSpendDays.map { LocalDate.ofEpochDay(it) }).toSortedSet()
        val leakDays = txns.filter { it.type == TxType.EXPENSE && bucket(it) == Bucket.LEAK }.map { it.date }.toSet()

        val logStreak = currentStreak(today) { it in activeDays }
        val bestLog = bestStreak(activeDays)
        val leakFreeOk: (LocalDate) -> Boolean = { it in activeDays && it !in leakDays }
        val leakFree = currentStreak(today, leakFreeOk)
        val bestLeakFree = bestStreak(activeDays.filter { it !in leakDays }.toSortedSet())

        val last21 = (20 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val list = byDate[d].orEmpty()
            DayCell(
                date = d,
                active = d in activeDays,
                leak = d in leakDays,
                noSpend = d.toEpochDay() in noSpendDays,
                spendPaise = list.filter { it.type == TxType.EXPENSE && bucket(it).isSpend }.sumOf { it.amountPaise },
            )
        }

        // XP: rewards consistency, not volume, so nobody games it by splitting entries.
        var xp = 0
        byDate.forEach { (d, list) ->
            xp += 10 + 5 * (list.size - 1).coerceIn(0, 3)
            if (d !in leakDays) xp += 10
            if (list.any { it.type == TxType.INVEST }) xp += 30
        }
        xp += noSpendDays.size * 20
        xp += challengesWon * 100

        val earned = earnedBadges(txns, categories, goals, noSpendDays, challengesWon, budgetPaise, today, bestLog, bestLeakFree)
        xp += earned.size * 50

        val level = levels.last { xp >= it.minXp }
        val next = levels.firstOrNull { it.minXp > xp }
        return GameState(
            xp = xp, level = level, nextLevel = next, logStreak = logStreak, bestLogStreak = bestLog,
            leakFreeStreak = leakFree, bestLeakFreeStreak = bestLeakFree, last21 = last21,
            loggedToday = today in activeDays,
            badges = badges.map { BadgeStatus(it, it.id in earned) },
        )
    }

    /** Counts back from today; if today isn't done yet, the streak is still alive from yesterday. */
    fun currentStreak(today: LocalDate, ok: (LocalDate) -> Boolean): Int {
        var d = if (ok(today)) today else today.minusDays(1)
        var n = 0
        while (ok(d)) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(days: Collection<LocalDate>): Int {
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    private fun earnedBadges(
        txns: List<Txn>, categories: Map<Long, Category>, goals: List<Goal>, noSpend: Set<Long>,
        challengesWon: Int, budgetPaise: Long, today: LocalDate, bestLog: Int, bestLeakFree: Int,
    ): Set<String> {
        val e = mutableSetOf<String>()
        if (txns.isNotEmpty()) e += "first_step"
        if (bestLog >= 7) e += "streak_7"
        if (bestLog >= 21) e += "streak_21"
        if (bestLog >= 60) e += "streak_60"
        if (bestLeakFree >= 7) e += "leak_free_7"
        if (noSpend.groupBy { YearMonth.from(LocalDate.ofEpochDay(it)) }.any { it.value.size >= 5 }) e += "no_spend_5"
        if (txns.any { it.type == TxType.INVEST }) e += "first_invest"
        if (goals.any { it.done }) e += "goal_done"
        if (txns.size >= 100) e += "logger_100"
        if (challengesWon > 0) e += "challenge_won"

        val current = YearMonth.from(today)
        val months = txns.map { it.month }.filter { it < current }.toSet()
        for (m in months) {
            val r = ReportEngine.build(m, txns, categories, ReportEngine.Config(budgetPaise = budgetPaise, today = today))
            if (r.incomePaise > 0 && r.savingsRatePct >= 20) e += "saver_20"
            if (r.split.followsRule) e += "rule_503020"
            if (budgetPaise > 0 && r.spendPaise in 1..budgetPaise) e += "under_budget"
            if (r.prevLeaksPaise > 0 && r.leaksPaise * 10 <= r.prevLeaksPaise * 7) e += "leak_cut"
        }
        return e
    }

    fun progress(
        challenge: Challenge, start: LocalDate, txns: List<Txn>, categories: Map<Long, Category>,
        noSpendDays: Set<Long>, today: LocalDate = LocalDate.now(),
    ): ChallengeProgress {
        val keyOf: (Txn) -> String? = { categories[it.categoryId]?.key }
        val bucketOf: (Txn) -> Bucket? = { categories[it.categoryId]?.bucket }
        val inWindow = txns.filter { !it.date.isBefore(start) && !it.date.isAfter(start.plusDays(challenge.days - 1L)) }
        val byDate = inWindow.groupBy { it.date }

        fun violated(d: LocalDate): Boolean = byDate[d].orEmpty().any { t ->
            t.type == TxType.EXPENSE && when (challenge.kind) {
                ChallengeKind.AVOID_CATEGORIES -> keyOf(t) in challenge.categoryKeys
                ChallengeKind.AVOID_LEAKS -> bucketOf(t) == Bucket.LEAK
                else -> false
            }
        }
        fun active(d: LocalDate) = byDate.containsKey(d) || d.toEpochDay() in noSpendDays

        val marks = (0 until challenge.days).map { i ->
            val d = start.plusDays(i.toLong())
            when {
                d.isAfter(today) -> DayMark.FUTURE
                challenge.kind == ChallengeKind.NO_SPEND_DAYS -> if (d.toEpochDay() in noSpendDays) DayMark.OK else if (d == today) DayMark.TODAY else DayMark.NEUTRAL
                challenge.kind == ChallengeKind.LOG_EVERY_DAY -> when { active(d) -> DayMark.OK; d == today -> DayMark.TODAY; else -> DayMark.FAIL }
                violated(d) -> DayMark.FAIL
                d == today -> DayMark.TODAY
                else -> DayMark.OK
            }
        }
        val end = start.plusDays(challenge.days - 1L)
        val count = marks.count { it == DayMark.OK }
        val status = when (challenge.kind) {
            ChallengeKind.NO_SPEND_DAYS -> when {
                count >= challenge.target -> ChallengeStatus.WON
                today.isAfter(end) -> ChallengeStatus.FAILED
                else -> ChallengeStatus.ACTIVE
            }
            ChallengeKind.LOG_EVERY_DAY -> when {
                DayMark.FAIL in marks -> ChallengeStatus.FAILED
                marks.all { it == DayMark.OK } -> ChallengeStatus.WON
                else -> ChallengeStatus.ACTIVE
            }
            else -> when {
                DayMark.FAIL in marks -> ChallengeStatus.FAILED
                today.isAfter(end) -> ChallengeStatus.WON
                else -> ChallengeStatus.ACTIVE
            }
        }
        return ChallengeProgress(challenge, start, marks, status, count)
    }
}
