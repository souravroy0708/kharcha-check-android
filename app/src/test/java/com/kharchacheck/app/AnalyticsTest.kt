package com.kharchacheck.app

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.domain.AnalyticsEngine
import com.kharchacheck.app.domain.DayPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class AnalyticsTest {
    private val end = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 11, 8)

    private fun rentAndSalary() = (0L..5L).flatMap { back ->
        val m = end.minusMonths(back)
        listOf(
            Fx.tx("salary", 50_000, m.atDay(1)),
            Fx.tx("rent", 15_000, m.atDay(2)).copy(payee = "Landlord"),
            Fx.tx("ott", 199, m.atDay(5)).copy(payee = "Netflix"),
            Fx.tx("sip", 5_000, m.atDay(7)).copy(payee = "Index Fund SIP"),
        )
    }

    @Test fun monthlySeriesAndAverages() {
        val txns = rentAndSalary() + Fx.tx("food_delivery", 600, end.atDay(10)) + Fx.tx("food_delivery", 400, end.atDay(11))
        val a = AnalyticsEngine.build(txns, Fx.byId, end, 6, today)
        assertEquals(6, a.months.size)
        assertEquals(YearMonth.of(2026, 5), a.months.first().month)
        val oct = a.months.last()
        assertEquals(50_000_00L, oct.incomePaise)
        assertEquals(15_000_00L + 199_00 + 1_000_00, oct.spendPaise)
        assertEquals(5_000_00L, oct.investPaise)
        assertEquals(1_000_00L, oct.leaksPaise)
        assertEquals(oct.incomePaise - oct.spendPaise, oct.netPaise)
        assertEquals(50_000_00L, a.avgMonthlyIncomePaise)
        assertEquals("rent", a.categories.first().category.key)
        assertEquals(15_000_00L * 12, a.categories.first().yearlyPaise)
        assertTrue(a.buckets.getValue(Bucket.LEAK) == 1_000_00L)
    }

    @Test fun detectsRegularPaymentsButNotOneOffs() {
        val txns = rentAndSalary() + Fx.tx("gadgets", 30_000, end.atDay(15)).copy(payee = "Croma")
        val names = AnalyticsEngine.build(txns, Fx.byId, end, 6, today).regulars.map { it.payee }
        assertEquals(listOf("Landlord", "Index Fund SIP", "Netflix"), names)
    }

    @Test fun regularToleratesSmallPriceChangesOnly() {
        val ok = (0L..3L).map { Fx.tx("ott", if (it == 0L) 219 else 199, end.minusMonths(it).atDay(5)).copy(payee = "Netflix") }
        assertEquals(1, AnalyticsEngine.build(ok, Fx.byId, end, 4, today).regulars.size)
        val wild = (0L..3L).map { Fx.tx("dining_out", 500L + it * 900, end.minusMonths(it).atDay(5)).copy(payee = "Cafe") }
        assertEquals(0, AnalyticsEngine.build(wild, Fx.byId, end, 4, today).regulars.size)
    }

    @Test fun outliersNeedHistoryAndScale() {
        val normal = (1..6).map { Fx.tx("groceries", 800, end.atDay(it)) }
        val big = Fx.tx("groceries", 9_000, end.atDay(20))
        val a = AnalyticsEngine.build(normal + big, Fx.byId, end, 1, today)
        assertEquals(listOf(9_000_00L), a.outliers.map { it.amountPaise })
        assertTrue(AnalyticsEngine.build(normal.take(3) + big, Fx.byId, end, 1, today).outliers.isEmpty())
    }

    @Test fun heatmapBucketsByWeekdayAndTime() {
        val fridayNight = LocalDate.of(2026, 10, 9) // a Friday
        val a = AnalyticsEngine.build(listOf(Fx.tx("food_delivery", 700, fridayNight, hour = 23), Fx.tx("chai_coffee", 30, fridayNight, hour = 9)), Fx.byId, end, 1, today)
        assertEquals(DayOfWeek.FRIDAY to DayPart.NIGHT, a.busiest)
        assertEquals(DayPart.NIGHT, DayPart.of(2))
        assertEquals(DayPart.EVENING, DayPart.of(21))
    }

    @Test fun trendNeedsHistory() {
        assertNull(AnalyticsEngine.trend(listOf(100, 200)))
        assertEquals(50, AnalyticsEngine.trend(listOf(100, 100, 150)))
        assertNull(AnalyticsEngine.trend(listOf(0, 100, 150)))
    }

    @Test fun categoryDrillDownWorksForIncomeToo() {
        val d = AnalyticsEngine.category(rentAndSalary(), Fx.byId, Fx.id("salary"), end, 6)
        assertEquals(6, d.txns.size)
        assertEquals(50_000_00L, d.share!!.monthlyAvgPaise)
        val r = AnalyticsEngine.category(rentAndSalary(), Fx.byId, Fx.id("rent"), end, 6)
        assertEquals("Landlord", r.payees.first().name)
    }
}

class WeeklyDigestTest {
    @Test fun lastFullWeekVsFourWeekAverage() {
        val monday = LocalDate.of(2026, 11, 9)
        val lastWeek = listOf(Fx.tx("food_delivery", 1_200, LocalDate.of(2026, 11, 7)), Fx.tx("groceries", 300, LocalDate.of(2026, 11, 2)))
        val earlier = (1L..4L).map { Fx.tx("groceries", 1_000, LocalDate.of(2026, 10, 28).minusWeeks(it - 1)) }
        val thisWeek = listOf(Fx.tx("rent", 15_000, monday)) // not part of last week
        val w = AnalyticsEngine.lastWeek(lastWeek + earlier + thisWeek, Fx.byId, monday)
        assertEquals(1_500_00L, w.spendPaise)
        assertEquals(1_000_00L, w.avgPrevPaise)
        assertEquals(50, w.deltaPct)
        assertEquals("food_delivery", w.topCategory?.key)
    }
}
