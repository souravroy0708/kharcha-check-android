package com.kharchacheck.app.domain

/** What the score is computed from (prepared from the user's entries and settings). Null = not known yet. */
data class HealthInputs(
    val avgIncomePaise: Long?,          // average monthly income, last 3 full months (or salary from settings)
    val avgSpendPaise: Long,            // average monthly spending, same months
    val avgNeedsPaise: Long,            // average essential spending
    val leaksSharePct: Int?,            // leaks as % of spending, last full month
    val emiPaise: Long,                 // EMIs last month
    val costlyDebt: Boolean,            // card interest or late fees in the last 90 days
    val emergencySavedPaise: Long,
    val healthInsured: Boolean,
    val termInsured: Boolean,
    val dependents: Boolean,
    val investMonths: Int,              // of the last 3 full months, how many had a Save & Invest entry
)

enum class Pillar(val label: String, val emoji: String, val weight: Int) {
    SAVINGS("Savings rate", "💰", 25), EMERGENCY("Emergency fund", "🛟", 20), PROTECTION("Insurance", "🛡️", 15),
    DEBT("Debt load", "⚖️", 15), LEAKS("Leak control", "🕳️", 15), INVESTING("Investing habit", "📈", 10),
}

/** [route] tells the UI which screen fixes this pillar. */
data class PillarScore(val pillar: Pillar, val score: Int?, val detail: String, val action: String, val route: String)

data class HealthScore(val score: Int?, val pillars: List<PillarScore>) {
    val grade: String get() = when {
        score == null -> "Add more data"
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Needs work"
    }
    /** The single most useful next step: the weakest pillar, weighted by importance. */
    val nextStep: PillarScore? get() = pillars.filter { it.score != null && it.score < 80 }.maxByOrNull { (100 - it.score!!) * it.pillar.weight }
}

/**
 * A 0–100 money health score from six widely used personal-finance yardsticks. Pillars without enough data are
 * left out and the rest re-weighted, so a new user isn't punished for an empty app.
 */
object HealthScorer {
    /** Straight-line interpolation through (x, score) points; clamps at the ends. */
    internal fun curve(x: Double, vararg pts: Pair<Double, Int>): Int {
        if (x <= pts.first().first) return pts.first().second
        if (x >= pts.last().first) return pts.last().second
        for (i in 1 until pts.size) {
            val (x0, y0) = pts[i - 1]; val (x1, y1) = pts[i]
            if (x <= x1) return (y0 + (y1 - y0) * (x - x0) / (x1 - x0)).toInt()
        }
        return pts.last().second
    }

    fun score(h: HealthInputs): HealthScore {
        val inc = h.avgIncomePaise?.takeIf { it > 0 }
        val pillars = mutableListOf<PillarScore>()

        // 1. Savings rate: 20%+ is a widely used target.
        if (inc != null) {
            val rate = (inc - h.avgSpendPaise) * 100.0 / inc
            pillars += PillarScore(Pillar.SAVINGS, curve(rate, -10.0 to 0, 0.0 to 10, 10.0 to 50, 20.0 to 80, 30.0 to 100),
                "You keep about ${rate.toInt()}% of what comes in.",
                if (rate < 20) "Aim for 20%: cut one leak and automate a SIP on salary day." else "Great. Raise it with every salary hike.", "guide")
        } else pillars += PillarScore(Pillar.SAVINGS, null, "Needs your income.", "Add your monthly salary in the Smart Money Guide.", "guide")

        // 2. Emergency fund: 6 months of essentials.
        val needs = h.avgNeedsPaise.takeIf { it > 0 } ?: inc?.let { it / 2 }
        if (needs != null && needs > 0) {
            val months = h.emergencySavedPaise.toDouble() / needs
            pillars += PillarScore(Pillar.EMERGENCY, curve(months, 0.0 to 0, 1.0 to 25, 3.0 to 60, 6.0 to 100),
                "Covers about ${"%.1f".format(months)} months of essentials.",
                if (months < 6) "Build it to 6 months in a separate account before investing more." else "Fully built. Keep it untouched.", "goals")
        } else pillars += PillarScore(Pillar.EMERGENCY, null, "Needs your essential spending or income.", "Log a month of expenses or add your salary.", "guide")

        // 3. Protection: health cover for everyone; term cover if someone depends on you.
        val prot = (if (h.healthInsured) 60 else 0) + (if (!h.dependents || h.termInsured) 40 else 0)
        pillars += PillarScore(Pillar.PROTECTION, prot,
            listOf(if (h.healthInsured) "Health cover ✓" else "No health cover recorded", if (h.dependents) (if (h.termInsured) "term cover ✓" else "no term cover") else null).filterNotNull().joinToString(", ") + ".",
            when { !h.healthInsured -> "Get a family health policy; one hospital stay can wipe out years of savings."
                h.dependents && !h.termInsured -> "Get pure term insurance (about 10–15× yearly income)."
                else -> "Review your cover once a year." }, "guide")

        // 4. Debt load: EMIs under ~30% of income, and no card interest.
        if (inc != null) {
            val emiPct = h.emiPaise * 100.0 / inc
            val base = curve(emiPct, 0.0 to 100, 10.0 to 100, 30.0 to 60, 50.0 to 10)
            pillars += PillarScore(Pillar.DEBT, (base - if (h.costlyDebt) 40 else 0).coerceAtLeast(0),
                "EMIs take ${emiPct.toInt()}% of income" + if (h.costlyDebt) "; card interest or late fees seen recently." else ".",
                when { h.costlyDebt -> "Clear credit-card dues in full: ~40% yearly interest beats any investment."
                    emiPct > 30 -> "Avoid new loans; prepay the costliest one."
                    else -> "Healthy. Keep it that way." }, if (h.costlyDebt) "bills" else "guide")
        }

        // 5. Leak control: small avoidable spends as a share of spending.
        h.leaksSharePct?.let { pct ->
            pillars += PillarScore(Pillar.LEAKS, curve(pct.toDouble(), 5.0 to 100, 10.0 to 80, 20.0 to 50, 35.0 to 10),
                "Leaks were $pct% of last month's spending.",
                if (pct > 10) "Use the wishlist for wants and check Subscriptions." else "Well controlled.", if (pct > 10) "wishlist" else "subs")
        }

        // 6. Investing habit: invest every month.
        pillars += PillarScore(Pillar.INVESTING, listOf(0, 40, 70, 100)[h.investMonths.coerceIn(0, 3)],
            "Invested in ${h.investMonths} of the last 3 months.",
            if (h.investMonths < 3) "Start a monthly SIP, even ₹500; the habit matters most." else "Consistent. Step it up 10% a year.", "goals")

        val known = pillars.filter { it.score != null }
        val total = if (known.size < 3) null else (known.sumOf { it.score!! * it.pillar.weight }.toDouble() / known.sumOf { it.pillar.weight }).toInt()
        return HealthScore(total, pillars)
    }
}
