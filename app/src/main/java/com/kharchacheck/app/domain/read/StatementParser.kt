package com.kharchacheck.app.domain.read

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

/**
 * Turns bank / credit-card statement text into transaction rows. Two inputs:
 *  - [fromLines]: OCR text lines of a PDF page or photo (one table row per line, roughly)
 *  - [fromCsv]: the CSV / TSV download most Indian netbanking sites offer (most accurate)
 *
 * Debit vs credit is decided, in order of trust, by: the running balance column, explicit Dr/Cr,
 * separate withdrawal/deposit columns, then keywords. Rows are always shown for review before saving.
 */
object StatementParser {

    data class Row(
        val date: LocalDate,
        val description: String,
        val amountPaise: Long,
        val credit: Boolean,
        val balancePaise: Long? = null,
        /** True when the direction was confirmed by the balance, a Dr/Cr mark or a dedicated column. */
        val sure: Boolean = false,
        val ref: String = "",
    )

    data class Result(val rows: List<Row>, val creditCard: Boolean)

    class NotReadableException(message: String) : Exception(message)

    private val MONTHS = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val D_NUM = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})\b""")
    private val D_NAMED = Regex("""\b(\d{1,2})[\s\-/]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s\-/,]*(\d{4}|\d{2})?\b""", RegexOption.IGNORE_CASE)
    private val D_ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val AMOUNT = Regex("""(?<![\d.])(\d{1,3}(?:,\d{2,3})+|\d+)\.(\d{2})(?![\d])\s*(cr|dr)?\b""", RegexOption.IGNORE_CASE)
    private val SKIP = Regex("""(opening balance|closing balance|balance b/?f|balance c/?f|brought forward|carried forward|^total|grand total|page \d+|statement (period|date|summary)|minimum amount due|total amount due|payment due|credit limit|available limit|reward points|account summary|generated on|computer generated|end of statement|registered office|customer care|call us|www\.)""", RegexOption.IGNORE_CASE)
    private val CREDIT_WORDS = Regex("""(\bby transfer|\bby clg|neft cr|imps cr|rtgs cr|upi/cr|upi cr|/cr/|\bcr-|salary|refund|reversal|reversed|interest|int\.pd|cashback|deposit|cash dep|received|payment received|thank you)""", RegexOption.IGNORE_CASE)
    private val DEBIT_WORDS = Regex("""(\bto transfer|upi/dr|/dr/|\bdr-|withdrawal|atm|purchase|debit|pos |ecom|nach|ach d|emi|bill ?pay)""", RegexOption.IGNORE_CASE)
    private val CARD_HINTS = Regex("""(credit card|card statement|minimum amount due|total amount due|card no|card number)""", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- OCR lines

    private class Raw(val date: LocalDate, var desc: String, val amounts: MutableList<Pair<Long, String>>)

    fun fromLines(lines: List<String>, hint: YearMonth, creditCard: Boolean? = null): Result {
        val text = lines.joinToString("\n")
        val isCard = creditCard ?: CARD_HINTS.containsMatchIn(text)
        var opening: Long? = null
        val raws = mutableListOf<Raw>()
        var open: Raw? = null

        for (rawLine in lines) {
            val line = rawLine.replace('|', ' ').replace(Regex("""\s+"""), " ").trim()
            if (line.isEmpty()) continue
            if (Regex("""opening balance""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                AMOUNT.findAll(line).lastOrNull()?.let { opening = paise(it) }
                continue
            }
            if (SKIP.containsMatchIn(line)) { open = null; continue }

            val lead = leadingDate(line, hint)
            val body = if (lead != null) line.substring(lead.second) else line
            val noDates = stripDates(body)
            val amounts = AMOUNT.findAll(noDates).map { paise(it) to it.groupValues[3].lowercase() }.toMutableList()
            val desc = AMOUNT.replace(noDates, " ").replace(Regex("""\s+"""), " ").trim()

            if (lead != null) {
                val r = Raw(lead.first, desc, amounts)
                raws += r
                // A row whose amounts wrapped onto the next line stays open to receive them.
                open = if (amounts.isEmpty()) r else null
            } else {
                val target = open ?: raws.lastOrNull() ?: continue
                if (target.amounts.isEmpty() && amounts.isNotEmpty()) target.amounts += amounts
                if (desc.any(Char::isLetter) && target.desc.length < 160 && (target === open || amounts.isEmpty())) target.desc = (target.desc + " " + desc).trim()
                if (target.amounts.isNotEmpty()) open = null
            }
        }

        val usable = raws.filter { it.amounts.isNotEmpty() }
        val rows = if (isCard) cardRows(usable) else bankRows(usable, opening)
        return Result(rows.filter { it.amountPaise > 0 }, isCard)
    }

    private fun cardRows(raws: List<Raw>): List<Row> = raws.map { r ->
        val (amt, mark) = r.amounts.last()
        val credit = when (mark) { "cr" -> true; "dr" -> false; else -> Regex("""(payment received|thank you|refund|reversal|cashback|credit)""", RegexOption.IGNORE_CASE).containsMatchIn(r.desc) }
        Row(r.date, clean(r.desc), amt, credit, sure = mark.isNotEmpty())
    }

    private fun bankRows(raws: List<Raw>, opening: Long?): List<Row> {
        // Each row: txn amount = last non-zero amount before the balance; balance = last amount (when 2+ amounts).
        data class P(val r: Raw, val amt: Long, val bal: Long?, val colCredit: Boolean?, val mark: String)
        val ps = raws.map { r ->
            val a = r.amounts
            if (a.size >= 2) {
                val bal = a.last().first
                val cands = a.dropLast(1)
                val nonZero = cands.withIndex().filter { it.value.first != 0L }
                val pick = nonZero.lastOrNull()?.value ?: cands.last()
                // Two amount columns with one zero (withdrawal, deposit): position tells direction.
                val col = if (cands.size == 2 && nonZero.size == 1) nonZero[0].index == 1 else null
                P(r, pick.first, bal, col, pick.second)
            } else P(r, a[0].first, null, null, a[0].second)
        }
        // Detect chronological order: forward means balance[i] = balance[i-1] ± amount[i].
        var fwd = 0; var rev = 0
        for (i in 1 until ps.size) {
            val b0 = ps[i - 1].bal ?: continue; val b1 = ps[i].bal ?: continue
            if (abs(abs(b1 - b0) - ps[i].amt) <= 1) fwd++
            if (abs(abs(b1 - b0) - ps[i - 1].amt) <= 1) rev++
        }
        val forward = fwd >= rev
        return ps.mapIndexed { i, p ->
            val older: Long? = if (forward) (if (i == 0) opening else ps[i - 1].bal) else ps.getOrNull(i + 1)?.bal
            val byBalance = if (p.bal != null && older != null) when {
                abs(older + p.amt - p.bal) <= 1 -> true
                abs(older - p.amt - p.bal) <= 1 -> false
                else -> null
            } else null
            val byMark = when (p.mark) { "cr" -> true; "dr" -> false; else -> null }
            val byWords = when {
                CREDIT_WORDS.containsMatchIn(p.r.desc) && !DEBIT_WORDS.containsMatchIn(p.r.desc) -> true
                else -> false
            }
            val credit = byBalance ?: byMark ?: p.colCredit ?: byWords
            Row(p.r.date, clean(p.r.desc), p.amt, credit, p.bal, sure = byBalance != null || byMark != null || p.colCredit != null)
        }
    }

    private fun leadingDate(line: String, hint: YearMonth): Pair<LocalDate, Int>? {
        val window = line.take(18)
        val m = listOf(D_NUM, D_ISO, D_NAMED).mapNotNull { it.find(window) }.minByOrNull { it.range.first } ?: return null
        if (m.range.first > 4) return null // allow a serial number before the date
        val d = toDate(m, hint) ?: return null
        return d to (m.range.last + 1).coerceAtMost(line.length)
    }

    private fun stripDates(s: String): String = D_ISO.replace(D_NUM.replace(D_NAMED.replace(s) { m -> if (m.groupValues[3].isNotEmpty() || m.value.length <= 7) " " else m.value }, " "), " ")

    internal fun toDate(m: MatchResult, hint: YearMonth): LocalDate? = runCatching {
        fun yr(s: String) = if (s.isEmpty()) null else s.toInt().let { if (it < 100) 2000 + it else it }
        when {
            m.groupValues.size == 4 && m.value.contains(Regex("[A-Za-z]")) -> {
                val month = MONTHS.getValue(m.groupValues[2].lowercase(Locale.ENGLISH).take(3))
                val y = yr(m.groupValues[3]) ?: if (month > hint.monthValue) hint.year - 1 else hint.year
                LocalDate.of(y, month, m.groupValues[1].toInt())
            }
            m.value.length >= 10 && m.value[4] == '-' -> LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            else -> LocalDate.of(yr(m.groupValues[3])!!, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
    }.getOrNull()

    private fun paise(m: MatchResult): Long = (m.groupValues[1].replace(",", "") + m.groupValues[2]).toLong()

    private fun clean(desc: String): String = desc.replace(Regex("""\s+"""), " ").trim().trimEnd('-', '/', ',').take(160)

    // ---------------------------------------------------------------- CSV / TSV

    fun fromCsv(text: String, hint: YearMonth, creditCard: Boolean = false): Result {
        if (text.any { it == '\u0000' }) throw NotReadableException("This looks like an Excel (.xls/.xlsx) file. In Excel or Google Sheets, use File → Save as / Download → CSV, then attach the CSV.")
        return fromTable(SpreadsheetReader.delimited(text), hint, creditCard)
    }

    /** Any spreadsheet download: .xlsx, HTML ".xls", CSV or TSV. */
    fun fromSpreadsheet(bytes: ByteArray, hint: YearMonth, creditCard: Boolean = false): Result {
        val table = try { SpreadsheetReader.read(bytes) } catch (e: SpreadsheetReader.UnsupportedFormatException) { throw NotReadableException(e.message.orEmpty()) }
        return fromTable(table, hint, creditCard)
    }

    fun fromTable(table: List<List<String>>, hint: YearMonth, creditCard: Boolean = false): Result {
        val headerIdx = table.indexOfFirst { row ->
            val low = row.map { it.lowercase(Locale.ENGLISH) }
            low.any { "date" in it } && low.any { c -> listOf("withdraw", "debit", "amount", "dr").any { it in c } }
        }
        if (headerIdx < 0) throw NotReadableException("Couldn't find the column headings (Date, Narration, Debit/Withdrawal, Credit/Deposit). Is this a bank statement CSV?")
        val h = table[headerIdx].map { it.lowercase(Locale.ENGLISH).trim() }
        fun col(vararg keys: String, not: String? = null) = h.indexOfFirst { c -> keys.any { it in c } && (not == null || not !in c) }
        val cDate = col("txn date", "transaction date", "tran date", "posting date").takeIf { it >= 0 } ?: col("date", not = "value")
        val cDesc = col("narration", "description", "particulars", "remarks", "details", "transaction")
        val cDebit = col("withdrawal", "debit", "dr amount", "paid out")
        val cCredit = col("deposit", "credit", "cr amount", "paid in")
        val cAmount = col("amount", not = "balance").takeIf { cDebit < 0 || cCredit < 0 } ?: -1
        val cType = h.indexOfFirst { it == "dr/cr" || it == "cr/dr" || it == "type" || it == "dr / cr" || it == "debit/credit" }
        val cBal = col("balance")
        val cRef = col("ref", "chq", "cheque", "utr")

        val rows = table.drop(headerIdx + 1).mapNotNull { r ->
            fun cell(i: Int) = if (i in r.indices) r[i].trim() else ""
            val date = parseDateCell(cell(cDate), hint) ?: return@mapNotNull null
            val debit = money(cell(cDebit)); val credit = money(cell(cCredit))
            val (amt, isCredit, sure) = when {
                cDebit >= 0 && cCredit >= 0 && (debit ?: 0) > 0 -> Triple(debit!!, false, true)
                cDebit >= 0 && cCredit >= 0 && (credit ?: 0) > 0 -> Triple(credit!!, true, true)
                cAmount >= 0 -> {
                    val raw = cell(cAmount)
                    val v = money(raw) ?: return@mapNotNull null
                    val t = cell(cType).lowercase(Locale.ENGLISH)
                    when {
                        t.startsWith("cr") || t.startsWith("credit") || raw.trim().endsWith("Cr", true) -> Triple(v, true, true)
                        t.startsWith("dr") || t.startsWith("debit") || raw.trim().endsWith("Dr", true) || raw.trim().startsWith("-") -> Triple(v, false, true)
                        else -> Triple(v, creditCard && Regex("payment|refund|reversal|cashback", RegexOption.IGNORE_CASE).containsMatchIn(cell(cDesc)), false)
                    }
                }
                else -> return@mapNotNull null
            }
            if (amt <= 0) return@mapNotNull null
            Row(date, clean(cell(cDesc)), amt, isCredit, money(cell(cBal)), sure, cell(cRef).trimStart('0'))
        }
        return Result(rows, creditCard)
    }

    fun parseDateCell(s: String, hint: YearMonth): LocalDate? {
        // Excel stores dates as day numbers since 1899-12-30 (e.g. 46327 = 2026-11-01).
        s.trim().toDoubleOrNull()?.let { n -> if (n in 30_000.0..80_000.0) return LocalDate.of(1899, 12, 30).plusDays(n.toLong()) }
        val m = listOf(D_ISO, D_NUM, D_NAMED).firstNotNullOfOrNull { it.find(s) } ?: return null
        return toDate(m, hint)
    }

    fun money(s: String): Long? {
        val c = s.replace(Regex("""[₹,\s]|INR|Rs\.?|Cr|Dr""", RegexOption.IGNORE_CASE), "").trimStart('-', '+')
        if (c.isEmpty()) return null
        val v = c.toBigDecimalOrNull() ?: return null
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    internal fun split(line: String, d: Char): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> q = !q
                ch == d && !q -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
