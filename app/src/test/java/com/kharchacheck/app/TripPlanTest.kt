package com.kharchacheck.app

import com.kharchacheck.app.domain.DiaryEntry
import com.kharchacheck.app.domain.Trip
import com.kharchacheck.app.domain.TripItem
import com.kharchacheck.app.domain.TripItemKind
import com.kharchacheck.app.domain.TripPerson
import com.kharchacheck.app.domain.TripPlace
import com.kharchacheck.app.domain.TripPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TripPlanTest {
    private val trip = Trip(1, "Goa trip", "🏖️", "trip-goa", 40_000_00, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 24))
    private val z = ZoneId.of("Asia/Kolkata")
    private fun t(d: Int, h: Int) = LocalDate.of(2026, 12, d).atTime(h, 0).atZone(z).toInstant().toEpochMilli()

    private val items = listOf(
        TripItem(1, 1, TripItemKind.TRAIN, "12779 Goa Express", "Hyderabad", "Madgaon", t(20, 6), costPaise = 3_200_00, paid = true, txnId = 99),
        TripItem(2, 1, TripItemKind.TRAIN, "Return train", "Madgaon", "Hyderabad", t(24, 18), costPaise = 3_400_00, paid = true),
        TripItem(3, 1, TripItemKind.STAY, "Beach resort", costPaise = 12_000_00),
    )

    @Test fun noDoubleCounting() {
        // Item 1 was recorded (txn 99 exists, ₹3,200 is inside the ₹5,000 tagged spend); 2 and 3 aren't yet.
        val s = TripPlan.summary(items, people = 4, spentTagged = 5_000_00, txnIds = setOf(99L), now = t(19, 0))
        assertEquals(18_600_00L, s.itemsTotal)
        assertEquals(15_400_00L, s.notRecorded)
        assertEquals(20_400_00L, s.expected)      // 5,000 spent + 15,400 still to record
        assertEquals(5_100_00L, s.perPerson)
        assertEquals(1L, s.next?.id)
        assertEquals(TripItemKind.STAY, s.byKind.first().first)
    }

    @Test fun deletedExpenseMeansNotRecorded() {
        val s = TripPlan.summary(items, people = 1, spentTagged = 0, txnIds = emptySet(), now = t(19, 0))
        assertEquals(18_600_00L, s.expected)
        assertEquals(null, s.perPerson)
    }

    @Test fun statusAndOrder() {
        assertEquals("Starts in 5 days", TripPlan.status(trip, LocalDate.of(2026, 12, 15)))
        assertEquals("Day 3 of 5", TripPlan.status(trip, LocalDate.of(2026, 12, 22)))
        assertEquals("Ended yesterday", TripPlan.status(trip, LocalDate.of(2026, 12, 25)))
        assertEquals(listOf(1L, 2L, 3L), TripPlan.ordered(items.reversed()).map { it.id })
    }

    @Test fun keepsakeText() {
        val s = TripPlan.summary(items, 2, 0, emptySet(), t(19, 0))
        val text = TripPlan.shareText(trip, s, items, listOf(TripPlace(1, 1, "Baga beach", LocalDate.of(2026, 12, 21))),
            listOf(TripPerson(1, 1, "Sourav"), TripPerson(2, 1, "Riya")),
            listOf(DiaryEntry(1, 1, LocalDate.of(2026, 12, 21), "🌅", "Sunset at Baga", "Best evening of the year.")), z)
        assertTrue(text.contains("With: Sourav, Riya"))
        assertTrue(text.contains("Hyderabad → Madgaon"))
        assertTrue(text.contains("Sunset at Baga"))
    }
}

class TripCoupleTest {
    private val people = listOf(com.kharchacheck.app.domain.TripPerson(1, 1, "Sourav"), com.kharchacheck.app.domain.TripPerson(2, 1, "Riya"))

    @Test fun coupleSettlesUp() {
        val items = listOf(
            TripItem(1, 1, TripItemKind.TRAIN, "Train", costPaise = 6_600_00, paidBy = "Sourav"),
            TripItem(2, 1, TripItemKind.STAY, "Resort", costPaise = 12_000_00, paidBy = "Riya"),
            TripItem(3, 1, TripItemKind.FOOD, "Dinner", costPaise = 2_401_00, paidBy = "Sourav"),
            TripItem(4, 1, TripItemKind.FOOD, "Chai", costPaise = 40_00),           // no payer: not in settle-up
        )
        val (balances, transfers) = TripPlan.settle(items, people)
        assertEquals(21_001_00L, balances.sumOf { it.share })                       // shares add up exactly
        assertEquals(listOf(com.kharchacheck.app.domain.TripPlan.Transfer("Sourav", "Riya", 1_499_50)), transfers)
    }

    @Test fun nothingToSettleForOnePerson() {
        assertTrue(TripPlan.settle(listOf(TripItem(1, 1, TripItemKind.FOOD, "x", costPaise = 100, paidBy = "Sourav")), people.take(1)).second.isEmpty())
    }

    @Test fun spendByDayAndOnThisDay() {
        val trip = Trip(1, "Goa", "🏖️", "trip-goa", 0, LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 22))
        val z = ZoneId.of("Asia/Kolkata")
        val at = { d: Int -> LocalDate.of(2025, 12, d).atTime(13, 0).atZone(z).toInstant().toEpochMilli() }
        val items = listOf(TripItem(1, 1, TripItemKind.FOOD, "Lunch", startAt = at(21), costPaise = 900_00),
            TripItem(2, 1, TripItemKind.TRAIN, "Train", startAt = at(19), costPaise = 3_000_00))   // day before trip → day 1
        val days = TripPlan.byDay(trip, items, listOf(LocalDate.of(2025, 12, 21) to 100_00L), emptySet(), z)
        assertEquals(listOf(3_000_00L, 1_000_00L, 0L), days.map { it.second })
        val diary = listOf(DiaryEntry(1, 1, LocalDate.of(2025, 12, 21), title = "Sunset"), DiaryEntry(2, 1, LocalDate.of(2026, 12, 21), title = "Today"))
        assertEquals(listOf(1L), TripPlan.onThisDay(diary, LocalDate.of(2026, 12, 21)).map { it.id })
        assertEquals(TripItemKind.FOOD, TripPlan.kindFor("chai_coffee"))
    }
}
