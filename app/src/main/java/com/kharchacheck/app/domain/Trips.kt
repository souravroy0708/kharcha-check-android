package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A trip or event with its own budget: Goa trip, a wedding, Diwali shopping, a house move.
 * Entries belong to it through a tag (e.g. "trip-goa"), so they still count in normal monthly reports too.
 * Optional foreign currency: amounts typed in, e.g., USD are converted at the user's own rate (no internet).
 */
data class Trip(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val tag: String,
    val budgetPaise: Long,
    val start: LocalDate,
    val end: LocalDate,
    /** "INR" or an ISO code like "USD", "AED", "THB". */
    val currency: String = "INR",
    /** ₹ paise for 1 unit of [currency]: ₹84.20 per USD → 8420. Ignored for INR. */
    val ratePaise: Long = 100,
) {
    val foreign: Boolean get() = currency != "INR" && ratePaise > 0
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
    fun isOn(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)
}

object Trips {
    /** "Goa trip 2026!" → "trip-goa-trip-2026", unique among [taken]. */
    fun tagFor(name: String, taken: Set<String>): String {
        val slug = name.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifBlank { "event" }
        val base = if (slug.startsWith("trip-")) slug else "trip-$slug"
        var tag = base; var n = 2
        while (tag in taken) { tag = "$base-$n"; n++ }
        return tag
    }

    fun tagsOf(tags: String): List<String> = tags.split(',', ' ', ';').map { it.trim().lowercase(Locale.ENGLISH) }.filter { it.isNotEmpty() }
    fun hasTag(t: Txn, tag: String) = tag.lowercase(Locale.ENGLISH) in tagsOf(t.tags)

    fun addTag(tags: String, tag: String): String = if (tag in tagsOf(tags)) tags else listOf(tags.trim().trimEnd(','), tag).filter { it.isNotBlank() }.joinToString(", ")
    fun removeTag(tags: String, tag: String): String = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.equals(tag, true) }.joinToString(", ")

    /** Spending on the trip: expenses tagged with it (investments, income and transfers don't count). */
    fun spent(trip: Trip, txns: List<Txn>): Long = txns.filter { it.type == TxType.EXPENSE && hasTag(it, trip.tag) }.sumOf { it.amountPaise }

    /** What's left to spend per remaining day (today included); null before the trip starts or after it ends. */
    fun perDayLeft(trip: Trip, spentPaise: Long, today: LocalDate): Long? {
        if (!trip.isOn(today) || trip.budgetPaise <= 0) return null
        val daysLeft = ChronoUnit.DAYS.between(today, trip.end) + 1
        return ((trip.budgetPaise - spentPaise).coerceAtLeast(0)) / daysLeft
    }

    /** 50.00 in USD at ₹84.20 → ₹4,210.00 (all in paise / cents). */
    fun toRupees(foreignCents: Long, ratePaise: Long): Long = Math.round(foreignCents * ratePaise / 100.0)
}
