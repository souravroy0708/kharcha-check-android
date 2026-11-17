package com.kharchacheck.app

import com.kharchacheck.app.domain.read.Glyph
import com.kharchacheck.app.domain.read.PdfTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Builds synthetic PDF pages the way PdfBox reports them: one glyph per character with x/width/y.
 * Cells are placed left-aligned (start x) or right-aligned (end x), like real statements.
 */
class Page(private val page: Int = 0, private val zeroWidths: Boolean = false) {
    val glyphs = mutableListOf<Glyph>()
    private var y = 50f
    private val cw = 5f
    private var wordNo = page * 100_000

    /** Like PdfBox: each space-separated token is one word (own word id); widths may be reported as 0. */
    fun left(text: String, x: Float, yy: Float = y) {
        var id = ++wordNo
        text.forEachIndexed { i, c ->
            if (c == ' ') { id = ++wordNo; return@forEachIndexed }
            glyphs += Glyph(c.toString(), x + i * cw, if (zeroWidths) 0f else cw, yy, 10f, page, id)
        }
    }
    /** A separate PDF word written right after the previous one (e.g. ".56" after "1,234"). */
    fun fragment(text: String, x: Float, yy: Float = y) = left(text, x, yy)
    fun right(text: String, xEnd: Float, yy: Float = y) = left(text, xEnd - text.length * cw, yy)
    fun row(block: Page.() -> Unit) { block(); y += 14f }
}

class PdfTableTest {
    private val nov = YearMonth.of(2026, 11)

    // HDFC-like columns: Date | Narration | Chq/Ref | Value Dt | Withdrawal | Deposit | Closing Balance
    private fun hdfcHeader(p: Page) = p.row {
        left("Date", 20f); left("Narration", 80f); left("Chq./Ref.No.", 290f); left("Value Dt", 370f)
        right("Withdrawal Amt.", 520f); right("Deposit Amt.", 610f); right("Closing Balance", 710f)
    }

    @Test fun hdfcColumnsWrappedNarrationAndGluedNumbers() {
        val p = Page()
        p.row { left("Statement of accounts for SOURAV", 20f) }
        hdfcHeader(p)
        p.row { left("Opening Balance", 80f); right("25,000.00", 710f) }
        p.row { left("01/11/26", 20f); left("UPI-SWIGGY-SWIGGY@ICICI", 80f); left("0000630518920112", 284f); left("01/11/26", 370f); right("340.00", 520f); right("24,660.00", 710f) }
        p.row { left("-ICIC0000103-PAYMENT", 80f) }  // wrapped narration
        p.row { left("02/11/26", 20f); left("NEFT CR-ACME TECH PVT LTD-SALARY", 80f); left("N123456", 290f); left("02/11/26", 370f); right("52,000.00", 610f); right("76,660.00", 710f) }
        // Deposit and balance glued: no space glyph between them, only a small visual gap
        p.row { left("03/11/26", 20f); left("ATM WDL MG ROAD", 80f); left("03/11/26", 370f); right("2,000.00", 520f); left("74,660.00", 662f) }
        p.row { left("Closing Balance", 80f); right("74,660.00", 710f) }
        val res = PdfTable.parse(p.glyphs, nov)
        assertNotNull(res)
        val rows = res!!.rows
        assertEquals(3, rows.size)
        assertEquals(34000L, rows[0].amountPaise); assertFalse(rows[0].credit); assertTrue(rows[0].sure)
        assertTrue(rows[0].description.contains("SWIGGY") && rows[0].description.contains("PAYMENT"))
        assertEquals("0000630518920112", rows[0].ref)
        assertEquals(5200000L, rows[1].amountPaise); assertTrue(rows[1].credit); assertTrue(rows[1].sure)
        assertEquals(200000L, rows[2].amountPaise); assertFalse(rows[2].credit)
        assertEquals(7466000L, rows[2].balancePaise)
        assertEquals(LocalDate.of(2026, 11, 3), rows[2].date)
    }

