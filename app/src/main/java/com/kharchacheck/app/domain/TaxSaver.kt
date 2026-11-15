package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate

enum class TaxRegime(val label: String) { UNKNOWN("Not sure"), OLD("Old regime"), NEW("New regime") }

/**
 * Common old-regime deductions for salaried people. Limits are the long-standing ones; they can change in a
 * Budget, so the screen says so. Educational, not tax advice.
 */
enum class TaxBucket(val label: String, val short: String, private val limitPaise: Long, private val seniorLimitPaise: Long, val covers: String) {
    C80("Section 80C", "80C", 1_50_000_00, 1_50_000_00,
        "PPF, EPF / VPF, ELSS mutual funds, life insurance premium, children's tuition fees, home-loan principal, 5-year tax-saver FD, Sukanya Samriddhi, NSC"),
    NPS_1B("Section 80CCD(1B)", "NPS extra", 50_000_00, 50_000_00, "Your own NPS contributions, over and above 80C"),
    D80_SELF("Section 80D (you & family)", "Health: you", 25_000_00, 50_000_00, "Health insurance for yourself, spouse and children; preventive check-ups"),
    D80_PARENTS("Section 80D (parents)", "Health: parents", 25_000_00, 50_000_00, "Health insurance for your parents");

    fun limit(senior: Boolean) = if (senior) seniorLimitPaise else limitPaise
}

data class BucketStatus(val bucket: TaxBucket, val usedPaise: Long, val limitPaise: Long, val entries: List<Txn>, val manualPaise: Long) {
    val counted: Long get() = minOf(usedPaise, limitPaise)
    val leftPaise: Long get() = (limitPaise - usedPaise).coerceAtLeast(0)
}

data class TaxSummary(val fyStartYear: Int, val buckets: List<BucketStatus>, val slabPct: Int, val daysLeft: Long) {
    val fyLabel: String get() = "FY $fyStartYear-${(fyStartYear + 1) % 100}"
    val deductionPaise: Long get() = buckets.sumOf { it.counted }
    /** Tax saved so far and still possible, at the user's slab plus 4% health & education cess. */
    val savedTaxPaise: Long get() = deductionPaise * slabPct * 104 / 10_000
    val possibleMoreTaxPaise: Long get() = buckets.sumOf { it.leftPaise } * slabPct * 104 / 10_000
}

object TaxSaver {
    /** Indian financial year: April to March. Returns the starting year (FY 2026-27 → 2026). */
    fun fyStart(d: LocalDate) = if (d.monthValue >= 4) d.year else d.year - 1

    private val HEALTH = Regex("""health|medi ?claim|star health|care health|niva|bupa|hdfc ergo|icici lombard|tata aig|aditya birla health|religare|manipal ?cigna|optima|family floater""", RegexOption.IGNORE_CASE)
    private val ELSS = Regex("""elss|tax ?saver|taxsaver|long term equity""", RegexOption.IGNORE_CASE)
    private val TAX_FD = Regex("""tax ?saver|tax ?saving|5 ?y(ea)?r""", RegexOption.IGNORE_CASE)
    private val NPS = Regex("""\bnps\b|national pension|pran""", RegexOption.IGNORE_CASE)

    /** A sensible default bucket for an entry; the user can change it. Null = not tax-relevant. */
    fun suggest(t: Txn, cat: Category?): TaxBucket? {
        val key = cat?.key ?: return null
        val text = "${t.payee} ${t.note}"
        return when {
            t.type == TxType.INCOME -> null
            key == "insurance" -> if (HEALTH.containsMatchIn(text)) TaxBucket.D80_SELF else TaxBucket.C80
            key == "ppf_epf_nps" -> if (NPS.containsMatchIn(text)) TaxBucket.NPS_1B else TaxBucket.C80
            key == "education" -> TaxBucket.C80
            key == "sip" && ELSS.containsMatchIn(text) -> TaxBucket.C80
            key == "fd_rd" && TAX_FD.containsMatchIn(text) -> TaxBucket.C80
            key == "medical" && HEALTH.containsMatchIn(text) -> TaxBucket.D80_SELF
            else -> null
        }
    }

    /**
     * [assigned]: user choices per entry id (null value = "not for tax"). [manual]: amounts paid outside the app
     * this FY (EPF from the salary slip, home-loan principal…).
     */
    fun summary(all: List<Txn>, cats: Map<Long, Category>, today: LocalDate, assigned: Map<Long, TaxBucket?>, manual: Map<TaxBucket, Long>,
                slabPct: Int, selfSenior: Boolean, parentsSenior: Boolean): TaxSummary {
        val fy = fyStart(today)
        val from = LocalDate.of(fy, 4, 1); val to = LocalDate.of(fy + 1, 3, 31)
        val inFy = all.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val byBucket = inFy.mapNotNull { t -> val b = if (t.id in assigned) assigned[t.id] else suggest(t, cats[t.categoryId]); b?.let { it to t } }
            .groupBy({ it.first }, { it.second })
        val buckets = TaxBucket.entries.map { b ->
            val list = byBucket[b].orEmpty()
            val m = manual[b] ?: 0
            BucketStatus(b, list.sumOf { it.amountPaise } + m, b.limit(if (b == TaxBucket.D80_PARENTS) parentsSenior else selfSenior), list, m)
        }
        return TaxSummary(fy, buckets, slabPct, java.time.temporal.ChronoUnit.DAYS.between(today, to).coerceAtLeast(0))
    }

    /** Entries in this FY that could be tax-relevant, for the "assign" list. */
    fun candidates(all: List<Txn>, cats: Map<Long, Category>, today: LocalDate): List<Txn> {
        val fy = fyStart(today)
        val from = LocalDate.of(fy, 4, 1); val to = LocalDate.of(fy + 1, 3, 31)
        val keys = setOf("insurance", "ppf_epf_nps", "education", "sip", "fd_rd", "medical", "stocks")
        return all.filter { !it.date.isBefore(from) && !it.date.isAfter(to) && cats[it.categoryId]?.key in keys }.sortedByDescending { it.epochMillis }
    }

}
