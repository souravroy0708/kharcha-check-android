package com.kharchacheck.app

import com.kharchacheck.app.domain.Subscriptions
import com.kharchacheck.app.domain.TaxBucket
import com.kharchacheck.app.domain.TaxSaver
import com.kharchacheck.app.domain.read.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaxSaverTest {
    private val today = LocalDate.of(2027, 1, 20)   // FY 2026-27

    @Test fun financialYearRunsAprilToMarch() {
        assertEquals(2026, TaxSaver.fyStart(LocalDate.of(2027, 3, 31)))
        assertEquals(2027, TaxSaver.fyStart(LocalDate.of(2027, 4, 1)))
    }

    @Test fun bucketsLimitsManualAndOverrides() {
        val txns = listOf(
            Fx.tx("ppf_epf_nps", 50_000, LocalDate.of(2026, 5, 10)).copy(id = 1, payee = "PPF SBI"),
            Fx.tx("ppf_epf_nps", 20_000, LocalDate.of(2026, 6, 10)).copy(id = 2, payee = "NPS Tier 1"),
            Fx.tx("insurance", 18_000, LocalDate.of(2026, 7, 1)).copy(id = 3, payee = "Star Health"),
            Fx.tx("insurance", 30_000, LocalDate.of(2026, 8, 1)).copy(id = 4, payee = "LIC"),
            Fx.tx("sip", 12_000, LocalDate.of(2026, 9, 1)).copy(id = 5, payee = "Axis ELSS Tax Saver"),
            Fx.tx("sip", 99_000, LocalDate.of(2026, 9, 1)).copy(id = 6, payee = "Nifty Index Fund"),  // not tax-saving
            Fx.tx("ppf_epf_nps", 40_000, LocalDate.of(2026, 3, 1)).copy(id = 7, payee = "PPF"),        // previous FY
            Fx.tx("education", 60_000, LocalDate.of(2026, 6, 15)).copy(id = 8, payee = "School"),
        )
        val s = TaxSaver.summary(txns, Fx.byId, today, assigned = mapOf(8L to null), manual = mapOf(TaxBucket.C80 to 21_600_00),
            slabPct = 30, selfSenior = false, parentsSenior = false)
        val c80 = s.buckets.first { it.bucket == TaxBucket.C80 }
        assertEquals(50_000_00L + 30_000_00 + 12_000_00 + 21_600_00, c80.usedPaise)   // school fees switched off by the user
        assertEquals(1_50_000_00L - 1_13_600_00, c80.leftPaise)
        assertEquals(20_000_00L, s.buckets.first { it.bucket == TaxBucket.NPS_1B }.usedPaise)
        assertEquals(18_000_00L, s.buckets.first { it.bucket == TaxBucket.D80_SELF }.usedPaise)
        assertEquals("FY 2026-27", s.fyLabel)
        assertEquals(70L, s.daysLeft)
        // tax saved = deductions × 30% × 1.04
        assertEquals((1_13_600_00L + 20_000_00 + 18_000_00) * 30 * 104 / 10_000, s.savedTaxPaise)
    }

    @Test fun capsAtLimitAndSeniorParents() {
        val txns = listOf(Fx.tx("insurance", 45_000, LocalDate.of(2026, 10, 1)).copy(id = 1, payee = "Care Health parents"))
        val s = TaxSaver.summary(txns, Fx.byId, today, mapOf(1L to TaxBucket.D80_PARENTS), emptyMap(), 20, false, parentsSenior = true)
        val p = s.buckets.first { it.bucket == TaxBucket.D80_PARENTS }
        assertEquals(50_000_00L, p.limitPaise); assertEquals(5_000_00L, p.leftPaise)
        val young = TaxSaver.summary(txns, Fx.byId, today, mapOf(1L to TaxBucket.D80_PARENTS), emptyMap(), 20, false, parentsSenior = false)
        assertEquals(25_000_00L, young.buckets.first { it.bucket == TaxBucket.D80_PARENTS }.counted)
    }
}

class SubscriptionsTest {
    private val today = LocalDate.of(2026, 11, 20)
    @Test fun detectsSmallRegularWantsNotRentAndFlagsChargesAfterCancel() {
        val txns = (0L..3L).flatMap { m ->
            val d = today.minusMonths(m).withDayOfMonth(5)
            listOf(Fx.tx("ott", 649, d).copy(payee = "Netflix"), Fx.tx("rent", 15_000, d).copy(payee = "Landlord"),
                Fx.tx("chai_coffee", 120, d).copy(payee = "Cult Pass"))
        }
        val subs = Subscriptions.detect(txns, Fx.byId, today)
        assertEquals(listOf("Netflix"), subs.filter { it.category?.key == "ott" }.map { it.payee })
        assertTrue(subs.none { it.payee == "Landlord" })
        assertEquals(649_00L * 12, subs.first { it.payee == "Netflix" }.yearlyPaise)
        val still = Subscriptions.stillCharged(txns, mapOf("netflix" to today.minusMonths(1).withDayOfMonth(10)))
        assertEquals(1, still.size)   // charged again on the 5th of this month
        assertEquals(649_00L * 12, Subscriptions.yearlySaved(subs, setOf("netflix")))
    }
}

class CardSummaryTest {
    @Test fun inlineLabels() {
        val s = StatementParser.cardSummary(listOf("Statement Date: 25/11/2026", "Payment Due Date: 15/12/2026", "Total Amount Due Rs. 18,450.00", "Minimum Amount Due 920.00"))!!
        assertEquals(LocalDate.of(2026, 12, 15), s.dueDate); assertEquals(18_450_00L, s.totalDuePaise); assertEquals(920_00L, s.minDuePaise)
    }

    @Test fun boxedLabelsThenValues() {
        val s = StatementParser.cardSummary(listOf("Payment Due Date   Total Dues   Minimum Amount Due", "05 Jan 2027   1,02,526.25   5,130.00"))!!
        assertEquals(LocalDate.of(2027, 1, 5), s.dueDate); assertEquals(1_02_526_25L, s.totalDuePaise); assertEquals(5_130_00L, s.minDuePaise)
    }

    @Test fun nothingOnABankStatement() = assertNull(StatementParser.cardSummary(listOf("01/11/26 UPI-SWIGGY 340.00 24,660.00")))
}
