package com.kharchacheck.app.domain.read

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

/** One character from the PDF with its position (PDF text-space, y grows downwards). */
data class Glyph(val text: String, val x: Float, val width: Float, val y: Float, val height: Float, val page: Int)

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

    fun words(glyphs: List<Glyph>): List<Word> {
        val out = mutableListOf<Word>()
        var cur = StringBuilder(); var x0 = 0f; var x1 = 0f; var y = 0f; var h = 0f; var page = -1
        fun flush() { if (cur.isNotBlank()) out += Word(cur.toString().trim(), x0, x1, y, h, page); cur = StringBuilder() }
        for (g in glyphs) {
            val space = g.text.isBlank()
            val gap = g.x - x1
            val sameLine = g.page == page && abs(g.y - y) < maxOf(h, g.height) * 0.5f
            val charW = if (g.width > 0) g.width else g.height * 0.5f
            if (space || !sameLine || cur.isEmpty() || gap > charW * 0.6f || gap < -charW * 2) {
                flush()
                if (space) { x1 = g.x + g.width; page = g.page; y = g.y; continue }
                x0 = g.x; y = g.y; h = g.height; page = g.page
            }
            cur.append(g.text); x1 = g.x + g.width; h = maxOf(h, g.height)
        }
        flush()
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

    /** Groups header words into labels ("Withdrawal Amt.", "Closing Balance") by horizontal closeness. */
    private fun headerColumns(line: List<Word>): List<Col>? {
        val groups = mutableListOf<MutableList<Word>>()
        for (w in line) {
            val g = groups.lastOrNull()
            if (g != null && w.x0 - g.last().x1 < w.height * 0.9f) g += w else groups += mutableListOf(w)
        }
        val cols = groups.mapNotNull { g -> fieldOf(g.joinToString(" ") { it.text })?.let { Col(it, g.first().x0, g.last().x1) } }
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

        for (line in allLines) {
            val text = line.joinToString(" ") { it.text }
            val header = headerColumns(line)
            if (header != null) { cols = header; inTable = true; open = null; continue }
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
                val b = Building(date, StringBuilder(desc), values, ref, line.first().y, line.first().page)
                built += b; open = b
            } else {
                val o = open ?: continue
                val near = line.first().page != o.page || abs(line.first().y - o.lastY) < line.first().height * 6
                if (!near) continue
                if (o.values.keys.none { it != Field.BALANCE } && values.isNotEmpty()) o.values.putAll(values)
                if (desc.isNotBlank() && o.desc.length < 200) o.desc.append(' ').append(desc)
                if (o.ref.isEmpty() && ref.isNotEmpty()) o.ref = ref
            }
        }
        if (cols == null) return null

        // Turn into rows; decide direction; then cross-check with the running balance.
        data class R(val b: Building, val amount: Long, val credit: Boolean?, val balance: Long?)
        val rs = built.mapNotNull { b ->
            val debit = b.values[Field.DEBIT]?.takeIf { it.paise > 0 }
            val credit = b.values[Field.CREDIT]?.takeIf { it.paise > 0 }
            val amount = b.values[Field.AMOUNT]?.takeIf { it.paise > 0 }
            val bal = b.values[Field.BALANCE]?.let { if (it.mark == "dr" || it.negative) -it.paise else it.paise }
            when {
                debit != null && credit == null -> R(b, debit.paise, false, bal)
                credit != null && debit == null -> R(b, credit.paise, true, bal)
                amount != null -> R(b, amount.paise, when { amount.mark == "cr" -> true; amount.mark == "dr" || amount.negative -> false; else -> null }, bal)
                else -> null
            }
        }
        // Chronological or newest-first? Count which neighbour explains each balance change.
        var fwd = 0; var rev = 0
        for (i in 1 until rs.size) {
            val b0 = rs[i - 1].balance ?: continue; val b1 = rs[i].balance ?: continue
            if (abs(abs(b1 - b0) - rs[i].amount) <= 1) fwd++
            if (abs(abs(b1 - b0) - rs[i - 1].amount) <= 1) rev++
        }
        val forward = fwd >= rev
        val rows = rs.mapIndexed { i, r ->
            val older: Long? = if (forward) (if (i == 0) openingBalance else rs[i - 1].balance) else rs.getOrNull(i + 1)?.balance
            val byBalance = if (r.balance != null && older != null) when {
                abs(older + r.amount - r.balance) <= 1 -> true
                abs(older - r.amount - r.balance) <= 1 -> false
                else -> null
            } else null
            val desc = r.b.desc.toString().replace(Regex("""\s+"""), " ").trim()
            val credit = byBalance ?: r.credit ?: (creditCard && Regex("payment|refund|reversal|cashback", RegexOption.IGNORE_CASE).containsMatchIn(desc))
            val sure = (byBalance != null && (r.credit == null || r.credit == byBalance)) || (r.balance == null && r.credit != null)
            StatementParser.Row(r.b.date, desc.take(200), r.amount, credit, r.balance, sure, r.b.ref)
        }
        return StatementParser.Result(rows, creditCard)
    }

    private val MONEY = setOf(Field.DEBIT, Field.CREDIT, Field.AMOUNT, Field.BALANCE)

    /**
     * Which column a word belongs to. Each column "owns" the space up to the midpoint with its neighbours.
     * Numbers landing in money territory snap to the money column whose right edge they align with,
     * because bank statements right-align amounts under their headings.
     */
    private fun colOf(w: Word, cols: List<Col>): Field {
        val sorted = cols.sortedBy { it.cx }
        var owner = sorted.last()
        for ((i, c) in sorted.withIndex()) {
            val right = if (i == sorted.lastIndex) Float.POSITIVE_INFINITY else (c.cx + sorted[i + 1].cx) / 2
            if (w.cx <= right) { owner = c; break }
        }
        if (owner.field !in MONEY) return owner.field
        val isNum = w.text.any(Char::isDigit) && num(w.text) != null
        return when {
            isNum -> cols.filter { it.field in MONEY }.minBy { minOf(abs(w.x1 - it.x1), abs(w.cx - it.cx)) }.field
            w.text.length > 3 -> if (cols.any { it.field == Field.DESC }) Field.DESC else owner.field // long text drifting right
            else -> owner.field // "Cr" / "Dr" marks stay with their amount
        }
    }
}
