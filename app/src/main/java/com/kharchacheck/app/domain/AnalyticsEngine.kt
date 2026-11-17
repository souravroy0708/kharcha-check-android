package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** One month on the trend charts. "Spend" = Needs + Wants + Leaks; investing and transfers are separate. */
data class MonthPoint(
    val month: YearMonth,
    val incomePaise: Long,
    val spendPaise: Long,
    val investPaise: Long,
    val needsPaise: Long,
    val wantsPaise: Long,
    val leaksPaise: Long,
) {
    val netPaise: Long get() = incomePaise - spendPaise
    /** Null when no income was logged (a rate would be meaningless). */
    val savingsRatePct: Int? get() = if (incomePaise > 0) ((netPaise * 100) / incomePaise).toInt().coerceIn(-200, 100) else null
}

data class CategoryShare(
    val category: Category,
    val totalPaise: Long,
    val sharePct: Float,
    val count: Int,
    val perMonth: List<Long>,
    /** Latest month vs the average of the months before it, in %; null without enough history. */
    val trendPct: Int?,
) {
    val monthlyAvgPaise: Long get() = if (perMonth.isEmpty()) 0 else perMonth.sum() / perMonth.size
    /** Yearly run-rate at the current average: "₹54,000 a year on food delivery". */
    val yearlyPaise: Long get() = monthlyAvgPaise * 12
}

data class Payee(val name: String, val totalPaise: Long, val count: Int, val categoryId: Long)

/** Same payee, similar amount, most months: rent, EMIs, SIPs, subscriptions, the newspaper. */
data class RegularPayment(val payee: String, val categoryId: Long, val typicalPaise: Long, val monthsSeen: Int, val lastDate: LocalDate) {
    val yearlyPaise: Long get() = typicalPaise * 12
}

/** Time-of-day buckets for the "when do I spend" heatmap. */
enum class DayPart(val label: String, val hours: IntRange) {
    MORNING("Morning", 5..11), AFTERNOON("Afternoon", 12..16), EVENING("Evening", 17..21), NIGHT("Night", 0..4);
    companion object { fun of(hour: Int) = if (hour >= 22) NIGHT else entries.first { hour in it.hours } }
}

data class Analytics(
    val months: List<MonthPoint>,
    val categories: List<CategoryShare>,
    val buckets: Map<Bucket, Long>,
    val payees: List<Payee>,
    val regulars: List<RegularPayment>,
    val outliers: List<Txn>,
    /** [weekday 0=Mon..6][DayPart.ordinal] total spend. */
    val heat: List<List<Long>>,
    val modeTotals: Map<PayMode, Long>,
    val avgMonthlySpendPaise: Long,
    val avgMonthlyIncomePaise: Long,
    val avgDailySpendPaise: Long,
    val bestMonth: MonthPoint?,
    val spendTrendPct: Int?,
    val txnCount: Int,
) {
    val totalSpendPaise: Long get() = months.sumOf { it.spendPaise }
    val totalIncomePaise: Long get() = months.sumOf { it.incomePaise }
    val totalInvestPaise: Long get() = months.sumOf { it.investPaise }
    val isEmpty: Boolean get() = txnCount == 0
    val peakHeat: Long get() = heat.flatten().maxOrNull() ?: 0
    val busiest: Pair<DayOfWeek, DayPart>? get() {
        if (peakHeat <= 0) return null
        heat.forEachIndexed { d, row -> row.forEachIndexed { p, v -> if (v == peakHeat) return DayOfWeek.of(d + 1) to DayPart.entries[p] } }
        return null
    }
}

/**
 * Multi-month analytics, computed on the phone in one pass-ish over the user's entries.
 * Pure function; tested in AnalyticsTest.
 */
object AnalyticsEngine {

