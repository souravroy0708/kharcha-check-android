package com.kharchacheck.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- AMFI NAV file

/** One mutual fund scheme from AMFI's daily NAVAll.txt. NAV kept as 1/10000 rupee to avoid floating point. */
data class Scheme(val code: String, val name: String, val amc: String, val category: String, val navMicro: Long?, val date: LocalDate?) {
    val nav: BigDecimal? get() = navMicro?.let { BigDecimal.valueOf(it, 4) }
}

/**
 * Parses https://portal.amfiindia.com/spages/NAVAll.txt:
 *   Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date
 * interleaved with category lines ("Open Ended Schemes(Equity Scheme - Flexi Cap Fund)") and fund-house lines.
 * The whole file is downloaded and searched on the phone, so nobody learns which funds the user holds.
 */
object AmfiNav {
    const val URL = "https://portal.amfiindia.com/spages/NAVAll.txt"
    const val FALLBACK_URL = "https://www.amfiindia.com/spages/NAVAll.txt"
    private val DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    fun parse(text: String): List<Scheme> {
        val out = ArrayList<Scheme>(32_000)
        var amc = ""; var category = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.contains(';')) {
                if (Regex("""^(open|close|interval)[\s-]+ended""", RegexOption.IGNORE_CASE).containsMatchIn(line) || line.startsWith("Interval Fund", true)) category = line
                else amc = line
                continue
            }
            val p = line.split(';')
            if (p.size < 6 || !p[0].trim().all(Char::isDigit) || p[0].isBlank()) continue
            val nav = p[4].trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.setScale(4, RoundingMode.HALF_UP)?.unscaledValue()?.toLong()
            val date = runCatching { LocalDate.parse(p[5].trim().replaceFirstChar { it.uppercase() }.let(::fixMonth), DATE) }.getOrNull()
            out += Scheme(p[0].trim(), p[3].trim(), amc, category, nav, date)
        }
        return out
    }

    // "20-APR-2026" / "20-apr-2026" → "20-Apr-2026" so the formatter accepts it.
    private fun fixMonth(s: String): String = s.split('-').let { if (it.size == 3) "${it[0]}-${it[1].lowercase().replaceFirstChar { c -> c.uppercase() }}-${it[2]}" else s }

    /** Words in any order; Direct + Growth plans first (what most app users hold), then shorter names. */
    fun search(schemes: List<Scheme>, query: String, limit: Int = 25): List<Scheme> {
        val words = query.lowercase(Locale.ENGLISH).split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        if (words.size == 1 && words[0].all(Char::isDigit)) return schemes.filter { it.code == words[0] }
        return schemes.asSequence()
            .filter { s -> val n = s.name.lowercase(Locale.ENGLISH); words.all { n.contains(it) } }
            .sortedWith(compareByDescending<Scheme> { it.name.contains("direct", true) && it.name.contains("growth", true) }
                .thenByDescending { it.navMicro != null }.thenBy { it.name.length })
            .take(limit).toList()
    }
}

// ---------------------------------------------------------------- Holdings & net worth

enum class HoldingKind(val label: String, val emoji: String, val live: Boolean) {
    MUTUAL_FUND("Mutual fund", "📈", true),
    STOCKS("Stocks", "📊", false),
    FD_RD("FD / RD", "🔒", false),
    PPF_EPF_NPS("PPF / EPF / NPS", "🏛️", false),
    GOLD("Gold / SGB", "🪙", false),
    OTHER("Other investment", "💼", false),
}

/** units is a decimal string ("123.456") to keep MF unit precision exact. */
data class Holding(
    val id: Long = 0,
    val kind: HoldingKind,
    val name: String,
    val schemeCode: String? = null,
    val units: String = "",
    val investedPaise: Long = 0,
    val manualValuePaise: Long = 0,
)

data class HoldingValue(val holding: Holding, val valuePaise: Long, val live: Boolean, val navDate: LocalDate?) {
    val gainPaise: Long get() = valuePaise - holding.investedPaise
    val gainPct: Double? get() = if (holding.investedPaise > 0) gainPaise * 100.0 / holding.investedPaise else null
}

enum class NetSide { ASSET, LIABILITY }

/** Manually tracked balances: savings account, cash, property, home loan, card outstanding… */
data class NetItem(val id: Long = 0, val side: NetSide, val name: String, val emoji: String, val valuePaise: Long)

data class NetWorth(
    val holdings: List<HoldingValue>,
    val assets: List<NetItem>,
    val liabilities: List<NetItem>,
) {
    val investmentsPaise: Long get() = holdings.sumOf { it.valuePaise }
    val investedPaise: Long get() = holdings.sumOf { it.holding.investedPaise }
    val assetsPaise: Long get() = investmentsPaise + assets.sumOf { it.valuePaise }
    val liabilitiesPaise: Long get() = liabilities.sumOf { it.valuePaise }
    val netPaise: Long get() = assetsPaise - liabilitiesPaise
    val gainPaise: Long get() = investmentsPaise - investedPaise
    val gainPct: Double? get() = if (investedPaise > 0) gainPaise * 100.0 / investedPaise else null
}

object Wealth {
    /** Live MF value = units × latest NAV; everything else uses the value the user entered. */
    fun value(h: Holding, navs: Map<String, Scheme>): HoldingValue {
        val scheme = h.schemeCode?.let { navs[it] }
        val units = h.units.toBigDecimalOrNull()
        if (h.kind.live && scheme?.nav != null && units != null) {
            val paise = units.multiply(scheme.nav).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            return HoldingValue(h, paise, true, scheme.date)
        }
        return HoldingValue(h, if (h.manualValuePaise > 0) h.manualValuePaise else h.investedPaise, false, null)
    }

    fun netWorth(holdings: List<Holding>, items: List<NetItem>, navs: Map<String, Scheme>) = NetWorth(
        holdings.map { value(it, navs) }.sortedByDescending { it.valuePaise },
        items.filter { it.side == NetSide.ASSET }.sortedByDescending { it.valuePaise },
        items.filter { it.side == NetSide.LIABILITY }.sortedByDescending { it.valuePaise },
    )

    /** Monthly history: keep the latest snapshot per month, last 24 months. */
    fun mergeSnapshot(history: Map<YearMonth, Long>, month: YearMonth, netPaise: Long): Map<YearMonth, Long> =
        (history + (month to netPaise)).toSortedMap().entries.toList().takeLast(24).associate { it.key to it.value }
}

// ---------------------------------------------------------------- Auto-backup rotation

object BackupRotation {
    private val NAME = Regex("""^kharcha-auto-(\d{4}-\d{2}-\d{2})\.kcbak$""")
    fun fileName(date: LocalDate) = "kharcha-auto-$date.kcbak"

    /** Names to delete so only the newest [keep] automatic backups remain. Files the user made by hand are never touched. */
    fun toDelete(names: List<String>, keep: Int = 7): List<String> =
        names.mapNotNull { n -> NAME.find(n)?.let { n to it.groupValues[1] } }.sortedByDescending { it.second }.drop(keep).map { it.first }
}