    @Test fun sbiStyleSplitDatesNewestFirstAcrossPages() {
        val pages = listOf(Page(0), Page(1))
        fun header(p: Page) = p.row {
            left("Txn Date", 20f); left("Value Date", 90f); left("Description", 160f); left("Ref No./Cheque No.", 360f)
            right("Debit", 540f); right("Credit", 620f); right("Balance", 720f)
        }
        header(pages[0])
        pages[0].row { left("8 Nov 2026", 20f); left("8 Nov 2026", 90f); left("BY TRANSFER-UPI/CR/630700001111/RAHUL", 160f); right("500.00", 620f); right("10,500.00", 720f) }
        pages[0].row { left("7 Nov 2026", 20f); left("7 Nov 2026", 90f); left("TO TRANSFER-UPI/DR/630611110000/BLINKIT", 160f); right("1,200.00", 540f); right("10,000.00", 720f) }
        pages[0].row { left("Page 1 of 2", 300f) }
        header(pages[1])
        pages[1].row { left("6 Nov 2026", 20f); left("6 Nov 2026", 90f); left("TO TRANSFER-UPI/DR/630500002222/NETFLIX", 160f); right("199.00", 540f); right("11,200.00", 720f) }
        pages[1].row { left("5 Nov 2026", 20f); left("5 Nov 2026", 90f); left("BY TRANSFER-NEFT INTEREST", 160f); right("99.00", 620f); right("11,399.00", 720f) }
        val rows = PdfTable.parse(pages.flatMap { it.glyphs }, nov)!!.rows
        assertEquals(4, rows.size)
        assertEquals(listOf(true, false, false, true), rows.map { it.credit })
        // Oldest row can't be balance-checked, but its amount came cleanly from the Credit column.
        assertEquals(listOf(50000L, 120000L, 19900L, 9900L), rows.map { it.amountPaise })
        assertEquals(LocalDate.of(2026, 11, 8), rows[0].date)
        assertTrue(rows.take(3).all { it.sure })
    }

    @Test fun creditCardAmountColumnWithCrMarks() {
        val p = Page()
        p.row { left("Date", 20f); left("Transaction Details", 100f); right("Amount (in Rs.)", 600f) }
        p.row { left("02/11/2026", 20f); left("SWIGGY BANGALORE", 100f); right("456.00", 600f) }
        p.row { left("10/11/2026", 20f); left("PAYMENT RECEIVED - THANK YOU", 100f); right("18,450.00", 590f); left("Cr", 595f) }
        p.row { left("12/11/2026", 20f); left("AMAZON PAY INDIA", 100f); right("1,299.00 Dr", 600f) }
        val rows = PdfTable.parse(p.glyphs, nov, creditCard = true)!!.rows
        assertEquals(3, rows.size)
        assertEquals(listOf(false, true, false), rows.map { it.credit })
        assertEquals(listOf(45600L, 1845000L, 129900L), rows.map { it.amountPaise })
    }

    @Test fun noHeaderMeansFallBack() {
        val p = Page(); p.row { left("Hello world 123.45", 20f) }
        assertNull(PdfTable.parse(p.glyphs, nov))
    }
}


class PdfAccuracyTest {
    private val nov = YearMonth.of(2026, 11)

    /** HDFC-style header where each money heading wraps onto two lines and "Date" sits between them. */
    private fun twoLineHeader(p: Page) {
        p.row { right("Withdrawal", 520f); right("Deposit", 610f); right("Closing", 710f) }
        p.row { left("Date", 20f); left("Narration", 80f); left("Value Dt", 370f); right("Amt.", 520f); right("Amt.", 610f); right("Balance", 710f) }
    }

    @Test fun twoLineHeaderAndZeroWidthFonts() {
        val p = Page(zeroWidths = true)
        twoLineHeader(p)
        p.row { left("Opening Balance", 80f); right("10,000.00", 710f) }
        p.row { left("01/11/26", 20f); left("UPI-ZOMATO-PAYMENT", 80f); left("01/11/26", 370f); right("250.00", 520f); right("9,750.00", 710f) }
        p.row { left("02/11/26", 20f); left("NEFT CR SALARY", 80f); left("02/11/26", 370f); right("40,000.00", 610f); right("49,750.00", 710f) }
        p.row { left("03/11/26", 20f); left("UPI-BLINKIT", 80f); left("03/11/26", 370f); right("1,234.56", 520f); right("48,515.44", 710f) }
        val rows = PdfTable.parse(p.glyphs, nov)!!.rows
        assertEquals(listOf(25000L, 4000000L, 123456L), rows.map { it.amountPaise })
        assertEquals(listOf(false, true, false), rows.map { it.credit })
        assertTrue(rows.all { it.sure })
    }

    @Test fun decimalWrittenAsSeparateWord() {
        val p = Page()
        twoLineHeader(p)
        p.row { left("Opening Balance", 80f); right("5,000.00", 710f) }
        // "1,234" and ".56" arrive as two PDF words in the withdrawal column
        p.row { left("04/11/26", 20f); left("CARD POS AMAZON", 80f); left("04/11/26", 370f); left("1,234", 485f); fragment(".56", 510f); right("3,765.44", 710f) }
        p.row { left("05/11/26", 20f); left("UPI-CHAI", 80f); left("05/11/26", 370f); right("40.00", 520f); right("3,725.44", 710f) }
        p.row { left("06/11/26", 20f); left("UPI-AUTO", 80f); left("06/11/26", 370f); right("60.00", 520f); right("3,665.44", 710f) }
        val rows = PdfTable.parse(p.glyphs, nov)!!.rows
        assertEquals(123456L, rows[0].amountPaise)
        assertTrue(rows[0].sure)
    }

