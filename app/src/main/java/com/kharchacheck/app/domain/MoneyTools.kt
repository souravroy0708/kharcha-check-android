package com.kharchacheck.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- Split & dues

enum class DueDirection(val label: String) { THEY_OWE("Owes you"), I_OWE("You owe") }

data class Due(
    val id: Long = 0,
    val person: String,
    val amountPaise: Long,
    val direction: DueDirection,
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val settled: Boolean = false,
    val txnId: Long? = null,
)

/** Net per person: positive = they owe you, negative = you owe them. */
data class PersonBalance(val person: String, val netPaise: Long, val open: Int)

object Dues {
    fun balances(dues: List<Due>): List<PersonBalance> = dues.filter { !it.settled }
        .groupBy { it.person.trim().lowercase() }
        .map { (_, l) ->
            PersonBalance(l.first().person.trim(), l.sumOf { if (it.direction == DueDirection.THEY_OWE) it.amountPaise else -it.amountPaise }, l.size)
        }
        .filter { it.netPaise != 0L }
        .sortedByDescending { kotlin.math.abs(it.netPaise) }

    fun toCollect(dues: List<Due>) = balances(dues).filter { it.netPaise > 0 }.sumOf { it.netPaise }
    fun toPay(dues: List<Due>) = -balances(dues).filter { it.netPaise < 0 }.sumOf { it.netPaise }

    /**
     * Equal split of [totalPaise] between you and [others]. Every share is whole paise and they add up exactly;
     * any leftover paise from rounding is yours. Returns (your share, each other person's share).
     */
    fun splitEqual(totalPaise: Long, others: Int): Pair<Long, Long> {
        require(others >= 1)
        val people = others + 1
        val each = totalPaise / people
        return (totalPaise - each * others) to each
    }

    /** "Rahul, Priya and Aman" → [Rahul, Priya, Aman]. */
    fun parseNames(text: String): List<String> = text.split(',', ';', '\n', '&')
        .flatMap { it.split(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE)) }
        .map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }.take(20)
}

// ---------------------------------------------------------------- Bills & due dates

data class Bill(
    val id: Long = 0,
    val name: String,
    val emoji: String = "🧾",
    val dueDay: Int,
    val amountPaise: Long = 0,
    val remindDaysBefore: Int = 3,
    val paidMonth: YearMonth? = null,
    val active: Boolean = true,
)

data class BillStatus(val bill: Bill, val dueDate: LocalDate, val daysLeft: Long, val overdue: Boolean)

object Bills {
    fun dueIn(bill: Bill, month: YearMonth): LocalDate = month.atDay(bill.dueDay.coerceIn(1, month.lengthOfMonth()))

    /**
     * The next unpaid due date. If this month's bill isn't marked paid it stays the "next" one even after
     * the date passes, and shows as overdue, which is exactly when a reminder matters most.
     */
    fun status(bill: Bill, today: LocalDate): BillStatus {
        val now = YearMonth.from(today)
        val month = if (bill.paidMonth != null && bill.paidMonth >= now) now.plusMonths(1) else now
        val due = dueIn(bill, month)
        val days = ChronoUnit.DAYS.between(today, due)
        return BillStatus(bill, due, days, days < 0)
    }

    fun upcoming(bills: List<Bill>, today: LocalDate, withinDays: Long = 7): List<BillStatus> =
        bills.filter { it.active }.map { status(it, today) }.filter { it.daysLeft <= withinDays }.sortedBy { it.daysLeft }

    /** Bills to notify about today: N days before, the day before, on the day, and once when 1 day overdue. */
    fun remindToday(bills: List<Bill>, today: LocalDate): List<BillStatus> =
        bills.filter { it.active }.map { status(it, today) }.filter { it.daysLeft in setOf(it.bill.remindDaysBefore.toLong(), 1L, 0L, -1L) }
}
