package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

data class CategoryLine(
    val category: Category,
    val paise: Long,
    val count: Int,
    val sharePct: Int,
    val prevPaise: Long,
    val budgetPaise: Long?,
) {
    val overBudget: Boolean get() = budgetPaise != null && budgetPaise > 0 && paise > budgetPaise
    val deltaPct: Int? get() = if (prevPaise > 0) (((paise - prevPaise) * 100) / prevPaise).toInt() else null
}

data class PayeeLine(val payee: String, val paise: Long, val count: Int)

/** Needs / Wants / Savings split, as % of income (or of total outflow when no income was logged). */
data class Split(val needsPct: Int, val wantsPct: Int, val savePct: Int, val basedOnIncome: Boolean) {
    val followsRule: Boolean get() = basedOnIncome && needsPct <= 50 && wantsPct <= 30 && savePct >= 20
}

data class Insight(val emoji: String, val text: String, val tone: Tone) { enum class Tone { GOOD, WARN, INFO } }

data class MonthReport(
    val month: YearMonth,
    val incomePaise: Long,
    val spendPaise: Long,
    val needsPaise: Long,
    val wantsPaise: Long,
    val leaksPaise: Long,
    val investPaise: Long,
    val transferPaise: Long,
    val txnCount: Int,
    val categories: List<CategoryLine>,
    val leakCategories: List<CategoryLine>,
    val topPayees: List<PayeeLine>,
    val dailySpend: List<Long>,
    val modeSplit: Map<PayMode, Long>,
    val weekdaySpend: Map<DayOfWeek, Long>,
    val regretPaise: Long,
    val smallTicketCount: Int,
    val smallTicketPaise: Long,
    val prevSpendPaise: Long,
    val prevLeaksPaise: Long,
    val budgetPaise: Long,
    val projectedSpendPaise: Long,
    val noSpendDays: Int,
    val split: Split,
    val leakTenYearValuePaise: Long,
    val insights: List<Insight>,
    /** True when no income was logged this month and the monthly income from Settings was used instead. */
    val incomeEstimated: Boolean = false,
) {
    /** Income minus spending. Investing counts as saved. */
    val savedPaise: Long get() = incomePaise - spendPaise
    val savingsRatePct: Int get() = if (incomePaise > 0) ((savedPaise * 100) / incomePaise).toInt().coerceIn(-999, 100) else 0
    val isEmpty: Boolean get() = txnCount == 0
    val spendDeltaPct: Int? get() = if (prevSpendPaise > 0) (((spendPaise - prevSpendPaise) * 100) / prevSpendPaise).toInt() else null
}

/**
 * Builds the month report entirely on-device. Pure function: same inputs, same report.
 * "Spend" = Needs + Wants + Leaks. Investing and transfers are never counted as spending.
 */
object ReportEngine {

    data class Config(
        val budgetPaise: Long = 0,
        val categoryBudgets: Map<Long, Long> = emptyMap(),
        val smallTicketPaise: Long = 200_00,
        val expectedReturnPct: Double = 12.0,
        val noSpendDays: Set<Long> = emptySet(),
        val today: LocalDate = LocalDate.now(),
        /** Monthly income from Settings; used only when no income entries exist for the month. */
        val fallbackIncomePaise: Long = 0,
    )

    fun build(month: YearMonth, all: List<Txn>, categories: Map<Long, Category>, cfg: Config = Config()): MonthReport {
        val txns = all.filter { it.month == month }
        val prev = all.filter { it.month == month.minusMonths(1) }
        fun bucketOf(t: Txn): Bucket = categories[t.categoryId]?.bucket ?: Bucket.WANT
        fun isSpend(t: Txn) = t.type == TxType.EXPENSE && bucketOf(t).isSpend

        val spendTx = txns.filter(::isSpend)
        val loggedIncome = txns.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
        val estimated = loggedIncome == 0L && cfg.fallbackIncomePaise > 0 && txns.isNotEmpty()
        val income = if (estimated) cfg.fallbackIncomePaise else loggedIncome
        val needs = spendTx.filter { bucketOf(it) == Bucket.NEED }.sumOf { it.amountPaise }
        val wants = spendTx.filter { bucketOf(it) == Bucket.WANT }.sumOf { it.amountPaise }
        val leaks = spendTx.filter { bucketOf(it) == Bucket.LEAK }.sumOf { it.amountPaise }
        val invest = txns.filter { it.type == TxType.INVEST }.sumOf { it.amountPaise }
        val transfer = txns.filter { it.type == TxType.TRANSFER }.sumOf { it.amountPaise }
        val spend = needs + wants + leaks

        val prevSpendTx = prev.filter(::isSpend)
        val prevByCat = prevSpendTx.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }

        val lines = spendTx.groupBy { it.categoryId }.mapNotNull { (id, list) ->
            val cat = categories[id] ?: return@mapNotNull null
            val sum = list.sumOf { it.amountPaise }
            CategoryLine(cat, sum, list.size, pct(sum, spend), prevByCat[id] ?: 0, cfg.categoryBudgets[id])
        }.sortedByDescending { it.paise }

