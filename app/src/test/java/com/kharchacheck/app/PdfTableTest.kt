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
class Page(private val page: Int = 0) {
    val glyphs = mutableListOf<Glyph>()
    private var y = 50f
    private val cw = 5f

    fun left(text: String, x: Float, yy: Float = y) { text.forEachIndexed { i, c -> glyphs += Glyph(c.toString(), x + i * cw, cw, yy, 10f, page) } }
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
