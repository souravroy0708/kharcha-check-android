package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/**
 * "Carry over" budgets (envelope style): what you didn't spend last month is added to this month's limit.
 * Only one month carries, so a careful month helps the next one without piling up forever, and only from the
 * month the user switched it on (a brand-new budget never "carries" a full unused month).
 */
object BudgetRollover {
    /** 2026-11 → 202611. 0 means carry-over is off. */
    fun key(m: YearMonth) = m.year * 100 + m.monthValue

    fun carry(
        limits: Map<Long, Long>, rolloverFrom: Map<Long, Int>, txns: List<Txn>, thisMonth: YearMonth,
    ): Map<Long, Long> {
        val last = thisMonth.minusMonths(1)
        val out = HashMap<Long, Long>()
        for ((cat, from) in rolloverFrom) {
            if (from <= 0 || key(last) < from) continue
            val limit = limits[cat] ?: continue
            val spent = txns.filter { it.categoryId == cat && it.type == TxType.EXPENSE && it.month == last }.sumOf { it.amountPaise }
            val left = limit - spent
            if (left > 0) out[cat] = left
        }
        return out
    }

    /** This week's share of a monthly limit, and what was spent since Monday (this month only). */
    fun weekly(limitPaise: Long, categoryId: Long, txns: List<Txn>, today: LocalDate): Pair<Long, Long> {
        val share = limitPaise * 7 / today.lengthOfMonth()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { if (it.month != today.month) today.withDayOfMonth(1) else it }
        val spent = txns.filter { it.categoryId == categoryId && it.type == TxType.EXPENSE && !it.date.isBefore(monday) && !it.date.isAfter(today) }.sumOf { it.amountPaise }
        return share to spent
    }
}
