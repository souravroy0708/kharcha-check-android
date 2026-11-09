package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.PayMode
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth

/** "Rent ₹15,000 on the 1st, every month." Created from an entry the user ticks "Repeat every month" on. */
data class RecurringRule(
    val id: Long = 0,
    val amountPaise: Long,
    val type: TxType,
    val categoryId: Long,
    val mode: PayMode,
    val payee: String,
    val note: String,
    val dayOfMonth: Int,
    /** Last month an entry was created for (the month of the original entry, initially). */
    val lastMonth: YearMonth,
    val active: Boolean = true,
)

object RecurringPlanner {
    /** Day in [month] the rule falls on; the 31st becomes the 30th or 28th in shorter months. */
    fun dateIn(rule: RecurringRule, month: YearMonth): LocalDate = month.atDay(rule.dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    /**
     * Months that still need an entry, oldest first. Catches up if the phone was off for a while,
     * but never more than 3 months back, so a long gap doesn't flood the inbox.
     */
    fun dueMonths(rule: RecurringRule, today: LocalDate): List<YearMonth> {
        if (!rule.active) return emptyList()
        val current = YearMonth.from(today)
        val earliest = current.minusMonths(2)
        val out = mutableListOf<YearMonth>()
        var m = rule.lastMonth.plusMonths(1)
        if (m < earliest) m = earliest
        while (m <= current) {
            if (!dateIn(rule, m).isAfter(today)) out += m
            m = m.plusMonths(1)
        }
        return out
    }
}
