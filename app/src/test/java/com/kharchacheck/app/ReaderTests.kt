package com.kharchacheck.app

import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.read.Classifier
import com.kharchacheck.app.domain.read.ReceiptParser
import com.kharchacheck.app.domain.read.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun googlePaySuccess() {
        val ocr = """
            ₹340
            Paid to
            Swiggy
            swiggy@icici
            Completed
            8 Nov 2026, 9:41 pm
            UPI transaction ID
            630518920112
            To: SWIGGY
            From: SOURAV KUMAR (State Bank of India)
        """.trimIndent()
        val r = ReceiptParser.parse(ocr, today)
        assertEquals(34000L, r.amountPaise)
        assertTrue(r.amountSure)
        assertEquals(TxType.EXPENSE, r.type)
        assertEquals("Swiggy", r.payee)
        assertEquals("food_delivery", r.categoryKey)
        assertEquals(LocalDateTime.of(2026, 11, 8, 21, 41), r.dateTime)
        assertEquals("630518920112", r.refNo)
        assertEquals(ReceiptParser.Status.SUCCESS, r.status)
    }

    @Test fun phonePeWithTimeFirst() {
        val ocr = """
            Transaction Successful
            09:12 am on 07 Nov 2026
            Paid to
            Ramesh Tea Stall
            ₹ 65
            Transfer Details
            UTR: 630598877665
            Debited from XXXXXX4821
        """.trimIndent()
        val r = ReceiptParser.parse(ocr, today)
        assertEquals(6500L, r.amountPaise)
        assertEquals("Ramesh Tea Stall", r.payee)
        assertEquals("chai_coffee", r.categoryKey)
        assertEquals(LocalDateTime.of(2026, 11, 7, 9, 12), r.dateTime)
        assertEquals("630598877665", r.refNo)
    }

    @Test fun rupeeMisreadAsPercentAndCommas() {
        val r = ReceiptParser.parse("Paid Successfully to\nBlinkit\n%1,280.50\nWed, 05 Nov 2026, 10:02 PM\nUPI Ref No: 630611112222", today)
        assertEquals(128050L, r.amountPaise)
        assertFalse(r.amountSure)
        assertEquals("groceries", r.categoryKey)
    }

    @Test fun receivedMoneyIsIncome() {
        val r = ReceiptParser.parse("₹500 received\nReceived from\nRahul Sharma\nrahul.s@okaxis\n6 Nov 2026, 7:15 pm", today)
        assertEquals(TxType.INCOME, r.type)
        assertEquals(50000L, r.amountPaise)
        assertEquals("Rahul Sharma", r.payee)
    }

    @Test fun failedPaymentFlagged() {
        val r = ReceiptParser.parse("Payment failed\n₹300\nTo Zomato\n8 Nov 2026", today)
        assertEquals(ReceiptParser.Status.FAILED, r.status)
    }

    @Test fun balanceLineIsNotTheAmount() {
        val r = ReceiptParser.parse("Available balance ₹12,400\nPaid to\nNetflix\n₹199\n2 Nov 2026", today)
        assertEquals(19900L, r.amountPaise)
        assertEquals("ott", r.categoryKey)
    }
}

class StatementParserTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun hdfcStyleForwardWithWrappedNarration() {
        val lines = """
            Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance
            Opening Balance 25,000.00
            01/11/26 UPI-SWIGGY-SWIGGY@ICICI-ICIC0000103-630518920112-PAYMENT 01/11/26 340.00 24,660.00
            02/11/26 NEFT CR-HDFC0000001-ACME TECHNOLOGIES PVT LTD-SALARY NOV 02/11/26 52,000.00 76,660.00
            03/11/26 ATM WDL-ATM CASH 1234 MG ROAD 03/11/26 2,000.00 74,660.00
            04/11/26 UPI-ZEPTO-ZEPTO@YBL-
            YESB0000001-630611112222 04/11/26 456.50 74,203.50
            Closing Balance 74,203.50
            Generated on 08/11/2026 this is a computer generated statement
        """.trimIndent().lines()
        val res = StatementParser.fromLines(lines, nov)
        assertFalse(res.creditCard)
        assertEquals(4, res.rows.size)
        val (swiggy, salary, atm, zepto) = res.rows
        assertEquals(34000L, swiggy.amountPaise); assertFalse(swiggy.credit); assertTrue(swiggy.sure)
        assertEquals(5200000L, salary.amountPaise); assertTrue(salary.credit)
        assertEquals(200000L, atm.amountPaise); assertFalse(atm.credit)
        assertEquals(45650L, zepto.amountPaise); assertFalse(zepto.credit); assertTrue(zepto.description.contains("ZEPTO"))
        assertEquals(LocalDate.of(2026, 11, 4), zepto.date)

