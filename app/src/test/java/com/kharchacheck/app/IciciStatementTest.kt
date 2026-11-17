package com.kharchacheck.app

import com.kharchacheck.app.domain.read.Classifier
import com.kharchacheck.app.domain.read.Glyph
import com.kharchacheck.app.domain.read.PdfTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Faithful replica of an ICICI "OpTransactionHistory" PDF page (positions taken from a real statement):
 *  - header cells on two lines, with S No. / Cheque Number / Transaction Remarks centred between them
 *  - the payee name (bold) sits HALF A LINE ABOVE the row's date/amount line; UPI details continue below
 *  - dd.mm.yyyy dates, amounts without thousands separators, serial-number column on the left
 */
class IciciStatementTest {
    private val g = mutableListOf<Glyph>()
    private var word = 0
    private val cw = 6.2f   // character advance in this PDF's units
    private val h = 9f      // text height as PDF libraries report it
    private fun left(t: String, x: Float, y: Float) { var id = ++word; t.forEachIndexed { i, c -> if (c == ' ') id = ++word else g += Glyph(c.toString(), x + i * cw, cw, y, h, 0, id) } }
    private fun right(t: String, x1: Float, y: Float) = left(t, x1 - t.length * cw, y)

    private data class R(val n: Int, val date: String, val name: String, val lines: List<String>, val wd: String?, val dep: String?, val bal: String)

    private val rows = listOf(
        R(1, "01.01.2026", "Mrs SHAIK", listOf("UPI/Mrs SHAIK/q880058940@ybl/UPI/YES BANK", "L/600142797840/ICI744020e9724b44adb3976ec27", "71eca38/"), "100.00", null, "168125.25"),
        R(2, "01.01.2026", "BHUKYA DEV", listOf("UPI/BHUKYA DEV/q859926175@ybl/UPI/YES BANK", "L/636764614496/ICI9dd9e6ea22414149829b7d47", "88985377/"), "130.00", null, "167995.25"),
        R(3, "01.01.2026", "B2 RAGHAVE", listOf("UPI/B2 RAGHAVE/q965725772@ybl/UPI/YES BANK", "L/600128794103/ICI7bc95a7ac57a4a26865798029", "0d4c10f/"), "552.00", null, "167443.25"),
        R(4, "01.01.2026", "Karachi Ba", listOf("UPI/Karachi Ba/paytmqr1l3752m/UPI/YES BANK", "L/636720416405/ICI571d82a493b042ed888dba4e", "89ab8b80/"), "795.00", null, "166648.25"),
        R(5, "02.01.2026", "Self", listOf("BIL/INFT/001136965005/CC BillPay-5007/Self"), "102526.25", null, "64122.00"),
        R(6, "02.01.2026", "Vasantha B", listOf("UPI/Vasantha B/437405000679@l/Sourav -50/ICICI", "Bank/600261846645/APLAP193d9458134961a84e", "58bed5d3a3f9"), "2000.00", null, "62122.00"),
        R(7, "02.01.2026", "DUMMY NAME", listOf("UPI/DUMMY NAME/50100055184380/Rent/HDFC", "BANK/636848385555/ICI5508e8dc65dd49fbbb43d", "241df0d26ca/"), "21000.00", null, "41122.00"),
        R(8, "02.01.2026", "Tobox Vent", listOf("UPI/Tobox Vent/paytm-14464838/Sent using/YES", "BANK", "L/297757874547/PTM463dfeaf767146069a21eadf", "e4a95d89"), "99.00", null, "41023.00"),
        R(9, "03.01.2026", "NAUKRI COM", listOf("UPI/NAUKRI COM/naukari.bdsi@h/UPI", "Mandat/HDFC", "BANK/102377615859/HDF4773D7B71AA8195BE0", "63EBEEE20AD769/"), "700.00", null, "40323.00"),
        R(10, "03.01.2026", "REFUND CO", listOf("UPI/REFUND CO/refund@ybl/UPI/YES BANK"), null, "500.00", "40823.00"),
    )

    private fun page() {
        // Header: top line, centred line, bottom line
        right("Transaction", 195f, 343f); right("Withdrawal", 811f, 343f); right("Deposit", 915f, 343f); right("Balance", 1023f, 343f)
        left("S No.", 43f, 352f); left("Cheque Number", 222f, 352f); left("Transaction Remarks", 448f, 352f)
        left("Date", 135f, 361f); right("Amount (INR)", 817f, 361f); right("Amount (INR)", 936f, 361f); right("(INR)", 1015f, 361f)
        var y = 388f
        for (r in rows) {
            left(r.name, 348f, y)                                    // bold payee name, half a line above
            val dy = y + 9f
            left("${r.n}", 55f, dy); left(r.date, 111f, dy)          // date / amount line
            r.wd?.let { right(it, 822f, dy) }; r.dep?.let { right(it, 940f, dy) }; right(r.bal, 1036f, dy)
            r.lines.forEachIndexed { i, l -> left(l, 348f, dy + 9f + i * 18f) }
            y = dy + 9f + r.lines.size * 18f + 1f
        }
        left("www.icici.bank.in   Dial your Bank 1800-1080", 390f, y + 60f)
    }

    @Test fun everyAmountDirectionDateAndPayeeMatchesThePrintedStatement() {
        page()
        val res = PdfTable.parse(g, YearMonth.of(2026, 1))
        assertTrue("table header must be recognised", res != null)
        val out = res!!.rows
        assertEquals(rows.size, out.size)
        rows.zip(out).forEach { (want, got) ->
            val amt = (want.wd ?: want.dep)!!.replace(".", "").toLong()
            assertEquals("row ${want.n} amount", amt, got.amountPaise)
            assertEquals("row ${want.n} direction", want.dep != null, got.credit)
            assertEquals("row ${want.n} date", LocalDate.parse(want.date.split('.').reversed().joinToString("-")), got.date)
            assertTrue("row ${want.n} confirmed", got.sure)
            assertTrue("row ${want.n} description starts with its own name: ${got.description}", got.description.startsWith(want.name))
        }
        // Payees come from each row's own line, not the previous row's
        assertEquals("Bhukya Dev", Classifier.classify(out[1].description, false).payee)
        assertEquals("Tobox Vent", Classifier.classify(out[7].description, false).payee)
        // "BIL/INFT/…/CC BillPay-5007/Self" is a credit-card bill payment: a transfer, not ₹1,02,526 of spending
        assertEquals(com.kharchacheck.app.core.model.TxType.TRANSFER, Classifier.classify(out[4].description, false).type)
        assertEquals("cc_bill", Classifier.classify(out[4].description, false).categoryKey)
        // The deposit row is income
        assertEquals(com.kharchacheck.app.core.model.TxType.INCOME, Classifier.classify(out[9].description, true).type)
    }
}
