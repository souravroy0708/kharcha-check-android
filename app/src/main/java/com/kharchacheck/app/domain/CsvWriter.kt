package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Txn
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Spreadsheet-friendly export. Amounts in rupees with 2 decimals; formula-injection safe. */
object CsvWriter {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun write(txns: List<Txn>, categories: Map<Long, Category>): String = buildString {
        append("Date,Time,Type,Amount (INR),Category,Bucket,Payee,Payment mode,Note,Tags,Worth,Source\n")
        txns.sortedBy { it.epochMillis }.forEach { t ->
            val zdt = Instant.ofEpochMilli(t.epochMillis).atZone(t.zone)
            val cat = categories[t.categoryId]
            val cells = listOf(
                zdt.format(dateFmt), zdt.format(timeFmt), t.type.label,
                "%d.%02d".format(t.amountPaise / 100, t.amountPaise % 100),
                cat?.name.orEmpty(), cat?.bucket?.label.orEmpty(), t.payee, t.mode.label, t.note, t.tags,
                if (t.worth.name == "NONE") "" else t.worth.label, t.source.label,
            )
            append(cells.joinToString(",") { cell(it) }).append('\n')
        }
    }

    fun cell(raw: String): String {
        // A leading = + - @ makes Excel/Sheets run it as a formula. Neutralise it.
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
