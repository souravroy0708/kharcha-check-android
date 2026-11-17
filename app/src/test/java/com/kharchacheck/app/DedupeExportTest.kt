package com.kharchacheck.app

import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.Dedupe
import com.kharchacheck.app.domain.export.ExportSpec
import com.kharchacheck.app.domain.export.Exporter
import com.kharchacheck.app.domain.read.SpreadsheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DedupeTest {
    private val d = LocalDate.of(2026, 11, 3)

    @Test fun reimportingSameStatementAddsNothing() {
        val first = listOf(
            Fx.tx("food_delivery", 340, d).copy(id = 1, payee = "Swiggy", refNo = "630518920112"),
            Fx.tx("chai_coffee", 40, d).copy(id = 2, payee = "Ramesh Tea"),
            Fx.tx("chai_coffee", 40, d).copy(id = 3, payee = "Ramesh Tea"),   // genuine second chai, same day
        )
        val again = first.map { it.copy(id = 0) }
        assertTrue(Dedupe.check(first, again).all { it.verdict == Dedupe.Verdict.DUPLICATE })
        // A third identical chai in the new file is new (count-based matching)
        val withExtra = again + Fx.tx("chai_coffee", 40, d).copy(payee = "Ramesh Tea")
        assertEquals(Dedupe.Verdict.NEW, Dedupe.check(first, withExtra).last().verdict)
    }

    @Test fun manualEntryVsStatementIsPossibleNotSilent() {
        val manual = listOf(Fx.tx("chai_coffee", 40, d).copy(id = 9, payee = "Chai"))
        val stmt = listOf(Fx.tx("chai_coffee", 40, d.plusDays(1)).copy(payee = "UPI Ramesh Tea Stall"))
        assertEquals(Dedupe.Verdict.POSSIBLE, Dedupe.check(manual, stmt).single().verdict)
        // Income never matches an expense of the same amount
        val salary = listOf(Fx.tx("salary", 40, d))
        assertEquals(Dedupe.Verdict.NEW, Dedupe.check(manual, salary).single().verdict)
    }

    @Test fun refNumberWinsEvenWithDifferentPayeeText() {
        val existing = listOf(Fx.tx("food_delivery", 340, d).copy(id = 1, payee = "Swiggy", refNo = "0000630518920112"))
        val incoming = listOf(Fx.tx("food_delivery", 340, d.plusDays(2)).copy(payee = "Bundl Technologies", refNo = "630518920112"))
        assertEquals(Dedupe.Verdict.DUPLICATE, Dedupe.check(existing, incoming).single().verdict)
    }
}

class ExportTest {
    private val txns = listOf(
        Fx.tx("salary", 52_000, LocalDate.of(2026, 10, 1)).copy(payee = "ACME, Pvt Ltd"),
        Fx.tx("food_delivery", 340, LocalDate.of(2026, 10, 5)).copy(payee = "Swiggy", note = "=HYPERLINK(\"x\")"),
        Fx.tx("rent", 15_000, LocalDate.of(2026, 11, 2)),
    )

    @Test fun csvHasBomDebitCreditAndIsFormulaSafe() {
        val text = String(Exporter.csv(txns, Fx.byId, ExportSpec()), Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFFDate,Time,Type"))
        val lines = text.trim().split("\r\n")
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("\"ACME, Pvt Ltd\"") && lines[1].contains(",,52000.00,52000.00,"))   // credit only
        assertTrue(lines[2].contains(",340.00,,340.00,"))                                                // debit only
        assertTrue(lines[2].contains("'=HYPERLINK"))
    }

    @Test fun rangeAndTypeFilters() {
        val oct = ExportSpec(from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31), types = setOf(TxType.EXPENSE))
        assertEquals(1, Exporter.select(txns, oct).size)
        assertEquals("KharchaCheck-2026-10.xlsx", Exporter.fileName(oct, "xlsx"))
    }

    @Test fun xlsxRoundTripsThroughOurReader() {
        val bytes = Exporter.xlsx(txns, Fx.byId, ExportSpec())
        val rows = SpreadsheetReader.xlsx(bytes)
        assertEquals("Date", rows[0][0])
        assertEquals(4, rows.size)
        assertEquals("Swiggy", rows[2][5])
        assertEquals("340.00", rows[2][6])
        assertEquals("46300", rows[2][0])  // Excel serial date for 2026-10-05
    }
}

class BudgetAlertsTest {
    @Test fun crossingsFireOncePerThreshold() {
        val b = com.kharchacheck.app.domain.BudgetAlerts
        assertEquals(80, b.crossing(2_000_00, 2_450_00, 3_000_00))
        assertEquals(null, b.crossing(2_450_00, 2_600_00, 3_000_00))   // already past 80%, not yet 100%
        assertEquals(100, b.crossing(2_600_00, 3_100_00, 3_000_00))
        assertEquals(100, b.crossing(1_000_00, 3_500_00, 3_000_00))    // jumps straight past both: report the bigger one
        assertEquals(null, b.crossing(3_100_00, 3_300_00, 3_000_00))
        assertEquals(null, b.crossing(0, 5_000_00, 0))
        assertEquals(80, b.level(2_400_00, 3_000_00))
        assertTrue(b.message("Food Delivery", 3_150_00, 3_000_00, 100).contains("Over"))
    }
}
