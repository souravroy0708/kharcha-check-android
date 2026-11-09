package com.kharchacheck.app

import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.AmountExpression
import com.kharchacheck.app.domain.BackupCrypto
import com.kharchacheck.app.domain.ChallengeStatus
import com.kharchacheck.app.domain.CsvWriter
import com.kharchacheck.app.domain.Gamification
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.ReportEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MoneyTest {
    @Test fun formatsIndianGrouping() {
        assertEquals("₹1,24,500", Money.format(1_24_500_00))
        assertEquals("₹99.50", Money.format(9950, withPaise = true))
        assertEquals("−₹20", Money.format(-2000))
    }
    @Test fun compactLakhCrore() {
        assertEquals("₹2.32L", Money.compact(2_32_339_00))
        assertEquals("₹1.5Cr", Money.compact(1_50_00_000_00).replace("₹1.50Cr", "₹1.5Cr"))
        assertEquals("₹4.5K", Money.compact(4_500_00))
    }
    @Test fun parsesUserText() {
        assertEquals(125050L, Money.parse("1,250.50"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("abc"))
    }
}

class AmountExpressionTest {
    @Test fun evaluatesSums() {
        assertEquals(16500L, AmountExpression.evaluate("120+45"))
        assertEquals(10000L, AmountExpression.evaluate("120−20"))
        assertEquals(12050L, AmountExpression.evaluate("120.5+"))
        assertNull(AmountExpression.evaluate("20-50"))
        assertNull(AmountExpression.evaluate(""))
    }
    @Test fun keypadGuards() {
        var s = ""
        listOf("1", "2", ".", "5", "5", "5", ".", "+", "+", "−").forEach { s = AmountExpression.press(s, it) }
        assertEquals("12.55−", s)
        assertEquals("", AmountExpression.press("", "+"))
        assertEquals("0.", AmountExpression.press("", "."))
        assertEquals("5", AmountExpression.press("0", "5"))
    }
}

class InvestMathTest {
    @Test fun sipFutureValueMatchesStandardFormula() {
        // ₹1,000/month, 12% p.a., 10 years, paid at start of month ≈ ₹2,32,339
        val fv = InvestMath.sipFutureValue(1_000_00, 12.0, 10)
        assertTrue("got $fv", fv in 2_32_300_00..2_32_400_00)
    }
    @Test fun ruleOf72() = assertEquals(6.0, InvestMath.yearsToDouble(12.0), 0.0001)
    @Test fun ageRuleClamped() { assertEquals(70, InvestMath.equityShareByAge(30)); assertEquals(20, InvestMath.equityShareByAge(95)) }
}

class ReportEngineTest {
    private val m = YearMonth.of(2026, 10)
    private fun d(day: Int) = m.atDay(day)

    @Test fun spendExcludesInvestingAndTransfers() {
        val txns = listOf(
            Fx.tx("salary", 50_000, d(1)),
            Fx.tx("rent", 15_000, d(2)),
            Fx.tx("groceries", 5_000, d(3)),
            Fx.tx("dining_out", 4_000, d(4)),
            Fx.tx("food_delivery", 3_000, d(5)),
            Fx.tx("sip", 10_000, d(6)),
            Fx.tx("cc_bill", 20_000, d(7)),
            Fx.tx("atm_cash", 2_000, d(8)),
        )
        val r = ReportEngine.build(m, txns, Fx.byId, ReportEngine.Config(today = LocalDate.of(2026, 11, 8)))
        assertEquals(50_000_00L, r.incomePaise)
        assertEquals(27_000_00L, r.spendPaise)
        assertEquals(20_000_00L, r.needsPaise)
        assertEquals(3_000_00L, r.leaksPaise)
        assertEquals(10_000_00L, r.investPaise)
        assertEquals(22_000_00L, r.transferPaise)
        assertEquals(46, r.savingsRatePct)
        assertEquals(40, r.split.needsPct)
        assertEquals(14, r.split.wantsPct)
        assertEquals(46, r.split.savePct)
        assertTrue(r.split.followsRule)
        assertEquals("rent", r.categories.first().category.key)
        assertTrue(r.insights.any { it.text.contains("50/30/20") })
    }

    @Test fun projectsCurrentMonth() {
        val today = LocalDate.of(2026, 11, 10)
        val txns = listOf(Fx.tx("groceries", 3_000, LocalDate.of(2026, 11, 5)))
        val r = ReportEngine.build(YearMonth.of(2026, 11), txns, Fx.byId, ReportEngine.Config(budgetPaise = 5_000_00, today = today))
        assertEquals(9_000_00L, r.projectedSpendPaise)
        assertTrue(r.insights.any { it.emoji == "⏱️" })
    }
}

class GamificationTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun streakSurvivesUntilEndOfToday() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, Gamification.currentStreak(today) { it in days })
        assertEquals(4, Gamification.currentStreak(today) { it in days + today })
        assertEquals(0, Gamification.currentStreak(today) { it == today.minusDays(2) })
    }

    @Test fun xpLevelsAndBadges() {
        val txns = (0L..7L).map { Fx.tx("groceries", 100, today.minusDays(it)) } + Fx.tx("sip", 1_000, today)
        val g = Gamification.build(txns, Fx.byId, emptyList(), emptySet(), 0, 0, today)
        assertEquals(8, g.logStreak)
        assertTrue(g.loggedToday)
        assertTrue(g.badges.first { it.badge.id == "streak_7" }.earned)
        assertTrue(g.badges.first { it.badge.id == "first_invest" }.earned)
        assertTrue(g.xp >= 100)
        assertEquals(21, g.last21.size)
    }

    @Test fun avoidChallengeFailsOnViolation() {
        val ch = Gamification.challenge("no_delivery_7")!!
        val start = today.minusDays(3)
        val clean = Gamification.progress(ch, start, listOf(Fx.tx("groceries", 200, today.minusDays(1))), Fx.byId, emptySet(), today)
        assertEquals(ChallengeStatus.ACTIVE, clean.status)
        val broken = Gamification.progress(ch, start, listOf(Fx.tx("food_delivery", 300, today.minusDays(2))), Fx.byId, emptySet(), today)
        assertEquals(ChallengeStatus.FAILED, broken.status)
        val won = Gamification.progress(ch, today.minusDays(8), emptyList(), Fx.byId, emptySet(), today)
        assertEquals(ChallengeStatus.WON, won.status)
    }

    @Test fun noSpendChallengeCountsDays() {
        val ch = Gamification.challenge("zero_days_4")!!
        val ns = (1L..4L).map { today.minusDays(it).toEpochDay() }.toSet()
        val p = Gamification.progress(ch, today.minusDays(5), emptyList(), Fx.byId, ns, today)
        assertEquals(ChallengeStatus.WON, p.status)
    }
}

