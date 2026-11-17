package com.kharchacheck.app

import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.PlanInput
import com.kharchacheck.app.domain.RiskComfort
import com.kharchacheck.app.domain.SalaryPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryPlannerTest {
    @Test fun fiftyThousandSalaryPlan() {
        val p = SalaryPlanner.plan(PlanInput(50_000_00, age = 28, dependents = true, risk = RiskComfort.BALANCED))
        assertEquals(25, p.savePct)
        assertEquals(12_500_00L, p.savePaise)
        assertEquals(25_000_00L, p.needsCapPaise)
        assertEquals(12_500_00L, p.wantsCapPaise)
        assertEquals(p.input.monthlyIncomePaise, p.needsCapPaise + p.wantsCapPaise + p.savePaise)
        assertEquals(72, p.equityPct)                       // 100 − 28
        assertEquals(1_50_000_00L, p.emergencyTargetPaise)   // 6 × ₹25,000 estimated needs
        assertEquals(72_00_000_00L, p.termCoverPaise)        // 12 × ₹6L
        assertEquals(p.savePaise, p.allocation.sumOf { it.monthlyPaise } + (p.savePaise - p.allocation.sumOf { it.monthlyPaise }))
        assertTrue(p.allocation.sumOf { it.sharePct } == 100)
        assertTrue(p.corpus20yStepUp > p.corpus20y && p.corpus20y > p.corpus10y)
        assertEquals(listOf("budget", "starter_fund", "health", "term", "full_fund", "sip", "step_up", "review"), p.steps.map { it.id })
    }

    @Test fun lowIncomeSingleWithHeavyEmis() {
        val p = SalaryPlanner.plan(PlanInput(20_000_00, age = 23, dependents = false, risk = RiskComfort.SAFE, emiPaise = 9_000_00))
        assertEquals(10, p.savePct)
        assertEquals(12_000_00L, p.needsCapPaise)             // 60% cap for lower incomes
        assertNull(p.termCoverPaise)
        assertEquals(45, p.emiPct)
        assertTrue(p.emiWarning!!.contains("risky"))
        assertTrue("debt" in p.steps.map { it.id })
        assertEquals(57, p.equityPct)                          // 100 − 23 − 20
    }

    @Test fun equityClampedAndStepsMarkedDone() {
        val old = SalaryPlanner.plan(PlanInput(1_50_000_00, age = 70, dependents = false, risk = RiskComfort.SAFE, emergencySavedPaise = 99_00_00_000), setOf("health"))
        assertEquals(20, old.equityPct)
        assertEquals(30, old.savePct)
        assertTrue(old.steps.first { it.id == "health" }.done)
        assertTrue(old.steps.first { it.id == "full_fund" }.done)
    }

    @Test fun stepUpMatchesFlatWhenNoStepUp() {
        val flat = InvestMath.sipFutureValue(10_000_00, 12.0, 10)
        val zero = SalaryPlanner.stepUpSip(10_000_00, 12.0, 10, 0.0)
        assertTrue(kotlin.math.abs(flat - zero) < 100)  // within ₹1
    }
}

class GuideGamificationTest {
    @Test fun practisedTipsAndPlanStepsEarnXpAndBadges() {
        val today = java.time.LocalDate.of(2026, 11, 10)
        val base = com.kharchacheck.app.domain.Gamification.build(emptyList(), Fx.byId, emptyList(), emptySet(), 0, 0, today)
        val days = (0L..6L).map { today.minusDays(it).toEpochDay() }.toSet()
        val g = com.kharchacheck.app.domain.Gamification.build(emptyList(), Fx.byId, emptyList(), emptySet(), 0, 0, today, days, 3)
        assertEquals(base.xp + 7 * 15 + 3 * 40 + 2 * 50, g.xp)
        assertTrue(g.badges.first { it.badge.id == "habit_builder" }.earned)
        assertTrue(g.badges.first { it.badge.id == "plan_progress" }.earned)
    }

    @Test fun onlineTipsCoverLeakCategories() {
        assertTrue(com.kharchacheck.app.domain.OnlineSpending.tipsFor("food_delivery").size >= 3)
        assertTrue(com.kharchacheck.app.domain.OnlineSpending.tipsFor("quick_commerce").isNotEmpty())
        assertEquals(24, com.kharchacheck.app.domain.OnlineSpending.tips.map { it.id }.toSet().size)
    }
}
