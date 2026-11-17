package com.kharchacheck.app.domain.export

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.core.model.Worth
import com.kharchacheck.app.domain.CsvWriter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** What to export. Null dates mean "from the beginning" / "until today". */
data class ExportSpec(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val types: Set<TxType> = TxType.entries.toSet(),
    val includeSummary: Boolean = true,
)

/**
 * Builds export files from the user's entries. Every entry keeps all its details, so the file is useful on
 * its own (taxes, sharing with a CA, moving to another app, or keeping a copy before uninstalling).
 */
object Exporter {
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun select(all: List<Txn>, spec: ExportSpec): List<Txn> = all.filter { t ->
        t.type in spec.types && (spec.from == null || !t.date.isBefore(spec.from)) && (spec.to == null || !t.date.isAfter(spec.to))
    }.sortedBy { it.epochMillis }

    val HEADER = listOf("Date", "Time", "Type", "Category", "Bucket", "Payee / merchant", "Debit (₹)", "Credit (₹)", "Amount (₹)",
        "Payment mode", "Note", "Tags", "Worth it?", "Source", "Bank reference")

    private fun fields(t: Txn, cats: Map<Long, Category>): List<Any> {
        val c = cats[t.categoryId]
        val out = t.type != TxType.INCOME
        return listOf(t.date, Instant.ofEpochMilli(t.epochMillis).atZone(t.zone).format(time), t.type.label, c?.name.orEmpty(), c?.bucket?.label.orEmpty(),
            t.payee, if (out) t.amountPaise else 0L, if (!out) t.amountPaise else 0L, t.amountPaise, t.mode.label, t.note, t.tags,
            if (t.worth == Worth.NONE) "" else t.worth.label, t.source.label, t.refNo)
    }

    /** UTF-8 with BOM so Excel shows ₹ and Hindi text correctly when the CSV is double-clicked. */
    fun csv(all: List<Txn>, cats: Map<Long, Category>, spec: ExportSpec): ByteArray {
        val sb = StringBuilder("\uFEFF")
        sb.append(HEADER.joinToString(",") { CsvWriter.cell(it) }).append("\r\n")
        select(all, spec).forEach { t ->
            sb.append(fields(t, cats).mapIndexed { i, v ->
                when {
                    v is LocalDate -> v.toString()
                    i in 6..8 -> (v as Long).let { p -> if (p == 0L && i != 8) "" else "%d.%02d".format(p / 100, p % 100) }
                    else -> CsvWriter.cell(v.toString())
                }
            }.joinToString(",")).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun xlsx(all: List<Txn>, cats: Map<Long, Category>, spec: ExportSpec): ByteArray {
        val list = select(all, spec)
        val entries = XlsxWriter.Sheet("Entries", HEADER, list.map { t ->
            fields(t, cats).mapIndexed { i, v ->
                when {
                    v is LocalDate -> XlsxWriter.Day(v)
                    i in 6..8 -> (v as Long).let { if (it == 0L && i != 8) XlsxWriter.Blank else XlsxWriter.Money(it) }
                    else -> XlsxWriter.Text(v.toString())
                }
            }
        }, widths = listOf(13, 7, 13, 26, 13, 26, 13, 13, 13, 11, 32, 14, 10, 18, 20))
        if (!spec.includeSummary) return XlsxWriter.write(listOf(entries))

        fun bucket(t: Txn) = cats[t.categoryId]?.bucket ?: Bucket.WANT
        val months = list.groupBy { it.month }.toSortedMap()
        val monthly = XlsxWriter.Sheet("Monthly summary",
            listOf("Month", "Income (₹)", "Spent (₹)", "Needs (₹)", "Wants (₹)", "Leaks (₹)", "Invested (₹)", "Saved (₹)", "Savings rate %", "Entries"),
            months.map { (m, l) ->
                val inc = l.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
                val sp = l.filter { it.type == TxType.EXPENSE && bucket(it).isSpend }
                val spend = sp.sumOf { it.amountPaise }
                fun b(x: Bucket) = sp.filter { bucket(it) == x }.sumOf { it.amountPaise }
                listOf(XlsxWriter.Text(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))), XlsxWriter.Money(inc), XlsxWriter.Money(spend),
                    XlsxWriter.Money(b(Bucket.NEED)), XlsxWriter.Money(b(Bucket.WANT)), XlsxWriter.Money(b(Bucket.LEAK)),
                    XlsxWriter.Money(l.filter { it.type == TxType.INVEST }.sumOf { it.amountPaise }), XlsxWriter.Money(inc - spend),
                    if (inc > 0) XlsxWriter.Num(((inc - spend) * 1000 / inc) / 10.0) else XlsxWriter.Blank, XlsxWriter.Num(l.size.toDouble()))
            }, widths = listOf(12, 13, 13, 13, 13, 13, 13, 13, 14, 9))
        val byCat = XlsxWriter.Sheet("By category", listOf("Category", "Bucket", "Type", "Total (₹)", "Entries", "Average (₹)"),
            list.groupBy { it.categoryId }.map { (id, l) -> Triple(cats[id], l, l.sumOf { it.amountPaise }) }.sortedByDescending { it.third }.map { (c, l, sum) ->
                listOf(XlsxWriter.Text(c?.name ?: "?"), XlsxWriter.Text(c?.bucket?.label.orEmpty()), XlsxWriter.Text(l.first().type.label),
                    XlsxWriter.Money(sum), XlsxWriter.Num(l.size.toDouble()), XlsxWriter.Money(sum / l.size))
            }, widths = listOf(30, 14, 14, 14, 9, 13))
        return XlsxWriter.write(listOf(entries, monthly, byCat))
    }

    fun fileName(spec: ExportSpec, ext: String): String {
        val range = when {
            spec.from == null && spec.to == null -> "all"
            spec.from != null && spec.to != null && YearMonth.from(spec.from) == YearMonth.from(spec.to) -> YearMonth.from(spec.from).toString()
            else -> "${spec.from ?: "start"}_to_${spec.to ?: LocalDate.now()}"
        }
        return "KharchaCheck-$range.$ext"
    }
}
