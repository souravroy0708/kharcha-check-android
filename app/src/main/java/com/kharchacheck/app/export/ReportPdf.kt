package com.kharchacheck.app.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.MonthReport
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One-page A4 monthly report, drawn natively. No third-party PDF library, nothing leaves the phone. */
object ReportPdf {
    private const val W = 595
    private const val H = 842

    fun write(out: OutputStream, r: MonthReport, name: String) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = page.canvas
        val ink = Color.rgb(15, 18, 53); val haldi = Color.rgb(255, 194, 26); val muted = Color.rgb(90, 96, 120)
        val coral = Color.rgb(255, 107, 74); val mint = Color.rgb(43, 182, 115); val violet = Color.rgb(91, 108, 255); val amber = Color.rgb(255, 176, 32)
        fun p(size: Float, color: Int = ink, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        // Header band
        c.drawRect(0f, 0f, W.toFloat(), 120f, Paint().apply { color = ink })
        c.drawText("Kharcha Check", 40f, 48f, p(14f, haldi, true))
        val title = r.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " report"
        c.drawText(title, 40f, 82f, p(26f, Color.WHITE, true))
        if (name.isNotBlank()) c.drawText("for $name", 40f, 104f, p(12f, Color.rgb(188, 193, 224)))

        var y = 160f
        val cols = listOf("Income" to r.incomePaise, "Spent" to r.spendPaise, "Invested" to r.investPaise, "Saved" to r.savedPaise)
        cols.forEachIndexed { i, (label, v) ->
            val x = 40f + i * 130f
            c.drawText(label, x, y, p(11f, muted))
            c.drawText(Money.format(v), x, y + 24f, p(17f, ink, true))
        }
        y += 52f
        c.drawText("Savings rate ${r.savingsRatePct}%   ·   Leaks ${Money.format(r.leaksPaise)}   ·   ${r.txnCount} entries   ·   ${r.noSpendDays} no-spend days", 40f, y, p(11f, muted))

        // 50/30/20 bar
        y += 36f
        c.drawText("Needs / Wants / Savings  (ideal 50 / 30 / 20)", 40f, y, p(13f, ink, true))
        y += 14f
        val barW = W - 80f
        var x = 40f
        listOf(r.split.needsPct to violet, r.split.wantsPct to amber, r.split.savePct to mint).forEach { (pct, col) ->
            val w = barW * (pct.coerceIn(0, 100) / 100f)
            c.drawRect(x, y, x + w, y + 16f, Paint().apply { color = col }); x += w
        }
        y += 32f
        c.drawText("${r.split.needsPct}% needs   ${r.split.wantsPct}% wants + leaks   ${r.split.savePct}% saved", 40f, y, p(11f, muted))

        // Categories
        y += 34f
        c.drawText("Where it went", 40f, y, p(13f, ink, true))
        val maxCat = r.categories.maxOfOrNull { it.paise } ?: 1L
        r.categories.take(10).forEach { line ->
            y += 22f
            c.drawText(line.category.name.take(32), 40f, y, p(11f))
            val w = 200f * line.paise / maxCat
            val col = when (line.category.bucket.name) { "LEAK" -> coral; "NEED" -> violet; else -> amber }
            c.drawRect(260f, y - 9f, 260f + w, y + 1f, Paint().apply { color = col })
            c.drawText(Money.format(line.paise), 470f, y, p(11f, ink, true))
        }

        // Takeaways
        y += 36f
        c.drawText("Takeaways", 40f, y, p(13f, ink, true))
        r.insights.forEach { ins ->
            wrap(ins.text, 88).forEach { line -> y += 16f; c.drawText(line, 40f, y, p(11f)) }
            y += 4f
        }

        c.drawText("Made on your phone by Kharcha Check. Private: this file was never uploaded anywhere.", 40f, H - 30f, p(9f, muted))
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun wrap(s: String, n: Int): List<String> {
        val out = mutableListOf<String>(); var line = StringBuilder()
        s.split(' ').forEach { w -> if (line.length + w.length + 1 > n) { out += line.toString(); line = StringBuilder() }; if (line.isNotEmpty()) line.append(' '); line.append(w) }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }
}
