package com.kharchacheck.app.domain.read

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

/**
 * One character from the PDF with its position (PDF text-space, y grows downwards).
 * [word] is the PDF library's own word number: characters with the same number were written as one word.
 */
data class Glyph(val text: String, val x: Float, val width: Float, val y: Float, val height: Float, val page: Int, val word: Int = -1)

/** A word: glyphs that sit next to each other on the same line. */
data class Word(val text: String, val x0: Float, val x1: Float, val y: Float, val height: Float, val page: Int) {
    val cx: Float get() = (x0 + x1) / 2
}

/**
 * Position-aware statement table reader: the same idea as desktop table extractors (Tabula, Camelot).
 *
 * 1. Glyphs → words, splitting wherever there's a visible gap (fixes "340.0024,660.00" glued columns).
 * 2. Words → lines by y position.
 * 3. Find the header row (Date / Narration / Withdrawal / Deposit / Balance …) and the x-position of each column.
 * 4. Every word below is assigned to the column it sits under; wrapped narrations join the row above.
 * 5. Debit vs credit comes from the column the number is in, then is double-checked against the balance.
 */
object PdfTable {

    enum class Field { DATE, VALUE_DATE, DESC, REF, DEBIT, CREDIT, AMOUNT, TYPE, BALANCE }

    private data class Col(val field: Field, val x0: Float, val x1: Float) { val cx get() = (x0 + x1) / 2 }

    // ---------------------------------------------------------------- glyphs → words → lines

    /**
     * Glyphs → words. Uses the PDF library's own word boundaries (robust to fonts that report zero or wrong
     * character widths), and only splits *inside* a word where there's a clear jump in x, which is how two
     * columns printed with no space between them ("340.0024,660.00") are separated.
     */
    fun words(glyphs: List<Glyph>): List<Word> {
        val out = mutableListOf<Word>()
        val groups = mutableListOf<MutableList<Glyph>>()
        for (g in glyphs) {
            if (g.text.isBlank()) { groups.add(mutableListOf()); continue }
            val cur = groups.lastOrNull()
            val prev = cur?.lastOrNull()
            val sameWord = prev != null && prev.page == g.page && abs(prev.y - g.y) < maxOf(prev.height, g.height) * 0.5f &&
                (if (g.word >= 0) g.word == prev.word else g.x - (prev.x + prev.width) < maxOf(prev.width, prev.height * 0.5f) * 0.6f)
            if (sameWord) cur!!.add(g) else groups.add(mutableListOf(g))
        }
        for (grp in groups) {
            if (grp.isEmpty()) continue
            // Typical advance between characters inside this word.
            val deltas = grp.zipWithNext { a, b -> b.x - a.x }.filter { it > 0 }.sorted()
            val median = if (deltas.isEmpty()) 0f else deltas[deltas.size / 2]
            var piece = mutableListOf(grp.first())
            fun flush() {
                val t = piece.joinToString("") { it.text }.trim()
                if (t.isNotEmpty()) out += Word(t, piece.first().x, piece.last().x + maxOf(piece.last().width, median), piece.first().y,
                    piece.maxOf { it.height }, piece.first().page)
            }
            for (i in 1 until grp.size) {
                val jump = grp[i].x - grp[i - 1].x
                val big = median > 0 && jump > median * 2.6f && jump > grp[i].height * 0.9f
                if (big) { flush(); piece = mutableListOf() }
                piece.add(grp[i])
            }
            flush()
        }
        return out
    }

    fun lines(words: List<Word>): List<List<Word>> {
        val out = mutableListOf<MutableList<Word>>()
        for (w in words.sortedWith(compareBy<Word>({ it.page }, { it.y }, { it.x0 }))) {
            val last = out.lastOrNull()
            val ref = last?.firstOrNull()
            if (ref != null && ref.page == w.page && abs(ref.y - w.y) <= maxOf(ref.height, w.height) * 0.6f) last += w else out += mutableListOf(w)
        }
        return out.map { l -> l.sortedBy { it.x0 } }
    }