        assertEquals("food_delivery", Classifier.classify(swiggy.description, swiggy.credit).categoryKey)
        assertEquals("salary", Classifier.classify(salary.description, salary.credit).categoryKey)
        assertEquals(TxType.TRANSFER, Classifier.classify(atm.description, atm.credit).type)
        assertEquals("Swiggy", Classifier.classify(swiggy.description, false).payee)
    }

    @Test fun sbiStyleNewestFirstUsesBalanceDirection() {
        val lines = """
            08 Nov 2026 BY TRANSFER-UPI/CR/630700001111/RAHUL SHARMA/YESB/rahul@ybl 500.00 10,500.00
            07 Nov 2026 TO TRANSFER-UPI/DR/630611110000/BLINKIT/HDFC/blinkit@hdfc 1,200.00 10,000.00
            06 Nov 2026 TO TRANSFER-UPI/DR/630500002222/NETFLIX/ICIC/netflix@icici 199.00 11,200.00
            05 Nov 2026 BY TRANSFER-NEFT/INTEREST PAID 99.00 11,399.00
        """.trimIndent().lines()
        val rows = StatementParser.fromLines(lines, nov).rows
        assertEquals(listOf(true, false, false, true), rows.map { it.credit })
        assertTrue(rows.take(3).all { it.sure })
        assertEquals("Blinkit", Classifier.classify(rows[1].description, false).payee)
    }

    @Test fun creditCardStatement() {
        val lines = """
            Date Transaction Details Amount (in Rs.)
            02 Nov SWIGGY BANGALORE 456.00
            05 Nov AMAZON PAY INDIA PRIVATE 1,299.00
            10 Nov PAYMENT RECEIVED - THANK YOU 18,450.00 Cr
            12 Nov FINANCE CHARGES 312.40
            Total Amount Due 2,067.40
        """.trimIndent().lines()
        val res = StatementParser.fromLines(lines, nov, creditCard = true)
        assertEquals(4, res.rows.size)
        assertEquals(LocalDate.of(2026, 11, 2), res.rows[0].date)
        assertEquals(listOf(false, false, true, false), res.rows.map { it.credit })
        assertEquals("food_delivery", Classifier.classify(res.rows[0].description, false, true).categoryKey)
        assertEquals(TxType.TRANSFER, Classifier.classify(res.rows[2].description, true, true).type)
        assertEquals("cc_interest", Classifier.classify(res.rows[3].description, false, true).categoryKey)
    }

    @Test fun cardDatesWithoutYearCrossingNewYear() {
        val rows = StatementParser.fromLines(listOf("28 Dec ZOMATO 300.00", "03 Jan UBER INDIA 210.00"), YearMonth.of(2027, 1), creditCard = true).rows
        assertEquals(LocalDate.of(2026, 12, 28), rows[0].date)
        assertEquals(LocalDate.of(2027, 1, 3), rows[1].date)
    }

    @Test fun netbankingCsv() {
        val csv = """
            Statement of account
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/11/26,UPI-SWIGGY-SWIGGY@ICICI-630518920112,0000630518920112,01/11/26,340.00,,"24,660.00"
            02/11/26,"NEFT CR-ACME TECHNOLOGIES, PVT LTD-SALARY",N123,02/11/26,,"52,000.00","76,660.00"
            ,,,,,,
        """.trimIndent()
        val rows = StatementParser.fromCsv(csv, nov).rows
        assertEquals(2, rows.size)
        assertEquals(34000L, rows[0].amountPaise); assertFalse(rows[0].credit); assertTrue(rows[0].sure)
        assertEquals(5200000L, rows[1].amountPaise); assertTrue(rows[1].credit)
        assertTrue(rows[1].description.contains("ACME TECHNOLOGIES, PVT LTD"))
        assertEquals("630518920112", rows[0].ref)
    }

    @Test fun csvWithAmountAndTypeColumns() {
        val csv = "Txn Date\tDescription\tAmount\tDr/Cr\tBalance\n2026-11-03\tAMAZON PAY\t1,299.00\tDR\t9,000.00\n2026-11-04\tREFUND AMAZON\t299.00\tCR\t9,299.00"
        val rows = StatementParser.fromCsv(csv, nov).rows
        assertEquals(listOf(false, true), rows.map { it.credit })
        assertEquals(LocalDate.of(2026, 11, 3), rows[0].date)
    }

    @Test fun excelBinaryGivesHelpfulError() {
        var msg = ""
        try { StatementParser.fromCsv("PK\u0003\u0004\u0000\u0000binary", nov) } catch (e: StatementParser.NotReadableException) { msg = e.message.orEmpty() }
        assertTrue(msg.contains("CSV"))
    }
}

