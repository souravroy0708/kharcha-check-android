package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Everything about one trip, beyond its budget: bookings and cost line items (several trains, flights, hotels…),
 * where you're going, who's going, and a day-by-day diary of memorable moments with photos.
 * All of it is stored only in the phone's encrypted database.
 */
enum class TripItemKind(val label: String, val emoji: String, /** Expense category used when recorded. */ val categoryKey: String, val hasRoute: Boolean) {
    TRAIN("Train", "🚆", "travel", true),
    FLIGHT("Flight", "✈️", "travel", true),
    BUS("Bus", "🚌", "travel", true),
    CAB("Cab / car", "🚕", "cab", true),
    STAY("Hotel / stay", "🏨", "travel", false),
    FOOD("Food", "🍽️", "dining_out", false),
    ACTIVITY("Tickets / activity", "🎟️", "entertainment", false),
    SHOPPING("Shopping", "🛍️", "shopping", false),
    OTHER("Other", "📌", "travel", false);

    /** Label for the reference number field. */
    val refLabel: String get() = when (this) {
        TRAIN -> "PNR"; FLIGHT -> "PNR / booking ref"; STAY -> "Booking ID"; BUS -> "Ticket no."; else -> "Booking / ref no."
    }
    /** Label for the seat / class / room field. */
    val detailsLabel: String get() = when (this) {
        TRAIN -> "Train no., class, coach & seats (e.g. 12627 · 3A · B2 21,22)"; FLIGHT -> "Flight no. & seats (e.g. 6E 512 · 14A)"
        BUS -> "Operator & seats"; STAY -> "Room, check-in time"; CAB -> "Driver / vehicle no."; else -> "Details"
    }
}

data class TripItem(
    val id: Long = 0,
    val tripId: Long,
    val kind: TripItemKind,
    val title: String,
    val from: String = "",
    val to: String = "",
    /** Departure / check-in / start, epoch millis; null if not set. */
    val startAt: Long? = null,
    /** Arrival / check-out / end; null if not set. */
    val endAt: Long? = null,
    val refNo: String = "",
    val details: String = "",
    /** Who this is for, in the user's words ("All 4", "Sourav, Riya"). */
    val travellers: String = "",
    val costPaise: Long = 0,
    /** Booked and paid (vs only planned). */
    val paid: Boolean = false,
    /** The expense entry this was recorded as, if any. */
    val txnId: Long? = null,
    val note: String = "",
    /** Who paid (a name from the trip's people); "" = not set. Used to settle up between travellers. */
    val paidBy: String = "",
)

data class TripPlace(
    val id: Long = 0, val tripId: Long, val name: String, val day: LocalDate? = null,
    val note: String = "", val visited: Boolean = false, val sortOrder: Int = 0,
)

data class TripPerson(val id: Long = 0, val tripId: Long, val name: String, val note: String = "")

data class DiaryEntry(
    val id: Long = 0, val tripId: Long, val day: LocalDate, val mood: String = "😊", val title: String = "",
    val body: String = "", val place: String = "", val photoIds: List<Long> = emptyList(), val createdAt: Long = System.currentTimeMillis(),
)

object TripPlan {
    /** One person's position after the trip: what they paid, their equal share, and the difference. */
    data class Balance(val name: String, val paid: Long, val share: Long) { val net: Long get() = paid - share }
    data class Transfer(val from: String, val to: String, val amountPaise: Long)

