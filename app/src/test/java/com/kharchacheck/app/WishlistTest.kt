package com.kharchacheck.app

import com.kharchacheck.app.domain.WishDecision
import com.kharchacheck.app.domain.WishItem
import com.kharchacheck.app.domain.Wishlist
import com.kharchacheck.app.domain.WisdomLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WishlistTest {
    @Test fun coolingOffGrowsWithPrice() {
        assertEquals(listOf(2, 7, 14, 30), listOf(499_00L, 2_499_00L, 15_000_00L, 79_999_00L).map(Wishlist::waitDays))
        val w = WishItem(1, "Earbuds", 2_499_00, LocalDate.of(2026, 11, 1), 7)
        assertEquals(LocalDate.of(2026, 11, 8), w.readyOn)
        assertFalse(Wishlist.ready(w, LocalDate.of(2026, 11, 7)))
        assertTrue(Wishlist.ready(w, LocalDate.of(2026, 11, 8)))
        assertEquals(3L, Wishlist.daysLeft(w, LocalDate.of(2026, 11, 5)))
    }

    @Test fun hoursOfLifeAndCostPerUse() {
        // ₹44,000 a month over 176 hours = ₹250 an hour → ₹2,499 ≈ 10 hours
        val h = Wishlist.lifeHours(2_499_00, 44_000_00)!!
        assertEquals(9.996, h, 0.01)
        assertEquals("10.0 hours of your work", Wishlist.hoursLabel(h))
        assertTrue(Wishlist.hoursLabel(24.0).contains("3.0 working days"))
        assertEquals("30 minutes of your work", Wishlist.hoursLabel(0.5))
        assertEquals(41_66L, Wishlist.costPerUse(4_000_00, 8))
        assertEquals(null, Wishlist.lifeHours(100_00, 0))
    }

    @Test fun skippedMoneyIsCounted() {
        val d = LocalDate.of(2026, 11, 1)
        val items = listOf(WishItem(1, "A", 1_000_00, d, 7, decision = WishDecision.SKIPPED), WishItem(2, "B", 5_000_00, d, 14, decision = WishDecision.BOUGHT),
            WishItem(3, "C", 700_00, d, 2, decision = WishDecision.SKIPPED))
        assertEquals(1_700_00L, Wishlist.savedBySkipping(items))
    }
}

class WisdomLibraryTest {
    @Test fun everyCardIsSourcedActionableAndParaphrased() {
        val cards = WisdomLibrary.cards
        assertTrue(cards.size >= 25)
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
        cards.forEach { c ->
            assertTrue(c.id, Regex("""\(\d{4}\)|\(1700s\)|framework""").containsMatchIn(c.source))   // every source names its year (or is a standing rule)
            assertTrue(c.id, c.action.length in 20..200 && c.idea.length in 40..320)
            // Only the public-domain Franklin line may be a direct quote
            if (c.id != "franklin") assertFalse(c.id, c.idea.trimStart().startsWith("\""))
        }
        assertEquals(cards[3], WisdomLibrary.forDay(3)); assertEquals(cards[0], WisdomLibrary.forDay(cards.size.toLong()))
    }
}