    @Test fun missingAmountIsTakenFromTheBalanceChange() {
        val p = Page()
        twoLineHeader(p)
        p.row { left("Opening Balance", 80f); right("20,000.00", 710f) }
        p.row { left("01/11/26", 20f); left("UPI-SWIGGY", 80f); left("01/11/26", 370f); right("300.00", 520f); right("19,700.00", 710f) }
        p.row { left("02/11/26", 20f); left("UPI-RENT", 80f); left("02/11/26", 370f); right("19,700.00", 710f) /* amount cell lost */ }
        p.row { left("03/11/26", 20f); left("UPI-METRO", 80f); left("03/11/26", 370f); right("50.00", 520f); right("19,650.00", 710f) }
        p.row { left("04/11/26", 20f); left("NEFT CR REFUND", 80f); left("04/11/26", 370f); right("350.00", 610f); right("20,000.00", 710f) }
        p.row { left("05/11/26", 20f); left("UPI-CAB", 80f); left("05/11/26", 370f); right("200.00", 520f); right("19,800.00", 710f) }
        val rows = PdfTable.parse(p.glyphs, nov)!!.rows
        // The rent row's balance didn't change → no movement → amount 0 (not the balance!)
        assertEquals(listOf(30000L, 0L, 5000L, 35000L, 20000L), rows.map { it.amountPaise })
        assertFalse(rows[1].sure)
    }

    @Test fun balanceNeverBecomesTheAmount() {
        // Columns shifted: every "amount" is really the balance. The chain can't verify anything → all 0, not wrong.
        val p = Page()
        twoLineHeader(p)
        p.row { left("01/11/26", 20f); left("UPI-A", 80f); left("01/11/26", 370f); right("9,750.00", 520f) }
        p.row { left("02/11/26", 20f); left("UPI-B", 80f); left("02/11/26", 370f); right("9,500.00", 520f) }
        p.row { left("03/11/26", 20f); left("UPI-C", 80f); left("03/11/26", 370f); right("9,100.00", 520f) }
        val rows = PdfTable.parse(p.glyphs, nov)!!.rows
        // No balance column at all here, so values from the Withdrawal column are kept but flagged unsure
        assertTrue(rows.none { it.sure && it.amountPaise > 0 && it.credit })
        assertTrue(rows.all { !it.sure })
    }

    @Test fun textFallbackNeverUsesALoneNumber() {
        val lines = listOf(
            "Opening Balance 1,000.00",
            "01/11/26 UPI-ZOMATO 250.00 750.00",
            "02/11/26 UPI-RENT 650.00",            // only the balance survived on this line
            "03/11/26 UPI-METRO 50.00 600.00",
            "04/11/26 UPI-CHAI 20.00 580.00",
        )
        val rows = com.kharchacheck.app.domain.read.StatementParser.fromLines(lines, nov).rows
        assertEquals(listOf(25000L, 10000L, 5000L, 2000L), rows.map { it.amountPaise })  // 650 is the balance; the amount (100) comes from 750 → 650
        assertTrue(rows.all { it.sure })
    }
}

class DiagnosticsTest {
    @Test fun maskHidesNamesAndIdsButKeepsAmountsAndLayout() {
        assertEquals("UPI/999999999999/xxxxxx", PdfTable.mask("UPI/630518920112/SWIGGY"))
        assertEquals("24,660.00", PdfTable.mask("24,660.00"))
        assertEquals("Withdrawal", PdfTable.mask("Withdrawal"))
        assertEquals("01/11/26", PdfTable.mask("01/11/26"))
        assertEquals("xxxxxx", PdfTable.mask("SOURAV"))
        val p = Page()
        p.row { left("Date", 20f); left("Narration", 80f); right("Withdrawal Amt.", 520f); right("Closing Balance", 710f) }
        p.row { left("01/11/26", 20f); left("UPI-SOURAV-ROY", 80f); right("340.00", 520f); right("24,660.00", 710f) }
        val report = PdfTable.diagnose(p.glyphs, YearMonth.of(2026, 11), false)
        assertTrue(report.contains("340.00") && report.contains("Withdrawal"))
        assertFalse(report.contains("SOURAV") || report.contains("ROY"))
    }
}
