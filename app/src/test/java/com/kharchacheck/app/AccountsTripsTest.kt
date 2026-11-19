package com.kharchacheck.app

import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import com.kharchacheck.app.domain.Account
import com.kharchacheck.app.domain.AccountKind
import com.kharchacheck.app.domain.Balances
import com.kharchacheck.app.domain.BudgetRollover
import com.kharchacheck.app.domain.Trip
import com.kharchacheck.app.domain.Trips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AccountsTripsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(ist).toInstant().toEpochMilli()
    private fun tx(amt: Long, type: TxType, acc: Long?, to: Long? = null, cat: Long = 1, day: LocalDate = LocalDate.of(2026, 11, 10), tags: String = "") =
        Txn(amountPaise = amt, type = type, categoryId = cat, epochMillis = at(day), zoneId = "Asia/Kolkata", tags = tags, accountId = acc, toAccountId = to)

    @Test fun balancesFollowMoney() {
        val bank = Account(1, "HDFC", "🏦", AccountKind.BANK, 50_000_00)
        val card = Account(2, "SBI Card", "💳", AccountKind.CARD, -2_000_00)
        val cash = Account(3, "Cash", "💵", AccountKind.CASH, 0)
        val txns = listOf(
            tx(70_000_00, TxType.INCOME, 1),            // salary into bank
            tx(1_500_00, TxType.EXPENSE, 2),            // card spend
            tx(5_000_00, TxType.TRANSFER, 1, 3),        // ATM: bank → cash
            tx(3_500_00, TxType.TRANSFER, 1, 2),        // pay card bill from bank
            tx(10_000_00, TxType.INVEST, 1),            // SIP from bank
            tx(999_00, TxType.EXPENSE, null),           // not linked: changes nothing
        )
        val b = Balances.of(listOf(bank, card, cash), txns)
        assertEquals(101_500_00L, b[1])   // 50,000 + 70,000 − 5,000 − 3,500 − 10,000
        assertEquals(0L, b[2])            // −2,000 − 1,500 + 3,500
        assertEquals(5_000_00L, b[3])
        assertEquals(106_500_00L, Balances.net(listOf(bank, card, cash), b))
        assertEquals(0L, Balances.cardsOwed(listOf(bank, card, cash), b))
        val (inP, outP) = Balances.flow(3, txns, YearMonth.of(2026, 11))
        assertEquals(5_000_00L, inP); assertEquals(0L, outP)
    }

    @Test fun tripsByTag() {
        val trip = Trip(1, "Goa trip", "🏖️", "trip-goa-trip", 30_000_00, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 24))
        assertEquals("trip-goa-trip", Trips.tagFor("Goa trip!", emptySet()))
        assertEquals("trip-goa-trip-2", Trips.tagFor("Goa trip", setOf("trip-goa-trip")))
        val txns = listOf(
            tx(4_000_00, TxType.EXPENSE, null, tags = "trip-goa-trip"),
            tx(1_000_00, TxType.EXPENSE, null, tags = "office, trip-goa-trip"),
            tx(9_000_00, TxType.EXPENSE, null, tags = "office"),
            tx(5_000_00, TxType.INVEST, null, tags = "trip-goa-trip"),
        )
        assertEquals(5_000_00L, Trips.spent(trip, txns))
        assertEquals(5, trip.days)
        assertEquals(5_000_00L, Trips.perDayLeft(trip, 5_000_00, LocalDate.of(2026, 12, 20)))
        assertEquals(null, Trips.perDayLeft(trip, 0, LocalDate.of(2026, 12, 25)))
        assertEquals("office, trip-goa-trip", Trips.addTag("office", "trip-goa-trip"))
        assertEquals("office", Trips.removeTag("office, trip-goa-trip", "trip-goa-trip"))
        assertEquals(421_000L, Trips.toRupees(5_000, 8_420))   // $50 at ₹84.20 = ₹4,210
    }

    @Test fun carryOverOnlyFromItsStartMonth() {
        val nov = YearMonth.of(2026, 11)
        val txns = listOf(tx(3_000_00, TxType.EXPENSE, null, cat = 7, day = LocalDate.of(2026, 10, 5)))
        val limits = mapOf(7L to 5_000_00L)
        assertEquals(2_000_00L, BudgetRollover.carry(limits, mapOf(7L to 202610), txns, nov)[7])
        assertTrue(BudgetRollover.carry(limits, mapOf(7L to 202611), txns, nov).isEmpty())   // switched on this month
        assertTrue(BudgetRollover.carry(limits, mapOf(7L to 0), txns, nov).isEmpty())        // off
    }
}
