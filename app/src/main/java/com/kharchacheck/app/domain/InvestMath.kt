package com.kharchacheck.app.domain

import kotlin.math.pow
import kotlin.math.roundToLong

/** Small, honest maths used to show "what this money could become". All in paise. */
object InvestMath {
    /** Future value of a monthly SIP paid at the start of each month. */
    fun sipFutureValue(monthlyPaise: Long, annualRatePct: Double, years: Int): Long {
        if (monthlyPaise <= 0 || years <= 0) return 0
        val n = years * 12
        val r = annualRatePct / 100.0 / 12.0
        if (r == 0.0) return monthlyPaise * n
        val fv = monthlyPaise * (((1 + r).pow(n) - 1) / r) * (1 + r)
        return fv.roundToLong()
    }

    fun lumpSumFutureValue(paise: Long, annualRatePct: Double, years: Int): Long =
        (paise * (1 + annualRatePct / 100.0).pow(years)).roundToLong()

    /** Rule of 72: approximate years to double money. */
    fun yearsToDouble(annualRatePct: Double): Double = if (annualRatePct <= 0) Double.POSITIVE_INFINITY else 72.0 / annualRatePct

    /** Classic thumb rule (100 − age) for an equity share, clamped to a sensible range. */
    fun equityShareByAge(age: Int): Int = (100 - age).coerceIn(20, 80)

    fun emergencyFundTarget(avgMonthlyNeedsPaise: Long, months: Int = 6): Long = avgMonthlyNeedsPaise * months
}
