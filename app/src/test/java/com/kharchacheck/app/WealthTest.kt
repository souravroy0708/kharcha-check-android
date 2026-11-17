package com.kharchacheck.app

import com.kharchacheck.app.domain.AmfiNav
import com.kharchacheck.app.domain.BackupRotation
import com.kharchacheck.app.domain.Holding
import com.kharchacheck.app.domain.HoldingKind
import com.kharchacheck.app.domain.NetItem
import com.kharchacheck.app.domain.NetSide
import com.kharchacheck.app.domain.Wealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WealthTest {
    private val sample = """
        Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date

        Open Ended Schemes(Equity Scheme - Flexi Cap Fund)

        PPFAS Mutual Fund

        122639;INF879O01027;-;Parag Parikh Flexi Cap Fund - Direct Plan - Growth;92.4567;07-Nov-2026
        122640;INF879O01035;-;Parag Parikh Flexi Cap Fund - Regular Plan - Growth;84.1200;07-NOV-2026

        Open Ended Schemes(Index Funds)

        UTI Mutual Fund

        120716;INF789F01XA0;-;UTI Nifty 50 Index Fund - Direct Plan - Growth Option;171.2345;07-Nov-2026
        999999;-;-;Some Wound Up Scheme;N.A.;01-Jan-2020
    """.trimIndent()

    @Test fun parsesNavFileWithHeaders() {
        val s = AmfiNav.parse(sample)
        assertEquals(4, s.size)
        val ppfas = s.first()
        assertEquals("122639", ppfas.code)
        assertEquals("PPFAS Mutual Fund", ppfas.amc)
        assertTrue(ppfas.category.contains("Flexi Cap"))
        assertEquals(924567L, ppfas.navMicro)
        assertEquals(LocalDate.of(2026, 11, 7), ppfas.date)
        assertEquals(LocalDate.of(2026, 11, 7), s[1].date) // upper-case month accepted
        assertEquals("UTI Mutual Fund", s[2].amc)
        assertNull(s[3].navMicro)
    }

    @Test fun searchPrefersDirectGrowth() {
        val s = AmfiNav.parse(sample)
        assertEquals("122639", AmfiNav.search(s, "parikh flexi").first().code)
        assertEquals("120716", AmfiNav.search(s, "nifty uti").first().code)
        assertEquals(listOf("122640"), AmfiNav.search(s, "122640").map { it.code })
    }

    @Test fun liveValueGainAndNetWorth() {
        val navs = AmfiNav.parse(sample).associateBy { it.code }
        val mf = Holding(kind = HoldingKind.MUTUAL_FUND, name = "PPFAS", schemeCode = "122639", units = "1000.123", investedPaise = 80_000_00)
        val v = Wealth.value(mf, navs)
        assertTrue(v.live)
        assertEquals(9_246_807L, v.valuePaise) // 1000.123 × 92.4567 = 92,468.0722 → ₹92,468.07
        val fd = Holding(kind = HoldingKind.FD_RD, name = "SBI FD", investedPaise = 1_00_000_00, manualValuePaise = 1_07_000_00)
        val nw = Wealth.netWorth(listOf(mf, fd), listOf(
            NetItem(side = NetSide.ASSET, name = "Savings", emoji = "🏦", valuePaise = 50_000_00),
            NetItem(side = NetSide.LIABILITY, name = "Car loan", emoji = "🚗", valuePaise = 2_00_000_00),
        ), navs)
        assertEquals(v.valuePaise + 1_07_000_00 + 50_000_00 - 2_00_000_00, nw.netPaise)
        assertEquals(1_80_000_00L, nw.investedPaise)
        assertTrue(nw.gainPct!! > 0)
        // Unknown scheme falls back to invested amount, flagged as not live
        assertEquals(500_00L, Wealth.value(mf.copy(schemeCode = "000", investedPaise = 500_00), navs).valuePaise)
    }

    @Test fun snapshotsKeepLatestPerMonthAnd24Months() {
        var h = emptyMap<YearMonth, Long>()
        (0L..29L).forEach { h = Wealth.mergeSnapshot(h, YearMonth.of(2024, 1).plusMonths(it), it * 100) }
        assertEquals(24, h.size)
        h = Wealth.mergeSnapshot(h, YearMonth.of(2026, 6), 1)
        assertEquals(1L, h[YearMonth.of(2026, 6)])
    }

    @Test fun backupRotationOnlyTouchesAutoFiles() {
        val names = (1..10).map { BackupRotation.fileName(LocalDate.of(2026, 11, it)) } + "my-manual.kcbak"
        val del = BackupRotation.toDelete(names, keep = 7)
        assertEquals(listOf("kharcha-auto-2026-11-03.kcbak", "kharcha-auto-2026-11-02.kcbak", "kharcha-auto-2026-11-01.kcbak"), del)
    }
}
