package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Money

/** Budget warnings at 80% and 100%: shown once per crossing, never nagging on every entry after. */
object BudgetAlerts {
    /** The threshold (80 or 100) newly crossed by going from [before] to [after], or null. */
    fun crossing(before: Long, after: Long, limit: Long): Int? = when {
        limit <= 0 -> null
        before < limit && after >= limit -> 100
        before * 10 < limit * 8 && after * 10 >= limit * 8 -> 80
        else -> null
    }

    /** Highest threshold already reached, for the daily check (which may see several entries at once). */
    fun level(spent: Long, limit: Long): Int? = when {
        limit <= 0 -> null
        spent >= limit -> 100
        spent * 10 >= limit * 8 -> 80
        else -> null
    }

    fun message(name: String, spent: Long, limit: Long, level: Int): String =
        if (level >= 100) "🚨 Over your $name budget by ${Money.format(spent - limit)} (${Money.format(spent)} of ${Money.format(limit)})"
        else "⚠️ $name: ${Money.format(spent)} of ${Money.format(limit)} used (${spent * 100 / limit}%)"
}
