package com.kharchacheck.app

import com.kharchacheck.app.domain.NudgeContext
import com.kharchacheck.app.domain.NudgeSlot
import com.kharchacheck.app.domain.Nudges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NudgesTest {
    private val seed = 424242L

    @Test fun eachSlotHasALargeUniquePoolAndNeverRepeatsWithinACycle() {
        NudgeSlot.entries.forEach { slot ->
            val pool = Nudges.pools.getValue(slot)
            assertTrue("$slot has ${pool.size}", pool.size >= 60)
            assertEquals(pool.size, pool.map { it.first }.toSet().size)
            val start = LocalDate.of(2026, 1, 1).let { d -> d.minusDays(Math.floorMod(d.toEpochDay(), pool.size.toLong())) } // cycle start
            val shown = (0 until pool.size).map { Nudges.poolItem(slot, start.plusDays(it.toLong()), seed).key }
            assertEquals("no repeats in a cycle for $slot", pool.size, shown.toSet().size)
        }
        // The three slots on the same day are different messages
        val d = LocalDate.of(2026, 11, 20)
        assertEquals(3, NudgeSlot.entries.map { Nudges.poolItem(it, d, seed).key }.toSet().size)
    }

    @Test fun personalNudgesWinThenRespectCooldown() {
        val d = LocalDate.of(2026, 11, 20)
        val c = NudgeContext(today = d, deliveryOrdersThisWeek = 4, deliverySpendThisWeekPaise = 1_600_00)
        val first = Nudges.pick(NudgeSlot.AFTERNOON, c, seed, emptyMap())
        assertEquals("delivery", first.key)
        assertTrue(first.title.contains("4 delivery orders") && first.body.contains("₹400"))
        // Shown yesterday → cooldown (3 days) → falls back to the unique pool item
        val next = Nudges.pick(NudgeSlot.AFTERNOON, c.copy(today = d.plusDays(1)), seed, mapOf("delivery" to d))
        assertTrue(next.key.startsWith("s:"))
        // After the cooldown it may come back
        assertEquals("delivery", Nudges.pick(NudgeSlot.AFTERNOON, c.copy(today = d.plusDays(3)), seed, mapOf("delivery" to d)).key)
    }

    @Test fun morningPrioritisesSalaryDayAndGoals() {
        val c = NudgeContext(today = LocalDate.of(2026, 12, 1), salaryJustArrived = true, goalsDue = listOf("🏠 Home" to 8_500_00L, "🎓 Riya" to 4_200_00L), hasIncome = true)
        val n = Nudges.pick(NudgeSlot.MORNING, c, seed, emptyMap())
        assertEquals("payfirst", n.key); assertTrue(n.body.contains("₹12,700"))
        val n2 = Nudges.pick(NudgeSlot.MORNING, c, seed, mapOf("payfirst" to c.today))
        assertEquals("goal", n2.key); assertTrue(n2.title.contains("Home"))
    }

    @Test fun eveningCelebratesANoSpendDay() {
        assertEquals("nospend", Nudges.pick(NudgeSlot.EVENING, NudgeContext(today = LocalDate.of(2026, 11, 20), spentToday = false), seed, emptyMap()).key)
    }

    @Test fun contextFromEntries() {
        val today = LocalDate.of(2026, 11, 20)
        val txns = listOf(
            Fx.tx("food_delivery", 400, today), Fx.tx("food_delivery", 350, today.minusDays(2)), Fx.tx("food_delivery", 390, today.minusDays(5)),
            Fx.tx("food_delivery", 999, today.minusDays(9)),   // outside this week
            Fx.tx("salary", 60_000, today.minusDays(1)),
        )
        val c = Nudges.context(txns, Fx.byId, today, emptyList(), emptyMap(), 0, 0, 0, 0, 0)
        assertEquals(3, c.deliveryOrdersThisWeek); assertEquals(1_140_00L, c.deliverySpendThisWeekPaise)
        assertTrue(c.salaryJustArrived); assertTrue(c.spentToday)
    }
}