    fun build(all: List<Txn>, categories: Map<Long, Category>, end: YearMonth, monthCount: Int, today: LocalDate = LocalDate.now()): Analytics {
        val months = (monthCount - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        val first = months.first()
        val inRange = all.filter { it.month in first..end }
        fun bucket(t: Txn) = categories[t.categoryId]?.bucket ?: Bucket.WANT
        fun isSpend(t: Txn) = t.type == TxType.EXPENSE && bucket(t).isSpend
        val spendTx = inRange.filter(::isSpend)
        val byMonth = inRange.groupBy { it.month }

        val points = months.map { m ->
            val l = byMonth[m].orEmpty()
            val s = l.filter(::isSpend)
            MonthPoint(
                month = m,
                incomePaise = l.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise },
                spendPaise = s.sumOf { it.amountPaise },
                investPaise = l.filter { it.type == TxType.INVEST }.sumOf { it.amountPaise },
                needsPaise = s.filter { bucket(it) == Bucket.NEED }.sumOf { it.amountPaise },
                wantsPaise = s.filter { bucket(it) == Bucket.WANT }.sumOf { it.amountPaise },
                leaksPaise = s.filter { bucket(it) == Bucket.LEAK }.sumOf { it.amountPaise },
            )
        }

        val totalSpend = spendTx.sumOf { it.amountPaise }
        val cats = spendTx.groupBy { it.categoryId }.mapNotNull { (id, l) ->
            val cat = categories[id] ?: return@mapNotNull null
            val per = months.map { m -> l.filter { it.month == m }.sumOf { it.amountPaise } }
            CategoryShare(cat, l.sumOf { it.amountPaise }, if (totalSpend > 0) l.sumOf { it.amountPaise } * 100f / totalSpend else 0f, l.size, per, trend(per))
        }.sortedByDescending { it.totalPaise }

        val payees = spendTx.filter { it.payee.isNotBlank() }.groupBy { it.payee.trim().lowercase() }.map { (_, l) ->
            Payee(l.first().payee.trim(), l.sumOf { it.amountPaise }, l.size, l.groupingBy { it.categoryId }.eachCount().maxBy { it.value }.key)
        }.sortedByDescending { it.totalPaise }.take(8)

        val heat = List(7) { MutableList(4) { 0L } }
        spendTx.forEach { t -> heat[t.date.dayOfWeek.value - 1][DayPart.of(t.hour).ordinal] += t.amountPaise }

        // Average daily spend over elapsed days only (so a half-finished month doesn't look cheap).
        val startDay = first.atDay(1)
        val endDay = minOf(end.atEndOfMonth(), today)
        val days = (ChronoUnit.DAYS.between(startDay, endDay) + 1).coerceAtLeast(1)
        val activeMonths = points.filter { it.spendPaise > 0 || it.incomePaise > 0 }

        return Analytics(
            months = points,
            categories = cats,
            buckets = listOf(Bucket.NEED, Bucket.WANT, Bucket.LEAK).associateWith { b -> spendTx.filter { bucket(it) == b }.sumOf { it.amountPaise } },
            payees = payees,
            regulars = regulars(all, end, today),
            outliers = outliers(spendTx),
            heat = heat,
            modeTotals = spendTx.groupBy { it.mode }.mapValues { e -> e.value.sumOf { it.amountPaise } },
            avgMonthlySpendPaise = if (activeMonths.isEmpty()) 0 else activeMonths.sumOf { it.spendPaise } / activeMonths.size,
            avgMonthlyIncomePaise = if (activeMonths.isEmpty()) 0 else activeMonths.sumOf { it.incomePaise } / activeMonths.size,
            avgDailySpendPaise = totalSpend / days,
            bestMonth = points.filter { it.savingsRatePct != null }.maxByOrNull { it.savingsRatePct!! },
            spendTrendPct = trend(points.map { it.spendPaise }),
            txnCount = inRange.size,
        )
    }

    /** Last value vs the average of the earlier non-zero values. Needs at least 2 earlier months. */
    internal fun trend(series: List<Long>): Int? {
        if (series.size < 3) return null
        val prev = series.dropLast(1).filter { it > 0 }
        if (prev.size < 2) return null
        val avg = prev.sum().toDouble() / prev.size
        return (((series.last() - avg) / avg) * 100).roundToInt()
    }