    // ---------------------------------------------------------------- header detection

    private fun fieldOf(label: String): Field? {
        val l = label.lowercase(Locale.ENGLISH).replace(Regex("""[^a-z/ .]"""), " ").trim()
        return when {
            Regex("""value\s*d(ate|t)""").containsMatchIn(l) -> Field.VALUE_DATE
            Regex("""\b(txn|tran|transaction|posting|post)?\s*date\b|^dt\.?$""").containsMatchIn(l) -> Field.DATE
            Regex("""withdraw|debit|\bdr\b|paid out|money out""").containsMatchIn(l) && !l.contains("cr/dr") && !l.contains("dr/cr") -> Field.DEBIT
            Regex("""deposit|credit|\bcr\b|paid in|money in""").containsMatchIn(l) && !l.contains("cr/dr") && !l.contains("dr/cr") -> Field.CREDIT
            Regex("""balance|\bbal\b""").containsMatchIn(l) -> Field.BALANCE
            Regex("""\bamount\b|\bamt\b""").containsMatchIn(l) -> Field.AMOUNT
            Regex("""dr\s*/\s*cr|cr\s*/\s*dr|^type$""").containsMatchIn(l) -> Field.TYPE
            Regex("""narration|description|particulars|details|remarks|transaction""").containsMatchIn(l) -> Field.DESC
            Regex("""ref|chq|cheque|utr|instrument""").containsMatchIn(l) -> Field.REF
            else -> null
        }
    }

    /**
     * Groups header words into column labels ("Withdrawal Amt.", "Closing Balance"). Works across lines:
     * words stacked in the same column (a two-line header cell) overlap horizontally and join one label.
     */
    private fun headerColumns(line: List<Word>): List<Col>? {
        val groups = mutableListOf<MutableList<Word>>()
        for (w in line.sortedBy { it.x0 }) {
            val g = groups.lastOrNull()
            val gx1 = g?.maxOf { it.x1 } ?: Float.NEGATIVE_INFINITY
            if (g != null && w.x0 - gx1 < w.height * 0.9f) g += w else groups += mutableListOf(w)
        }
        val cols = groups.mapNotNull { g ->
            val label = g.sortedWith(compareBy<Word>({ it.y }, { it.x0 })).joinToString(" ") { it.text }
            fieldOf(label)?.let { Col(it, g.minOf { w -> w.x0 }, g.maxOf { w -> w.x1 }) }
        }
        val fields = cols.map { it.field }.toSet()
        val hasDate = Field.DATE in fields
        val hasMoney = fields.any { it in setOf(Field.DEBIT, Field.CREDIT, Field.AMOUNT) }
        return if (hasDate && hasMoney) cols.distinctBy { it.field }.sortedBy { it.x0 } else null
    }

    // ---------------------------------------------------------------- rows

    private val NUMBER = Regex("""^\(?-?(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?\)?\s*(cr|dr)?\.?$""", RegexOption.IGNORE_CASE)
    private val FOOTER = Regex("""(closing balance|opening balance|statement summary|total|grand total|page \d+ of|generated on|computer generated|end of statement|carried forward|brought forward)""", RegexOption.IGNORE_CASE)

    private data class Num(val paise: Long, val mark: String, val negative: Boolean)