        val payees = spendTx.filter { it.payee.isNotBlank() }
            .groupBy { it.payee.trim().lowercase() }
            .map { (_, l) -> PayeeLine(l.first().payee.trim(), l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }.take(5)

        val daily = MutableList(month.lengthOfMonth()) { 0L }
        spendTx.forEach { daily[it.date.dayOfMonth - 1] += it.amountPaise }

        val modes = spendTx.groupBy { it.mode }.mapValues { e -> e.value.sumOf { it.amountPaise } }
        val weekdays = spendTx.groupBy { it.date.dayOfWeek }.mapValues { e -> e.value.sumOf { it.amountPaise } }
        val small = spendTx.filter { it.amountPaise < cfg.smallTicketPaise }
        val regret = spendTx.filter { it.worth == Worth.REGRET }.sumOf { it.amountPaise }

        // Projection: only meaningful for the month in progress
        val isCurrent = month == YearMonth.from(cfg.today)
        val daysElapsed = if (isCurrent) cfg.today.dayOfMonth else month.lengthOfMonth()
        val projected = if (isCurrent && daysElapsed > 0) spend * month.lengthOfMonth() / daysElapsed else spend

        val noSpend = cfg.noSpendDays.count { d -> LocalDate.ofEpochDay(d).let { YearMonth.from(it) == month } }

        val split = if (income > 0) {
            val save = (income - needs - wants - leaks).coerceAtLeast(0)
            Split(pct(needs, income), pct(wants + leaks, income), pct(save, income), true)
        } else {
            val out = spend + invest
            Split(pct(needs, out), pct(wants + leaks, out), pct(invest, out), false)
        }

        val leakFuture = InvestMath.sipFutureValue(leaks, cfg.expectedReturnPct, 10)
        val prevLeaks = prevSpendTx.filter { bucketOf(it) == Bucket.LEAK }.sumOf { it.amountPaise }

        val leakLines = lines.filter { it.category.bucket == Bucket.LEAK }
        val report = MonthReport(
            month = month, incomePaise = income, spendPaise = spend, needsPaise = needs, wantsPaise = wants,
            leaksPaise = leaks, investPaise = invest, transferPaise = transfer, txnCount = txns.size,
            categories = lines, leakCategories = leakLines, topPayees = payees, dailySpend = daily,
            modeSplit = modes, weekdaySpend = weekdays, regretPaise = regret,
            smallTicketCount = small.size, smallTicketPaise = small.sumOf { it.amountPaise },
            prevSpendPaise = prevSpendTx.sumOf { it.amountPaise }, prevLeaksPaise = prevLeaks,
            budgetPaise = cfg.budgetPaise, projectedSpendPaise = projected, noSpendDays = noSpend,
            split = split, leakTenYearValuePaise = leakFuture, insights = emptyList(), incomeEstimated = estimated,
        )
        return report.copy(insights = insights(report, isCurrent))
    }

    private fun pct(part: Long, whole: Long): Int = if (whole <= 0) 0 else ((part * 100.0) / whole).roundToInt()

    /** Plain-language takeaways, most useful first. At most five so they get read. */
    fun insights(r: MonthReport, inProgress: Boolean): List<Insight> {
        if (r.isEmpty) return emptyList()
        val out = mutableListOf<Insight>()
        if (r.leaksPaise > 0) {
            out += Insight("🕳️", "Leaks took ${Money.format(r.leaksPaise)} (${pct(r.leaksPaise, r.spendPaise)}% of spending). " +
                "Invested monthly instead, that's about ${Money.compact(r.leakTenYearValuePaise)} in 10 years.", Insight.Tone.WARN)
        }
        if (r.prevLeaksPaise > 0 && !inProgress) {
            val d = ((r.leaksPaise - r.prevLeaksPaise) * 100 / r.prevLeaksPaise).toInt()
            if (d <= -10) out += Insight("🎉", "You cut leaks by ${-d}% vs last month. That is real money kept.", Insight.Tone.GOOD)
            else if (d >= 20) out += Insight("📈", "Leaks rose ${d}% vs last month. Pick one to pause next month.", Insight.Tone.WARN)
        }
        if (r.split.basedOnIncome) {
            when {
                r.split.followsRule -> out += Insight("🏆", "You followed 50/30/20 this month: ${r.split.needsPct}/${r.split.wantsPct}/${r.split.savePct}.", Insight.Tone.GOOD)
                r.savingsRatePct < 20 -> out += Insight("🎯", "Savings rate is ${r.savingsRatePct}%. Getting to 20% means keeping ${Money.format((r.incomePaise * 20 / 100) - r.savedPaise)} more.", Insight.Tone.INFO)
            }
        }
        r.categories.firstOrNull { it.overBudget }?.let {
            out += Insight("🚨", "${it.category.name} is over its budget by ${Money.format(it.paise - (it.budgetPaise ?: 0))}.", Insight.Tone.WARN)
        }
        if (inProgress && r.budgetPaise > 0 && r.projectedSpendPaise > r.budgetPaise) {
            out += Insight("⏱️", "At this pace you'll spend about ${Money.format(r.projectedSpendPaise)}, over your ${Money.format(r.budgetPaise)} budget.", Insight.Tone.WARN)
        }
        r.categories.filter { (it.deltaPct ?: 0) >= 40 && it.paise >= 1_000_00 }.maxByOrNull { it.paise }?.let {
            out += Insight("🔎", "${it.category.name} is up ${it.deltaPct}% vs last month.", Insight.Tone.INFO)
        }
        if (r.regretPaise > 0) out += Insight("😬", "You marked ${Money.format(r.regretPaise)} as regret. Those are the easiest cuts.", Insight.Tone.INFO)
        if (r.smallTicketCount >= 10) out += Insight("☕", "${r.smallTicketCount} small payments added up to ${Money.format(r.smallTicketPaise)}.", Insight.Tone.INFO)
        if (r.noSpendDays >= 3) out += Insight("🌿", "${r.noSpendDays} no-spend days this month. Shabash!", Insight.Tone.GOOD)
        return out.take(5)
    }
}
