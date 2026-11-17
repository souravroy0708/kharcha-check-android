package com.kharchacheck.app

import com.kharchacheck.app.domain.HealthInputs
import com.kharchacheck.app.domain.HealthScorer
import com.kharchacheck.app.domain.Pillar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthScoreTest {
    private val good = HealthInputs(avgIncomePaise = 1_00_000_00, avgSpendPaise = 65_000_00, avgNeedsPaise = 40_000_00, leaksSharePct = 4,
        emiPaise = 8_000_00, costlyDebt = false, emergencySavedPaise = 2_50_000_00, healthInsured = true, termInsured = true, dependents = true, investMonths = 3)

    @Test fun strongFinancesScoreHigh() {
        val s = HealthScorer.score(good)
        assertTrue("score ${s.score}", s.score!! >= 90)
        assertEquals("Excellent", s.grade)
        assertNull(s.nextStep)
    }

    @Test fun weakestImportantPillarBecomesTheNextStep() {
        val s = HealthScorer.score(good.copy(emergencySavedPaise = 0, leaksSharePct = 12))
        assertEquals(Pillar.EMERGENCY, s.nextStep!!.pillar)   // 0/100 × weight 20 beats leaks 72 × 15
        val debt = HealthScorer.score(good.copy(costlyDebt = true, emiPaise = 40_000_00))
        val d = debt.pillars.first { it.pillar == Pillar.DEBT }
        assertTrue("debt ${d.score}", d.score!! <= 0 + 35 - 0)       // 40% EMIs (~35) minus card interest penalty → 0
        assertEquals("bills", d.route)
    }

    @Test fun newUserIsNotPunishedForMissingData() {
        val s = HealthScorer.score(HealthInputs(null, 0, 0, null, 0, false, 0, false, false, false, 0))
        assertNull(s.score)                       // only protection + investing known → not enough for a score
        assertEquals("Add more data", s.grade)
        val partial = HealthScorer.score(HealthInputs(null, 20_000_00, 15_000_00, 8, 0, false, 45_000_00, true, false, false, 2))
        assertTrue(partial.score != null)         // emergency (from needs), protection, leaks, investing
    }

    @Test fun curveInterpolates() {
        assertEquals(80, HealthScorer.curve(20.0, 0.0 to 10, 20.0 to 80, 30.0 to 100))
        assertEquals(90, HealthScorer.curve(25.0, 0.0 to 10, 20.0 to 80, 30.0 to 100))
        assertEquals(10, HealthScorer.curve(-5.0, 0.0 to 10, 30.0 to 100))
    }
}