    /**
     * Regular payments: same payee in at least 3 of the last 4 complete-or-current months,
     * with amounts within ±15% of their median. Catches rent, EMIs, SIPs and subscriptions.
     */
    internal fun regulars(all: List<Txn>, end: YearMonth, today: LocalDate): List<RegularPayment> {
        val window = (0..3).map { end.minusMonths(it.toLong()) }.toSet()
        return all.filter { (it.type == TxType.EXPENSE || it.type == TxType.INVEST) && it.payee.isNotBlank() && it.month in window && !it.date.isAfter(today) }
            .groupBy { it.payee.trim().lowercase() }
            .mapNotNull { (_, l) ->
                val perMonth = l.groupBy { it.month }.mapValues { e -> e.value.sumOf { it.amountPaise } }
                if (perMonth.size < 3) return@mapNotNull null
                val sorted = perMonth.values.sorted()
                val median = sorted[sorted.size / 2]
                if (median <= 0 || perMonth.values.any { kotlin.math.abs(it - median) > median * 15 / 100 }) return@mapNotNull null
                RegularPayment(l.first().payee.trim(), l.last().categoryId, median, perMonth.size, l.maxOf { it.date })
            }
            .sortedByDescending { it.typicalPaise }
    }

    /** Unusually large spends: more than 3× the median of their category, with enough history to judge. */
    internal fun outliers(spend: List<Txn>): List<Txn> = spend.groupBy { it.categoryId }.flatMap { (_, l) ->
        if (l.size < 5) return@flatMap emptyList()
        val sorted = l.map { it.amountPaise }.sorted()
        val median = sorted[sorted.size / 2]
        l.filter { it.amountPaise > median * 3 && it.amountPaise >= 500_00 }
    }.sortedByDescending { it.amountPaise }.take(5)

    data class Week(val spendPaise: Long, val avgPrevPaise: Long, val topCategory: Category?, val topPaise: Long) {
        val deltaPct: Int? get() = if (avgPrevPaise > 0) (((spendPaise - avgPrevPaise) * 100) / avgPrevPaise).toInt() else null
    }

    /** Last full Mon–Sun week vs the average of the 4 weeks before it. For the Monday digest. */
    fun lastWeek(all: List<Txn>, categories: Map<Long, Category>, today: LocalDate): Week {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val start = thisMonday.minusWeeks(1)
        fun spendIn(from: LocalDate, to: LocalDate) = all.filter {
            it.type == TxType.EXPENSE && (categories[it.categoryId]?.bucket?.isSpend != false) && !it.date.isBefore(from) && it.date.isBefore(to)
        }
        val week = spendIn(start, thisMonday)
        val prev = (1..4).map { w -> spendIn(start.minusWeeks(w.toLong()), start.minusWeeks(w - 1L)).sumOf { it.amountPaise } }.filter { it > 0 }
        val top = week.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }.maxByOrNull { it.value }
        return Week(week.sumOf { it.amountPaise }, if (prev.isEmpty()) 0 else prev.sum() / prev.size, top?.key?.let { categories[it] }, top?.value ?: 0)
    }

    /** Everything about one category over the period, for the drill-down screen. */
    data class CategoryDetail(val share: CategoryShare?, val months: List<YearMonth>, val payees: List<Payee>, val txns: List<Txn>, val avgTicketPaise: Long)

    fun category(all: List<Txn>, categories: Map<Long, Category>, categoryId: Long, end: YearMonth, monthCount: Int): CategoryDetail {
        val a = build(all.filter { it.categoryId == categoryId }, categories, end, monthCount)
        val months = (monthCount - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        val txns = all.filter { it.categoryId == categoryId && it.month in months.first()..end }.sortedByDescending { it.epochMillis }
        val payees = txns.filter { it.payee.isNotBlank() }.groupBy { it.payee.trim().lowercase() }
            .map { (_, l) -> Payee(l.first().payee.trim(), l.sumOf { it.amountPaise }, l.size, categoryId) }.sortedByDescending { it.totalPaise }.take(6)
        val share = a.categories.firstOrNull() ?: categories[categoryId]?.let { c ->
            // Income / invest categories aren't "spend", so build their monthly series directly.
            val per = months.map { m -> txns.filter { it.month == m }.sumOf { it.amountPaise } }
            CategoryShare(c, per.sum(), 100f, txns.size, per, trend(per))
        }
        return CategoryDetail(share, months, payees, txns, if (txns.isEmpty()) 0 else txns.sumOf { it.amountPaise } / txns.size)
    }
}
