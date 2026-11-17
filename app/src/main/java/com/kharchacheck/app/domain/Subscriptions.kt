package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Txn
import java.time.LocalDate
import java.time.YearMonth

data class Subscription(val payee: String, val category: Category?, val monthlyPaise: Long, val monthsSeen: Int, val lastPaid: LocalDate) {
    val yearlyPaise: Long get() = monthlyPaise * 12
    val key: String get() = payee.trim().lowercase()
}

/** A subscription you marked as cancelled, but which charged you again afterwards. */
data class StillCharged(val payee: String, val cancelledOn: LocalDate, val charge: Txn)

object Subscriptions {
    private val SUB_KEYS = setOf("ott", "unused_subscriptions", "subscriptions", "gaming", "apps")

    /**
     * Regular small payments to the same payee (3 of the last 4 months, steady amount) in want / leak
     * categories, or in a subscription category. Rent, EMIs and SIPs are left out on purpose.
     */
    fun detect(all: List<Txn>, cats: Map<Long, Category>, today: LocalDate): List<Subscription> =
        AnalyticsEngine.regulars(all, YearMonth.from(today), today).mapNotNull { r ->
            val c = cats[r.categoryId]
            val looksSub = c?.key in SUB_KEYS || (c?.bucket in setOf(Bucket.WANT, Bucket.LEAK) && r.typicalPaise <= 5_000_00)
            if (!looksSub) null else Subscription(r.payee, c, r.typicalPaise, r.monthsSeen, r.lastDate)
        }.sortedByDescending { it.monthlyPaise }

    fun stillCharged(all: List<Txn>, cancelled: Map<String, LocalDate>): List<StillCharged> =
        all.mapNotNull { t ->
            val since = cancelled[t.payee.trim().lowercase()] ?: return@mapNotNull null
            if (t.date.isAfter(since)) StillCharged(t.payee, since, t) else null
        }.sortedByDescending { it.charge.epochMillis }

    fun yearlySaved(subs: List<Subscription>, cancelledKeys: Set<String>) = subs.filter { it.key in cancelledKeys }.sumOf { it.yearlyPaise }
}