class CsvWriterTest {
    @Test fun escapesAndBlocksFormulas() {
        assertEquals("'=HYPERLINK(1)", CsvWriter.cell("=HYPERLINK(1)"))
        assertEquals("\"a,b\"", CsvWriter.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell("say \"hi\""))
        val csv = CsvWriter.write(listOf(Fx.tx("groceries", 250, LocalDate.of(2026, 11, 1))), Fx.byId)
        assertTrue(csv.lines()[1].contains("250.00"))
    }
}

class BackupCryptoTest {
    @Test fun roundTripAndWrongPassword() {
        val data = "{\"hello\":\"duniya\"}".toByteArray()
        val blob = BackupCrypto.encrypt(data, "correct horse".toCharArray(), iterations = 100_000)
        assertTrue(BackupCrypto.decrypt(blob, "correct horse".toCharArray()).contentEquals(data))
        var threw = false
        try { BackupCrypto.decrypt(blob, "wrong".toCharArray()) } catch (e: BackupCrypto.WrongPasswordException) { threw = true }
        assertTrue(threw)
        var notBackup = false
        try { BackupCrypto.decrypt(ByteArray(100), "x".toCharArray()) } catch (e: BackupCrypto.NotABackupException) { notBackup = true }
        assertTrue(notBackup)
        assertFalse(blob.copyOfRange(36, blob.size).contentEquals(data))
    }
}

class RecurringTest {
    private fun rule(day: Int, last: String) = com.kharchacheck.app.domain.RecurringRule(
        amountPaise = 15_000_00, type = TxType.EXPENSE, categoryId = 1, mode = com.kharchacheck.app.core.model.PayMode.UPI,
        payee = "Rent", note = "", dayOfMonth = day, lastMonth = YearMonth.parse(last),
    )

    @Test fun createsOnlyOnceTheDayHasCome() {
        val p = com.kharchacheck.app.domain.RecurringPlanner
        assertTrue(p.dueMonths(rule(5, "2026-10"), LocalDate.of(2026, 11, 4)).isEmpty())
        assertEquals(listOf(YearMonth.of(2026, 11)), p.dueMonths(rule(5, "2026-10"), LocalDate.of(2026, 11, 5)))
        assertTrue(p.dueMonths(rule(5, "2026-11"), LocalDate.of(2026, 11, 30)).isEmpty())
    }

    @Test fun clampsDayAndCapsCatchUp() {
        val p = com.kharchacheck.app.domain.RecurringPlanner
        assertEquals(LocalDate.of(2027, 2, 28), p.dateIn(rule(31, "2027-01"), YearMonth.of(2027, 2)))
        val due = p.dueMonths(rule(1, "2025-01"), LocalDate.of(2026, 11, 8))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), due)
    }
}

class IncomeFallbackTest {
    private val m = YearMonth.of(2026, 10)
    private val cfg = ReportEngine.Config(today = LocalDate.of(2026, 11, 8), fallbackIncomePaise = 40_000_00)

    @Test fun usesSettingsIncomeOnlyWhenNoneLogged() {
        val spendOnly = listOf(Fx.tx("rent", 10_000, m.atDay(2)))
        val r = ReportEngine.build(m, spendOnly, Fx.byId, cfg)
        assertTrue(r.incomeEstimated)
        assertEquals(40_000_00L, r.incomePaise)
        assertEquals(75, r.savingsRatePct)

        val withSalary = spendOnly + Fx.tx("salary", 50_000, m.atDay(1))
        val r2 = ReportEngine.build(m, withSalary, Fx.byId, cfg)
        assertFalse(r2.incomeEstimated)
        assertEquals(50_000_00L, r2.incomePaise)
    }

    @Test fun emptyMonthStaysEmpty() {
        val r = ReportEngine.build(m, emptyList(), Fx.byId, cfg)
        assertFalse(r.incomeEstimated)
        assertEquals(0L, r.incomePaise)
    }
}
