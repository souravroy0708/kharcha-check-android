package com.kharchacheck.app.domain

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Life-goal templates. Inflation rates are long-run rules of thumb for India (education costs have
 * historically risen faster than general prices). All editable by the user; educational, not advice.
 */
enum class GoalKind(val label: String, val emoji: String, val inflationPct: Double, val childGoal: Boolean, val hint: String) {
    HOME("Home down payment", "🏠", 7.0, false, "Usually 20% of the property price, plus registration and interiors"),
    CHILD_EDUCATION("Child's higher education", "🎓", 10.0, true, "Today's cost of the course you have in mind (college fees + living)"),
    CHILD_WEDDING("Child's wedding", "💍", 7.0, true, "What a wedding like the one you imagine would cost today"),
    CHILD_FUTURE("Child's future fund", "👧", 6.0, true, "A head start for your child: studies, a business, a first home"),
    RETIREMENT("Retirement", "🧓", 6.0, false, "Roughly 25× the yearly expenses you'll have after retiring"),
    CAR("Car or bike", "🚗", 5.0, false, "On-road price today"),
    TRAVEL("Big trip", "✈️", 6.0, false, "Flights, stay and spending today"),
    EMERGENCY("Emergency fund", "🛟", 0.0, false, "About 6 months of essential expenses"),
    CUSTOM("Something else", "🎯", 6.0, false, "Today's cost"),
}

/** How the goal is planned. Stored per goal (by id) alongside the simple Goal row. */
data class GoalPlan(
    val goalId: Long,
    val kind: GoalKind,
    val todayCostPaise: Long,
    val years: Int,
    val inflationPct: Double = kind.inflationPct,
    val stepUp: Boolean = true,
    /** Day of the month to be reminded to invest (e.g. the day after salary). 0 = no reminder. */
    val sipDay: Int = 5,
    /** For child goals: the child's age today (to show "when she is 18"). */
    val childAge: Int? = null,
)

data class GoalMath(
    val futureCostPaise: Long,
    val years: Int,
    val expectedReturnPct: Double,
    val mix: String,
    /** Monthly SIP needed with a flat amount, and the starting amount if it grows 10% a year. 0 if already funded. */
    val sipFlatPaise: Long,
    val sipStepUpStartPaise: Long,
    val alreadyCoveredPct: Int,
)

object GoalPlanner {

    /** Longer horizons can hold more equity; short ones must stay safe. Conservative expected returns. */
    fun expectedReturn(years: Int): Pair<Double, String> = when {
        years >= 7 -> 11.0 to "Mostly equity: index / flexi-cap fund SIP, plus PPF for stability"
        years >= 3 -> 9.0 to "Balanced: hybrid or balanced-advantage fund, part in PPF / RD"
        else -> 7.0 to "Safe: RD, FD or a liquid / short-duration debt fund. Avoid equity this close to the goal"
    }

    fun futureCost(todayPaise: Long, inflationPct: Double, years: Int): Long =
        (todayPaise * (1 + inflationPct / 100).pow(years)).roundToLong()

    /** Value of one rupee-per-month SIP (paid at the start of each month) after [months] months. */
    private fun sipFactor(monthlyRate: Double, months: Int): Double =
        if (monthlyRate == 0.0) months.toDouble() else ((1 + monthlyRate).pow(months) - 1) / monthlyRate * (1 + monthlyRate)

    fun plan(p: GoalPlan, savedPaise: Long): GoalMath {
        val years = p.years.coerceAtLeast(1)
        val future = futureCost(p.todayCostPaise, p.inflationPct, years)
        val (ret, mix) = expectedReturn(years)
        val r = ret / 100 / 12
        val n = years * 12
        val savedGrows = (savedPaise * (1 + ret / 100).pow(years)).roundToLong()
        val remaining = (future - savedGrows).coerceAtLeast(0)
        val flat = if (remaining == 0L) 0L else ceilTo100(remaining / sipFactor(r, n))
        // Step-up SIP value is linear in its starting amount, so solve with a ₹1 (100 paise) start.
        val unit = SalaryPlanner.stepUpSip(100, ret, years, 10.0).toDouble() / 100
        val step = if (remaining == 0L) 0L else ceilTo100(remaining / unit)
        val covered = if (future > 0) ((savedGrows.coerceAtMost(future) * 100) / future).toInt() else 100
        return GoalMath(future, years, ret, mix, flat, step, covered)
    }

    private fun ceilTo100(paise: Double): Long = (ceil(paise / 100_00) * 100_00).toLong()   // round up to the next ₹100

    /** Years from a child's age today to the age the goal is for. */
    fun yearsForChild(childAge: Int, goalAge: Int): Int = (goalAge - childAge).coerceAtLeast(1)

    /**
     * Are this month's contributions on track? Expected so far = the SIP times months elapsed (flat plan),
     * compared with what has actually been saved towards the goal.
     */
    fun onTrack(math: GoalMath, monthsElapsed: Int, savedPaise: Long, startSavedPaise: Long): Boolean =
        savedPaise - startSavedPaise >= math.sipFlatPaise * monthsElapsed * 9 / 10

    /** How much of a goal's monthly SIP a cut in one leak would cover. */
    fun leakCoverPct(cutPerMonthPaise: Long, sipPaise: Long): Int =
        if (sipPaise <= 0) 100 else ((cutPerMonthPaise * 100) / sipPaise).toInt().coerceAtMost(999)

    /** Money "rescued" this month: how much less you spent on leaks than your recent average (never negative). */
    fun rescued(leaksThisMonthPaise: Long, avgLeaksPaise: Long, dayOfMonth: Int, daysInMonth: Int): Long {
        val expectedSoFar = avgLeaksPaise * dayOfMonth / daysInMonth
        return (expectedSoFar - leaksThisMonthPaise).coerceAtLeast(0) / 100_00 * 100_00
    }
}
