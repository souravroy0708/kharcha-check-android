package com.kharchacheck.app

import com.kharchacheck.app.domain.GoalKind
import com.kharchacheck.app.domain.GoalPlan
import com.kharchacheck.app.domain.GoalPlanner
import com.kharchacheck.app.domain.InvestMath
import com.kharchacheck.app.domain.SalaryPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GoalPlannerTest {
    @Test fun daughtersEducationIn15Years() {
        // ₹20L course today, daughter is 3, college at 18 → 15 years, education inflation 10%
        val years = GoalPlanner.yearsForChild(3, 18)
        assertEquals(15, years)
        val m = GoalPlanner.plan(GoalPlan(1, GoalKind.CHILD_EDUCATION, 20_00_000_00, years), savedPaise = 0)
        // 20L × 1.1^15 ≈ ₹83.5L
        assertTrue("future ${m.futureCostPaise}", m.futureCostPaise in 83_00_000_00..84_00_000_00)
        assertEquals(11.0, m.expectedReturnPct, 0.0)
        assertTrue(m.mix.startsWith("Mostly equity"))
        // The flat SIP must actually reach the target (within ₹100 rounding × months)
        val fv = InvestMath.sipFutureValue(m.sipFlatPaise, 11.0, years)
        assertTrue("flat SIP ${m.sipFlatPaise} reaches ${fv} vs ${m.futureCostPaise}", fv >= m.futureCostPaise && fv - m.futureCostPaise < 100_00 * 12 * years * 3)
        // A 10%-a-year step-up starts lower and still reaches it
        assertTrue(m.sipStepUpStartPaise < m.sipFlatPaise)
        assertTrue(SalaryPlanner.stepUpSip(m.sipStepUpStartPaise, 11.0, years, 10.0) >= m.futureCostPaise)
    }

    @Test fun existingSavingsReduceTheSipAndFullyFundedNeedsNothing() {
        val p = GoalPlan(2, GoalKind.HOME, 15_00_000_00, 5)
        val none = GoalPlanner.plan(p, 0)
        val some = GoalPlanner.plan(p, 5_00_000_00)
        assertTrue(some.sipFlatPaise < none.sipFlatPaise)
        assertTrue(some.alreadyCoveredPct in 30..45)
        val rich = GoalPlanner.plan(p, 1_00_00_000_00)
        assertEquals(0L, rich.sipFlatPaise); assertEquals(100, rich.alreadyCoveredPct)
        assertEquals(9.0, none.expectedReturnPct, 0.0)       // 5 years → balanced
    }

    @Test fun shortGoalsStaySafeAndEmergencyHasNoInflation() {
        val m = GoalPlanner.plan(GoalPlan(3, GoalKind.EMERGENCY, 3_00_000_00, 1), 0)
        assertEquals(3_00_000_00L, m.futureCostPaise)
        assertTrue(m.mix.startsWith("Safe"))
    }

    @Test fun leakCoverAndRescuedMoney() {
        assertEquals(45, GoalPlanner.leakCoverPct(1_800_00, 4_000_00))
        // Usually ₹6,000/month on leaks; 15 days into a 30-day month you've spent ₹1,800 → ₹1,200 rescued so far
        assertEquals(1_200_00L, GoalPlanner.rescued(1_800_00, 6_000_00, 15, 30))
        assertEquals(0L, GoalPlanner.rescued(5_000_00, 6_000_00, 15, 30))
    }

    @Test fun onTrackAllowsSmallShortfall() {
        val m = GoalPlanner.plan(GoalPlan(4, GoalKind.CAR, 8_00_000_00, 3), 0)
        assertTrue(GoalPlanner.onTrack(m, 6, m.sipFlatPaise * 6, 0))
        assertTrue(!GoalPlanner.onTrack(m, 6, m.sipFlatPaise * 3, 0))
        assertTrue(abs(m.futureCostPaise - 8_00_000_00 * 1.157625) < 100)
    }
}
