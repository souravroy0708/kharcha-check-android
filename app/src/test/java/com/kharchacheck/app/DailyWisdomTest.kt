package com.kharchacheck.app

import com.kharchacheck.app.domain.DailyWisdom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyWisdomTest {
    @Test fun exactlyHundredUniqueCards() {
        assertEquals(100, DailyWisdom.all.size)
        assertEquals(100, DailyWisdom.all.map { it.line }.toSet().size)
        assertEquals(100, DailyWisdom.all.map { it.doToday }.toSet().size)
        assertEquals(100, DailyWisdom.all.map { it.id }.toSet().size)
    }

    @Test fun noRepeatWithinAnyHundredDayCycle() {
        val seed = 424242L
        val start = LocalDate.of(2026, 11, 8)
        // Check a long window: every run of 100 days aligned to a cycle has no repeats,
        // and consecutive days are never the same card, including across cycle boundaries.
        val ids = (0L until 400L).map { DailyWisdom.forDate(start.plusDays(it), seed).id }
        for (i in 1 until ids.size) assertTrue("same card two days in a row at $i", ids[i] != ids[i - 1])
        val firstCycleStart = (0 until 100).first { Math.floorMod(start.plusDays(it.toLong()).toEpochDay(), 100L) == 0L }
        for (c in 0 until 3) {
            val window = ids.subList(firstCycleStart + c * 100, firstCycleStart + c * 100 + 100)
            assertEquals("cycle $c has repeats", 100, window.toSet().size)
        }
    }

    @Test fun differentInstallsSeeDifferentOrders() {
        val d = LocalDate.of(2026, 11, 8)
        val a = (0L until 30L).map { DailyWisdom.forDate(d.plusDays(it), 1L).id }
        val b = (0L until 30L).map { DailyWisdom.forDate(d.plusDays(it), 2L).id }
        assertTrue(a != b)
        assertEquals(DailyWisdom.forDate(d, 7L), DailyWisdom.forDate(d, 7L))
    }
}
