package com.kharchacheck.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates a clean, printable 1-page PDF summary of the Month-End Review using Android's built-in `PdfDocument`
 * (Spec Page 8 & 9: "The review stays on the phone. The user can save a PDF to a folder they choose.").
 */
object PdfReportExporter {

    fun writeMonthReviewPdf(
        outputStream: OutputStream,
        month: YearMonth,
        spentPaise: Long,
        incomePaise: Long,
        savedPaise: Long,
        avoidablePaise: Long,
        smallLeaksCount: Int,
        smallLeaksPaise: Long,
        categoryRows: List<Triple<String, Long, Boolean>>, // (Name, Paise, isFlagged)
        topMerchants: List<Pair<String, Long>>,
        selectedTargetNames: List<String>,
        intendedSavingPaise: Long,
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 72 DPI
        val page = doc.startPage(pageInfo)
        val c: Canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
            color = 0xFF0D1B2A.toInt()
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 11f
            color = 0xFF475569.toInt()
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = 0xFF0F766E.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 11f
            color = 0xFF1E293B.toInt()
        }
        val boldBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = 0xFF0D1B2A.toInt()
        }

        val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        var y = 48f

        c.drawText("Kharcha Check — Month-End Review ($monthLabel)", 40f, y, titlePaint)
        y += 18f
        c.drawText("Paisa kahan gaya — ab pata chalega · 100% On-Device Private Summary (Zero Cloud)", 40f, y, subPaint)
        y += 24f

        // 1. Headline Box
        c.drawText("1. Headline Summary", 40f, y, sectionPaint)
        y += 18f
        val savingsRate = if (incomePaise > 0) ((savedPaise * 100) / incomePaise).coerceIn(-100, 100) else 0L
        c.drawText("Total Income: ${Money.format(incomePaise)}   |   Total Spent: ${Money.format(spentPaise)}   |   Net Saved: ${Money.format(savedPaise)} ($savingsRate%)", 40f, y, boldBody)
        y += 16f
        c.drawText("Avoidable Spend Spotted: ${Money.format(avoidablePaise)}   |   Small Leaks (< ₹200): $smallLeaksCount payments (${Money.format(smallLeaksPaise)})", 40f, y, bodyPaint)
        y += 26f

        // 2. Where It Went & Overspend Flags
        c.drawText("2. Category Breakdown & Overspend Flags", 40f, y, sectionPaint)
        y += 18f
        categoryRows.take(10).forEach { (catName, amtPaise, flagged) ->
            val flagText = if (flagged) "  [OVERSPEND FLAG]" else ""
            c.drawText("• $catName: ${Money.format(amtPaise)}$flagText", 48f, y, if (flagged) boldBody else bodyPaint)
            y += 15f
        }
        y += 12f

        // 3. Top Merchants
        c.drawText("3. Top Merchants This Month", 40f, y, sectionPaint)
        y += 18f
        topMerchants.take(6).forEachIndexed { idx, (mName, amtPaise) ->
            c.drawText("${idx + 1}. $mName — ${Money.format(amtPaise)}", 48f, y, bodyPaint)
            y += 15f
        }
        y += 12f

        // 4. Next Month Targets & Savings Intent
        c.drawText("4. One Change for Next Month", 40f, y, sectionPaint)
        y += 18f
        if (selectedTargetNames.isEmpty()) {
            c.drawText("• No specific category budget target locked yet.", 48f, y, bodyPaint)
            y += 16f
        } else {
            selectedTargetNames.forEach { t ->
                c.drawText("• Target Focus: $t", 48f, y, boldBody)
                y += 16f
            }
        }
        if (intendedSavingPaise > 0) {
            c.drawText("Intended Avoidable Spend to Move to Savings: ${Money.format(intendedSavingPaise)}", 48f, y, boldBody)
        }

        c.drawText("Generated locally on your phone by Kharcha Check (No INTERNET permission).", 40f, 805f, subPaint)

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