    private fun num(text: String): Num? {
        val m = NUMBER.find(text.trim()) ?: return null
        val rupees = m.groupValues[1].replace(",", "")
        val frac = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }
        return Num((rupees + frac).toLong(), m.groupValues[3].lowercase(Locale.ENGLISH), text.contains('-') || text.trim().startsWith("("))
    }

    private class Building(val date: LocalDate, val desc: StringBuilder, val values: MutableMap<Field, Num>, var ref: String, val lastY: Float, val page: Int)

    /**
     * Returns parsed rows, or null when no statement table header was found (caller falls back to text parsing).
     */
    fun parse(glyphs: List<Glyph>, hint: YearMonth, creditCard: Boolean = false): StatementParser.Result? {
        val allLines = lines(words(glyphs))
        var cols: List<Col>? = null
        val built = mutableListOf<Building>()
        var open: Building? = null
        var inTable = false
        var openingBalance: Long? = null

        var skip = 0
        var pending = ""   // description text that belongs to the NEXT row (e.g. ICICI's bold payee name above the date line)
        fun isDateLine(l: List<Word>, c: List<Col>): Boolean {
            val dt = l.filter { colOf(it, c) == Field.DATE }.joinToString(" ") { it.text }
            return dt.isNotBlank() && StatementParser.parseDateCell(dt, hint) != null
        }
        for ((li, line) in allLines.withIndex()) {
            if (skip > 0) { skip--; continue }
            val text = line.joinToString(" ") { it.text }
            // Header: this line, or this line merged with the next one or two (two-line header cells).
            val header = (0..2).firstNotNullOfOrNull { extra ->
                val window = allLines.subList(li, minOf(allLines.size, li + extra + 1))
                val sameBlock = window.all { it.first().page == line.first().page && abs(it.first().y - line.first().y) <= line.first().height * 3f }
                val noDates = window.none { l -> l.any { w -> w.text.any(Char::isDigit) && StatementParser.parseDateCell(w.text, hint) != null } }
                // Every line of a multi-line header must itself contain a heading word (so a title line above isn't merged in).
                val allHeadingLines = extra == 0 || window.all { l -> l.any { w -> fieldOf(w.text) != null } }
                if (!sameBlock || !noDates || !allHeadingLines || window.size < extra + 1) null
                else headerColumns(window.flatten())?.let { it to extra }
            }
            if (header != null) { cols = header.first; inTable = true; open = null; pending = ""; skip = header.second; continue }
            if (Regex("""opening balance""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                line.mapNotNull { num(it.text) }.lastOrNull()?.let { openingBalance = it.paise }
                continue
            }
            val c = cols ?: continue
            if (!inTable) continue
            if (FOOTER.containsMatchIn(text) && line.none { StatementParser.parseDateCell(it.text, hint) != null && colOf(it, c) == Field.DATE }) { open = null; continue }

            // Assign each word to the column it sits under.
            val byField = mutableMapOf<Field, MutableList<Word>>()
            for (w in line) byField.getOrPut(colOf(w, c)) { mutableListOf() } += w
            val dateText = byField[Field.DATE].orEmpty().joinToString(" ") { it.text }
            val date = if (dateText.isNotBlank()) StatementParser.parseDateCell(dateText, hint) else null
            val values = mutableMapOf<Field, Num>()
            for (f in listOf(Field.DEBIT, Field.CREDIT, Field.AMOUNT, Field.BALANCE)) {
                val ws = byField[f].orEmpty()
                val joined = ws.joinToString("") { it.text }
                (num(joined) ?: ws.lastOrNull()?.let { num(it.text) })?.let { values[f] = it }
            }
            byField[Field.TYPE]?.joinToString("") { it.text }?.lowercase(Locale.ENGLISH)?.let { t ->
                values[Field.AMOUNT]?.let { a -> if (a.mark.isEmpty() && (t.startsWith("cr") || t.startsWith("dr"))) values[Field.AMOUNT] = a.copy(mark = t.take(2)) }
            }
            val desc = (byField[Field.DESC].orEmpty() + byField[Field.REF].orEmpty().takeIf { Field.DESC !in c.map { it.field } }.orEmpty())
                .joinToString(" ") { it.text }
            val ref = byField[Field.REF].orEmpty().joinToString("") { it.text }.filter { it.isLetterOrDigit() }

            if (date != null) {
                // "NAME | narration…" so the payee is clearly separated from the UPI details that follow.
                val first = when {
                    pending.isNotBlank() && desc.isNotBlank() -> "$pending | $desc"
                    pending.isNotBlank() -> "$pending |"
                    else -> desc
                }
                pending = ""
                val b = Building(date, StringBuilder(first), values, ref, line.first().y, line.first().page)
                built += b; open = b
            } else {
                // Peek: is this line hugging the next row's date line rather than continuing the current row?
                val next = allLines.getOrNull(li + 1)
                val prev = allLines.getOrNull(li - 1)
                if (next != null && next.first().page == line.first().page && values.isEmpty() && desc.isNotBlank() && isDateLine(next, c)) {
                    val gapNext = next.first().y - line.first().y
                    val gapPrev = if (prev != null && prev.first().page == line.first().page) line.first().y - prev.first().y else Float.MAX_VALUE
                    if (gapNext < gapPrev * 0.8f) { pending = listOf(pending, desc).filter { it.isNotBlank() }.joinToString(" "); continue }
                }
                val o = open ?: continue
                val near = line.first().page != o.page || abs(line.first().y - o.lastY) < line.first().height * 6
                if (!near) continue
                if (o.values.keys.none { it != Field.BALANCE } && values.isNotEmpty()) o.values.putAll(values)
                if (desc.isNotBlank() && o.desc.length < 200) o.desc.append(' ').append(desc)
                if (o.ref.isEmpty() && ref.isNotEmpty()) o.ref = ref
            }
        }
        if (cols == null) return null

        val rows = verifyWithBalance(built.mapNotNull { b ->
            val debit = b.values[Field.DEBIT]?.takeIf { it.paise > 0 }
            val credit = b.values[Field.CREDIT]?.takeIf { it.paise > 0 }
            val amount = b.values[Field.AMOUNT]?.takeIf { it.paise > 0 }
            val bal = b.values[Field.BALANCE]?.let { if (it.mark == "dr" || it.negative) -it.paise else it.paise }
            val desc = b.desc.toString().replace(Regex("""\s+"""), " ").trim().trimEnd('|').trim()
            val (amt, dir, ambiguous) = when {
                debit != null && credit != null -> Triple(0L, null, true)         // both columns filled: can't tell
                debit != null -> Triple(debit.paise, false, false)
                credit != null -> Triple(credit.paise, true, false)
                amount != null -> Triple(amount.paise, when { amount.mark == "cr" -> true; amount.mark == "dr" || amount.negative -> false; else -> null }, false)
                else -> Triple(0L, null, false)
            }
            if (amt == 0L && bal == null && !ambiguous) return@mapNotNull null  // not a transaction row
            Candidate(b.date, desc.take(200), amt, dir, bal, b.ref, fromColumn = !ambiguous && amt > 0)
        }, openingBalance, creditCard, hasBalanceColumn = cols!!.any { it.field == Field.BALANCE })
        return StatementParser.Result(rows, creditCard)
    }

    /** A row before verification: [amount] = 0 means "not found or ambiguous". */
    data class Candidate(val date: LocalDate, val desc: String, val amount: Long, val credit: Boolean?, val balance: Long?, val ref: String, val fromColumn: Boolean)

    /**
     * The accuracy core. In a bank statement every row's amount must equal the change in the running balance.
     *  - amount matches the balance change → confirmed (direction from the change)
     *  - no amount, or it disagrees, but the balance chain is reliable overall → amount = balance change (confirmed)
     *  - no balance to check against → keep the amount only if it came cleanly from a debit/credit/amount column;
     *    card statements (no balance column) are trusted the same way
     *  - otherwise → amount 0 and "not sure", so the user fills it in. A blank beats a wrong number.
     */
    fun verifyWithBalance(rs: List<Candidate>, opening: Long?, creditCard: Boolean, hasBalanceColumn: Boolean): List<StatementParser.Row> {
        // Oldest-first or newest-first? Count which neighbour explains each balance change.
        var fwd = 0; var rev = 0
        for (i in 1 until rs.size) {
            val b0 = rs[i - 1].balance ?: continue; val b1 = rs[i].balance ?: continue
            if (rs[i].amount > 0 && abs(abs(b1 - b0) - rs[i].amount) <= 1) fwd++
            if (rs[i - 1].amount > 0 && abs(abs(b1 - b0) - rs[i - 1].amount) <= 1) rev++
        }
        val forward = fwd >= rev
        fun older(i: Int): Long? = if (forward) (if (i == 0) opening else rs[i - 1].balance) else rs.getOrNull(i + 1)?.balance ?: (if (i == rs.lastIndex) opening else null)
        val withBalance = rs.indices.filter { rs[it].balance != null && older(it) != null && rs[it].amount > 0 }
        val matched = withBalance.count { i -> abs(abs(rs[i].balance!! - older(i)!!) - rs[i].amount) <= 1 }
        // The balance column is trustworthy if most rows that can be checked do check out.
        val chainReliable = withBalance.size >= 3 && matched * 10 >= withBalance.size * 7

        return rs.mapIndexed { i, r ->
            val o = older(i)
            val diff = if (r.balance != null && o != null) r.balance - o else null
            fun row(amount: Long, credit: Boolean, sure: Boolean) = StatementParser.Row(r.date, r.desc, amount, credit, r.balance, sure, r.ref)
            val cardCredit = Regex("payment|refund|reversal|cashback", RegexOption.IGNORE_CASE).containsMatchIn(r.desc)
            when {
                // 1. Amount agrees with the balance change: confirmed.
                diff != null && r.amount > 0 && abs(abs(diff) - r.amount) <= 1 -> row(r.amount, diff > 0, true)
                // 2. Amount missing or disagrees, but the balance chain is reliable: the change IS the amount.
                diff != null && diff != 0L && chainReliable -> row(abs(diff), diff > 0, true)
                // 3. Could be checked but doesn't add up: don't guess.
                diff != null -> row(0L, r.credit ?: (diff > 0), false)
                // 4. The statement has a balance column but this row has no balance: columns are probably off.
                hasBalanceColumn && r.balance == null -> row(0L, r.credit ?: false, false)
                // 5. Oldest row (nothing older to compare): trust the column only if the rest of the chain checks out.
                r.balance != null && r.fromColumn -> row(r.amount, r.credit ?: false, chainReliable && r.credit != null)
                // 6. No balance column at all (credit cards, amount-only statements): one clean amount per row.
                !hasBalanceColumn && r.fromColumn -> row(r.amount, r.credit ?: (creditCard && cardCredit), r.credit != null || creditCard)
                else -> row(0L, r.credit ?: false, false)
            }
        }
    }

    private val MONEY = setOf(Field.DEBIT, Field.CREDIT, Field.AMOUNT, Field.BALANCE)

    /**
     * Which column a word belongs to. Works whether headings are left-aligned, right-aligned or centred
     * (ICICI centres "Transaction Remarks" over a wide column):
     *  - numbers snap to the money column whose right edge they line up with (amounts are right-aligned)
     *  - a word overlapping a heading's span belongs to that column
     *  - a word in a gap between headings belongs to the description column (if the table has one),
     *    unless it is right of the first money column; words left of every heading go to the first column
     */
    private fun colOf(w: Word, cols: List<Col>): Field {
        val isNum = w.text.any(Char::isDigit) && num(w.text) != null
        if (isNum) {
            val m = cols.filter { it.field in MONEY }.minByOrNull { abs(w.x1 - it.x1) }
            if (m != null && abs(w.x1 - m.x1) <= maxOf(w.height * 2.5f, 20f)) return m.field
        }
        val tol = w.height * 0.5f
        val over = cols.filter { w.x1 > it.x0 - tol && w.x0 < it.x1 + tol }
            .maxByOrNull { minOf(w.x1, it.x1) - maxOf(w.x0, it.x0) }
        if (over != null) {
            if (over.field in MONEY && !isNum && w.text.length > 3 && cols.any { it.field == Field.DESC }) return Field.DESC
            return over.field
        }
        val first = cols.minBy { it.x0 }
        if (w.x1 <= first.x0) return first.field
        val firstMoney = cols.filter { it.field in MONEY }.minOfOrNull { it.x0 }
        if (cols.any { it.field == Field.DESC } && (firstMoney == null || w.x1 <= firstMoney + tol)) return Field.DESC
        return cols.minBy { c -> if (w.x1 < c.x0) c.x0 - w.x1 else if (w.x0 > c.x1) w.x0 - c.x1 else 0f }.field
    }

    // ---------------------------------------------------------------- diagnostics

    private val SAFE_WORDS = setOf("upi", "neft", "imps", "rtgs", "nach", "ach", "atm", "pos", "cr", "dr", "by", "to", "transfer", "wdl",
        "opening", "closing", "balance", "total", "page", "of", "date", "value", "dt", "amt", "amount", "withdrawal", "deposit", "debit",
        "credit", "narration", "description", "particulars", "ref", "no", "chq", "cheque", "inr", "rs", "txn", "tran", "salary", "payment", "card", "interest")

    /** Hides names and identifiers but keeps layout, dates, amounts and structural words. */
    fun mask(text: String): String {
        val lower = text.lowercase(Locale.ENGLISH)
        if (fieldOf(text) != null || lower.trim('.', ':', '-', '/') in SAFE_WORDS) return text
        if (num(text) != null && text.contains('.')) return text                    // amounts / balances stay
        if (StatementParser.parseDateCell(text, YearMonth.now()) != null) return text  // dates stay
        return text.split(Regex("""(?<=[/\-@*])|(?=[/\-@*])""")).joinToString("") { part ->
            when {
                part.lowercase(Locale.ENGLISH) in SAFE_WORDS -> part
                part.count(Char::isDigit) >= 5 -> part.replace(Regex("""\d"""), "9")  // account / reference numbers
                else -> part.replace(Regex("""[A-Za-z]"""), "x")
            }
        }
    }

    /**
     * A privacy-masked picture of what the reader saw: words with x-positions per line (first [maxPages]
     * pages), detected header columns, and the parsed rows. Lets layout problems be fixed without the PDF.
     */
    fun diagnose(glyphs: List<Glyph>, hint: YearMonth, creditCard: Boolean, maxPages: Int = 2): String = buildString {
        val ls = lines(words(glyphs))
        appendLine("== Kharcha Check reading report ==")
        appendLine("pages with text: ${glyphs.map { it.page }.distinct().size}, glyphs: ${glyphs.size}, lines: ${ls.size}, card: $creditCard")
        appendLine("glyph widths: zero=${glyphs.count { it.width == 0f && it.text.isNotBlank() }}, word ids present=${glyphs.any { it.word >= 0 }}")
        appendLine()
        appendLine("-- lines (page | y | [x0-x1] word ...) --")
        ls.filter { it.first().page < maxPages }.forEach { l ->
            append("p${l.first().page} y=${"%.0f".format(l.first().y)} | ")
            appendLine(l.joinToString("  ") { w -> "[${"%.0f".format(w.x0)}-${"%.0f".format(w.x1)}]${mask(w.text)}" })
        }
        appendLine()
        val res = parse(glyphs, hint, creditCard)
        if (res == null) appendLine("-- NO TABLE HEADER FOUND (text fallback would be used) --")
        else {
            appendLine("-- parsed rows: ${res.rows.size} (sure ${res.rows.count { it.sure }}, zero ${res.rows.count { it.amountPaise == 0L }}) --")
            res.rows.take(40).forEach { r ->
                appendLine("${r.date} amt=${"%d.%02d".format(r.amountPaise / 100, r.amountPaise % 100)} ${if (r.credit) "CR" else "DR"} sure=${r.sure} bal=${r.balancePaise?.let { "%d.%02d".format(it / 100, it % 100) } ?: "-"} | ${mask(r.description).take(40)}")
            }
        }
    }
}
