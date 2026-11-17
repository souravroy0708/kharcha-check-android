package com.kharchacheck.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WishDecision { WAITING, BOUGHT, SKIPPED }

/** Something you want to buy, parked for a cooling-off period instead of bought on impulse. */
data class WishItem(
    val id: Long,
    val name: String,
    val pricePaise: Long,
    val addedOn: LocalDate,
    val waitDays: Int,
    /** How often you expect to use it per month (for cost per use). 0 = not sure. */
    val usesPerMonth: Int = 0,
    /** How many of the four "do I need it?" questions pointed to a want. */
    val wantSignals: Int = 0,
    val decision: WishDecision = WishDecision.WAITING,
    val decidedOn: LocalDate? = null,
) {
    val readyOn: LocalDate get() = addedOn.plusDays(waitDays.toLong())
}

object Wishlist {
    /** Bigger price, longer wait: from 2 days for small things to the classic 30-day rule for big ones. */
    fun waitDays(pricePaise: Long): Int = when {
        pricePaise < 1_000_00 -> 2
        pricePaise < 5_000_00 -> 7
        pricePaise < 25_000_00 -> 14
        else -> 30
    }

    fun daysLeft(w: WishItem, today: LocalDate): Long = ChronoUnit.DAYS.between(today, w.readyOn).coerceAtLeast(0)
    fun ready(w: WishItem, today: LocalDate) = w.decision == WishDecision.WAITING && !today.isBefore(w.readyOn)

    /** "Your Money or Your Life": price in hours of your work. Null without an income. */
    fun lifeHours(pricePaise: Long, monthlyIncomePaise: Long, hoursPerMonth: Int = 176): Double? =
        if (monthlyIncomePaise <= 0 || hoursPerMonth <= 0) null else pricePaise.toDouble() / (monthlyIncomePaise.toDouble() / hoursPerMonth)

    fun hoursLabel(hours: Double): String = when {
        hours < 1 -> "${(hours * 60).toInt().coerceAtLeast(1)} minutes of your work"
        hours < 10 -> "%.1f hours of your work".format(hours)
        hours < 176 -> "${hours.toInt()} hours (≈ %.1f working days) of your work".format(hours / 8)
        else -> "%.1f months of your work".format(hours / 176)
    }

    /** Cost per use over a year: ₹4,000 shoes worn 8 times a month ≈ ₹42 a wear. */
    fun costPerUse(pricePaise: Long, usesPerMonth: Int): Long? = if (usesPerMonth <= 0) null else pricePaise / (usesPerMonth * 12L)

    fun savedBySkipping(items: List<WishItem>) = items.filter { it.decision == WishDecision.SKIPPED }.sumOf { it.pricePaise }

    /** The four questions shown before adding. Each "yes" here is a sign it's a want, not a need. */
    val QUESTIONS = listOf(
        "I already own something that does this job",
        "I probably won't use it every week",
        "I wouldn't buy it at full price, without the discount",
        "I could borrow, rent or buy it second-hand",
    )
}