class SpreadsheetReaderTest {
    private val nov = YearMonth.of(2026, 11)

    private fun xlsx(sheet: String, shared: String): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types/>")
            put("xl/workbook.xml", """<workbook xmlns:r="r"><sheets><sheet name="Statement" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Type="ws" Target="worksheets/sheet1.xml"/></Relationships>""")
            put("xl/sharedStrings.xml", shared)
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return bos.toByteArray()
    }

    @Test fun xlsxWithSharedStringsSerialDatesAndGaps() {
        val shared = """<sst><si><t>Txn Date</t></si><si><t>Description</t></si><si><t>Debit</t></si><si><t>Credit</t></si><si><t>Balance</t></si>
            <si><t>UPI/DR/630518920112/SWIGGY/swiggy@icici</t></si><si><r><t>SALARY </t></r><r><t>NOV &amp; BONUS</t></r></si></sst>"""
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c></row>
            <row r="2"><c r="A2"><v>46327</v></c><c r="B2" t="s"><v>5</v></c><c r="C2"><v>340</v></c><c r="E2"><v>24660</v></c></row>
            <row r="3"><c r="A3" t="inlineStr"><is><t>02/11/2026</t></is></c><c r="B3" t="s"><v>6</v></c><c r="D3"><v>52000.5</v></c><c r="E3"><v>76660.5</v></c></row>
        </sheetData></worksheet>"""
        val rows = StatementParser.fromSpreadsheet(xlsx(sheet, shared), nov).rows
        assertEquals(2, rows.size)
        assertEquals(LocalDate.of(2026, 11, 1), rows[0].date)
        assertEquals(34000L, rows[0].amountPaise); assertFalse(rows[0].credit)
        assertEquals(5200050L, rows[1].amountPaise); assertTrue(rows[1].credit)
        assertEquals("SALARY NOV & BONUS", rows[1].description)
        assertEquals("Swiggy", Classifier.classify(rows[0].description, false).payee)
    }

    @Test fun htmlDisguisedAsXls() {
        val html = """<html><body><table>
            <tr><th>Txn Date</th><th>Value Date</th><th>Description</th><th>Ref No./Cheque No.</th><th>Debit</th><th>Credit</th><th>Balance</th></tr>
            <tr><td>3 Nov 2026</td><td>3 Nov 2026</td><td>TO TRANSFER-UPI/DR/630611110000/BLINKIT<br/>/HDFC/blinkit@hdfc</td><td>630611110000</td><td>1,200.00</td><td>&nbsp;</td><td>10,000.00</td></tr>
            <tr><td>4 Nov 2026</td><td>4 Nov 2026</td><td>BY TRANSFER-NEFT*ACME</td><td>N1</td><td></td><td>5,000.00</td><td>15,000.00</td></tr>
        </table></body></html>"""
        val rows = StatementParser.fromSpreadsheet(html.toByteArray(), nov).rows
        assertEquals(2, rows.size)
        assertFalse(rows[0].credit); assertEquals(120000L, rows[0].amountPaise)
        assertTrue(rows[1].credit)
        assertEquals("630611110000", rows[0].ref)
    }

    @Test fun oldBinaryXlsExplainsWhatToDo() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0)
        var msg = ""
        try { StatementParser.fromSpreadsheet(ole, nov) } catch (e: StatementParser.NotReadableException) { msg = e.message.orEmpty() }
        assertTrue(msg.contains(".xlsx"))
    }
}
