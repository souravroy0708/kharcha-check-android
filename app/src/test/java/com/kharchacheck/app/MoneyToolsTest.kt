package com.kharchacheck.app

import com.kharchacheck.app.domain.Bill
import com.kharchacheck.app.domain.Bills
import com.kharchacheck.app.domain.Due
import com.kharchacheck.app.domain.DueDirection
import com.kharchacheck.app.domain.Dues
import com.kharchacheck.app.domain.Wrapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DuesTest {
    @Test fun splitAddsUpExactly() {
        val (mine, each) = Dues.splitEqual(1_000_01, 2) // ₹1000.01 between 3 people
        assertEquals(33_333L, each)
        assertEquals(1_000_01L, mine + each * 2)
        assertEquals(1_000_01L - 66_666, mine)
    }

    @Test fun balancesNetPerPersonCaseInsensitive() {
        val dues = listOf(
            Due(person = "Rahul", amountPaise = 500_00, direction = DueDirection.THEY_OWE),
            Due(person = "rahul ", amountPaise = 200_00, direction = DueDirection.I_OWE),
            Due(person = "Priya", amountPaise = 300_00, direction = DueDirection.I_OWE),
            Due(person = "Aman", amountPaise = 100_00, direction = DueDirection.THEY_OWE, settled = true),
        )
        val b = Dues.balances(dues)
        assertEquals(listOf("Rahul" to 300_00L, "Priya" to -300_00L), b.map { it.person to it.netPaise })
        assertEquals(300_00L, Dues.toCollect(dues))
        assertEquals(300_00L, Dues.toPay(dues))
    }

    @Test fun parsesNames() = assertEquals(listOf("Rahul", "Priya", "Aman"), Dues.parseNames("Rahul, Priya and Aman, rahul"))
}

class BillsTest {
    private val card = Bill(name = "HDFC card", dueDay = 15, remindDaysBefore = 3)

    @Test fun nextDueAndOverdue() {
        assertEquals(LocalDate.of(2026, 11, 15), Bills.status(card, LocalDate.of(2026, 11, 8)).dueDate)
        val late = Bills.status(card, LocalDate.of(2026, 11, 17))
        assertTrue(late.overdue); assertEquals(-2L, late.daysLeft)
        val paid = Bills.status(card.copy(paidMonth = YearMonth.of(2026, 11)), LocalDate.of(2026, 11, 17))
        assertEquals(LocalDate.of(2026, 12, 15), paid.dueDate)
    }

    @Test fun remindsOnTheRightDaysOnly() {
        fun on(d: Int) = Bills.remindToday(listOf(card), LocalDate.of(2026, 11, d)).size
        assertEquals(listOf(0, 1, 0, 1, 1, 1, 0), listOf(11, 12, 13, 14, 15, 16, 17).map(::on))
    }

    @Test fun day31InFebruary() = assertEquals(LocalDate.of(2027, 2, 28), Bills.dueIn(Bill(name = "Rent", dueDay = 31), YearMonth.of(2027, 2)))
}

class WrappedTest {
    @Test fun storyAndPersona() {
        val oct = YearMonth.of(2026, 10)
        val txns = listOf(
            Fx.tx("salary", 50_000, oct.atDay(1)),
            Fx.tx("food_delivery", 400, oct.atDay(2)).copy(payee = "Swiggy"),
            Fx.tx("food_delivery", 600, oct.atDay(9)).copy(payee = "Swiggy"),
            Fx.tx("food_delivery", 3_000, oct.atDay(9)).copy(payee = "Zomato"),
            Fx.tx("groceries", 2_000, oct.atDay(5)),
        )
        val w = Wrapped.build("October 2026", listOf(oct), txns, Fx.byId, setOf(oct.atDay(20).toEpochDay()))
        assertEquals(6_000_00L, w.spendPaise)
        assertEquals("food_delivery", w.topCategory?.key)
        assertEquals(66, w.topCategorySharePct)
        assertEquals("Swiggy", w.topPayee)
        assertEquals(LocalDate.of(2026, 10, 9), w.biggestDay)
        assertEquals(DayOfWeek.FRIDAY, w.favouriteDay)
        assertEquals(1, w.noSpendDays)
        assertEquals("The Saver", w.persona.title) // 88% saved beats foodie
        assertEquals("The Foodie", Wrapped.build("Oct", listOf(oct), txns.drop(1), Fx.byId, emptySet()).persona.title)
    }
}