    /**
     * Settle up (couples and groups): every item with a payer is shared equally by everyone on the trip.
     * Returns each person's balance and the fewest payments that square it ("Riya pays Sourav ₹1,850").
     */
    fun settle(items: List<TripItem>, people: List<TripPerson>): Pair<List<Balance>, List<Transfer>> {
        val names = people.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.size < 2) return emptyList<Balance>() to emptyList()
        val counted = items.filter { it.costPaise > 0 && it.paidBy.trim() in names }
        val total = counted.sumOf { it.costPaise }
        val base = total / names.size
        var extra = total - base * names.size // spread the odd paise so shares add up exactly
        val balances = names.map { n ->
            val share = base + if (extra > 0) { extra--; 1 } else 0
            Balance(n, counted.filter { it.paidBy.trim() == n }.sumOf { it.costPaise }, share)
        }
        // Greedy: the biggest debtor pays the biggest creditor until everyone is square.
        val owe = balances.filter { it.net < 0 }.map { it.name to -it.net }.sortedByDescending { it.second }.toMutableList()
        val get = balances.filter { it.net > 0 }.map { it.name to it.net }.sortedByDescending { it.second }.toMutableList()
        val transfers = mutableListOf<Transfer>()
        while (owe.isNotEmpty() && get.isNotEmpty()) {
            val (d, dAmt) = owe[0]; val (c, cAmt) = get[0]
            val x = minOf(dAmt, cAmt)
            if (x > 0) transfers += Transfer(d, c, x)
            if (dAmt - x == 0L) owe.removeAt(0) else owe[0] = d to (dAmt - x)
            if (cAmt - x == 0L) get.removeAt(0) else get[0] = c to (cAmt - x)
        }
        return balances to transfers
    }

    /**
     * Spend per day of the trip: line items by their date (if not also recorded as an expense) plus expenses
     * tagged to the trip. Days outside the trip are folded into its first / last day.
     */
    fun byDay(trip: Trip, items: List<TripItem>, tagged: List<Pair<LocalDate, Long>>, txnIds: Set<Long>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Long>> {
        val days = (0 until trip.days.coerceAtMost(60)).map { trip.start.plusDays(it.toLong()) }
        fun clamp(d: LocalDate) = if (d.isBefore(trip.start)) trip.start else if (d.isAfter(trip.end)) trip.end else d
        val m = HashMap<LocalDate, Long>()
        items.filter { it.costPaise > 0 && !isRecorded(it, txnIds) && it.startAt != null }.forEach { i ->
            val d = clamp(Instant.ofEpochMilli(i.startAt!!).atZone(zone).toLocalDate()); m[d] = (m[d] ?: 0L) + i.costPaise
        }
        tagged.forEach { (day, amt) -> val d = clamp(day); m[d] = (m[d] ?: 0L) + amt }
        return days.map { it to (m[it] ?: 0L) }
    }

    /** Spoken or typed trip spend ("240 chai at the beach") → the kind of line item. */
    fun kindFor(categoryKey: String?): TripItemKind = when (categoryKey) {
        "food_delivery", "dining_out", "chai_coffee", "junk_food", "cold_drinks", "sweets_desserts", "groceries", "vegetables", "alcohol" -> TripItemKind.FOOD
        "cab" -> TripItemKind.CAB
        "commute", "fuel", "vehicle" -> TripItemKind.CAB
        "shopping", "shopping_clothes", "gifts", "gadgets", "personal_care" -> TripItemKind.SHOPPING
        "entertainment", "festivals" -> TripItemKind.ACTIVITY
        "travel" -> TripItemKind.OTHER
        else -> TripItemKind.OTHER
    }

    /** "On this day": diary entries from the same date in earlier years, newest first. */
    fun onThisDay(diary: List<DiaryEntry>, today: LocalDate): List<DiaryEntry> =
        diary.filter { it.day.monthValue == today.monthValue && it.day.dayOfMonth == today.dayOfMonth && it.day.year < today.year }
            .sortedByDescending { it.day }

    data class Summary(
        /** Σ of all line items (planned + booked). */
        val itemsTotal: Long,
        /** Line items not yet recorded as an expense entry. */
        val notRecorded: Long,
        /** What's already in expenses with the trip tag (includes recorded items). */
        val spent: Long,
        /** Honest total: spent so far + items still to be recorded. Compared with the budget. */
        val expected: Long,
        val byKind: List<Pair<TripItemKind, Long>>,
        /** expected ÷ people, when 2+ people are going. */
        val perPerson: Long?,
        val next: TripItem?,
    )

    /** An item is "recorded" only if its expense entry still exists (the user may have deleted it). */
    fun isRecorded(item: TripItem, txnIds: Set<Long>) = item.txnId != null && item.txnId in txnIds

    fun summary(items: List<TripItem>, people: Int, spentTagged: Long, txnIds: Set<Long>, now: Long): Summary {
        val total = items.sumOf { it.costPaise }
        val notRecorded = items.filter { !isRecorded(it, txnIds) }.sumOf { it.costPaise }
        val expected = spentTagged + notRecorded
        val byKind = items.groupBy { it.kind }.map { (k, l) -> k to l.sumOf { it.costPaise } }.filter { it.second > 0 }.sortedByDescending { it.second }
        val next = items.filter { (it.startAt ?: 0L) >= now }.minByOrNull { it.startAt ?: Long.MAX_VALUE }
        return Summary(total, notRecorded, spentTagged, expected, byKind, if (people >= 2) expected / people else null, next)
    }

    /** "Starts in 12 days", "Day 3 of 5", "Ended 4 days ago". */
    fun status(trip: Trip, today: LocalDate): String = when {
        today.isBefore(trip.start) -> ChronoUnit.DAYS.between(today, trip.start).let { if (it == 1L) "Starts tomorrow" else "Starts in $it days" }
        trip.isOn(today) -> "Day ${ChronoUnit.DAYS.between(trip.start, today) + 1} of ${trip.days}"
        else -> ChronoUnit.DAYS.between(trip.end, today).let { if (it == 0L) "Ended today" else if (it == 1L) "Ended yesterday" else "Ended $it days ago" }
    }

    /** Items in the order they happen; undated ones last. */
    fun ordered(items: List<TripItem>) = items.sortedWith(compareBy<TripItem> { it.startAt == null }.thenBy { it.startAt ?: 0L }.thenBy { it.id })

    /** A plain-text keepsake of the whole trip, to share or save anywhere. */
    fun shareText(trip: Trip, s: Summary, items: List<TripItem>, places: List<TripPlace>, people: List<TripPerson>, diary: List<DiaryEntry>, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val dt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
        fun at(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(dt) }.orEmpty()
        return buildString {
            appendLine("${trip.emoji} ${trip.name}")
            appendLine("${trip.start.format(d)} – ${trip.end.format(d)} (${trip.days} days)")
            if (people.isNotEmpty()) appendLine("With: " + people.joinToString(", ") { it.name })
            if (places.isNotEmpty()) appendLine("Places: " + places.sortedWith(compareBy({ it.day ?: LocalDate.MAX }, { it.sortOrder })).joinToString(" → ") { it.name })
            appendLine()
            appendLine("Cost: ${Money.format(s.expected)}" + (if (trip.budgetPaise > 0) " of ${Money.format(trip.budgetPaise)} budget" else "") +
                (s.perPerson?.let { " · about ${Money.format(it)} per person" } ?: ""))
            if (items.isNotEmpty()) {
                appendLine()
                appendLine("Bookings & costs")
                ordered(items).forEach { i ->
                    val route = if (i.from.isNotBlank() || i.to.isNotBlank()) " ${i.from} → ${i.to}" else ""
                    appendLine("• ${i.kind.emoji} ${i.title}$route ${at(i.startAt)} · ${Money.format(i.costPaise)}".replace("  ", " ").trim())
                }
            }
            if (diary.isNotEmpty()) {
                appendLine()
                appendLine("Diary")
                diary.sortedWith(compareBy({ it.day }, { it.createdAt })).forEach { e ->
                    appendLine("${e.day.format(d)} ${e.mood} ${e.title}".trim())
                    if (e.place.isNotBlank()) appendLine("📍 ${e.place}")
                    if (e.body.isNotBlank()) appendLine(e.body)
                    appendLine()
                }
            }
            append("Kept with Kharcha Check")
        }
    }
}
