package com.kharchacheck.app.domain

import kotlin.math.roundToLong

/** How much market ups and downs the user is comfortable with. Shifts the equity share. */
enum class RiskComfort(val label: String, val blurb: String, val equityShift: Int) {
    SAFE("Safe", "I'd lose sleep if my money fell 20%", -20),
    BALANCED("Balanced", "Some ups and downs are fine for better growth", 0),
    GROWTH("Growth", "I'm investing for 10+ years and can sit through big dips", 10),
}

data class PlanInput(
    val monthlyIncomePaise: Long,
    val age: Int,
    val dependents: Boolean,
    val risk: RiskComfort,
    /** Current total EMIs per month (from the user's EMI entries). */
    val emiPaise: Long = 0,
    /** Average essential spending per month, if known from entries. 0 = estimate from income. */
    val avgNeedsPaise: Long = 0,
    /** Money already set aside for emergencies. */
    val emergencySavedPaise: Long = 0,
    /** True if the user logged credit-card interest, late fees or personal-loan EMIs recently. */
    val hasCostlyDebt: Boolean = false,
)

/** One rung of the money ladder, in the order it should be done. */
data class PlanStep(val id: String, val emoji: String, val title: String, val detail: String, val amountPaise: Long? = null, val done: Boolean = false)

data class Allocation(val label: String, val sharePct: Int, val monthlyPaise: Long, val where: String)

data class SalaryPlan(
    val input: PlanInput,
    val needsCapPaise: Long,
    val wantsCapPaise: Long,
    val savePaise: Long,
    val savePct: Int,
    val emiPct: Int,
    val emiWarning: String?,
    val emergencyTargetPaise: Long,
    val termCoverPaise: Long?,
    val healthCoverPaise: Long,
    val steps: List<PlanStep>,
    /** Where the monthly investment goes once the emergency fund is built. */
    val allocation: List<Allocation>,
    val equityPct: Int,
    /** Corpus with a flat SIP vs a 10%-a-year step-up SIP, at assumed returns. */
    val corpus10y: Long,
    val corpus20y: Long,
    val corpus20yStepUp: Long,
)

/**
 * Turns a monthly salary into a practical, Indian-context plan. General education based on widely used
 * rules of thumb (50/30/20, 6-month emergency fund, term cover ~10–15× income, 100-minus-age equity).
 * Not personal financial advice; amounts are rounded so they read like guidance, not precision.
 */
object SalaryPlanner {

    /** Lower incomes can't realistically save 20%: start smaller and grow it. */
    fun savingsTargetPct(monthlyIncomePaise: Long): Int {
        val r = monthlyIncomePaise / 100
        return when {
            r < 25_000 -> 10
            r < 50_000 -> 20
            r < 1_00_000 -> 25
            else -> 30
        }
    }

    fun plan(i: PlanInput, doneSteps: Set<String> = emptySet(), equityReturnPct: Double = 12.0, debtReturnPct: Double = 7.0): SalaryPlan {
        val income = i.monthlyIncomePaise
        val savePct = savingsTargetPct(income)
        val save = round100(income * savePct / 100)
        val needsCapPct = if (income / 100 < 25_000) 60 else 50
        val needsCap = round100(income * needsCapPct / 100)
        val wants = (income - needsCap - save).coerceAtLeast(0)
        val emiPct = if (income > 0) (i.emiPaise * 100 / income).toInt() else 0
        val emiWarning = when {
            emiPct > 40 -> "EMIs take $emiPct% of your income. Above 40% is risky: avoid any new loan and prepay the costliest one first."
            emiPct > 30 -> "EMIs take $emiPct% of your income. Try to keep them under 30% before taking on more."
            else -> null
        }
        val needsBase = if (i.avgNeedsPaise > 0) i.avgNeedsPaise else income * 50 / 100
        val emergency = round1000(needsBase * 6)
        val annual = income * 12
        val term = if (i.dependents) round1L(annual * 12) else null // ~12× annual income, middle of the 10–15× rule of thumb
        val health = if (i.dependents) 10_00_000_00L else 5_00_000_00L

        val equity = (100 - i.age + i.risk.equityShift).coerceIn(20, 80)
        val gold = 10
        val debt = 100 - equity - gold
        val allocation = listOf(
            Allocation("Equity", equity, round100(save * equity / 100), "Monthly SIP in a low-cost index fund (Nifty 50 / Nifty 500) or a flexi-cap fund"),
            Allocation("Safe / debt", debt, round100(save * debt / 100), "PPF, EPF/VPF, or a debt / liquid fund"),
            Allocation("Gold", gold, round100(save * gold / 100), "Gold ETF / gold fund or Sovereign Gold Bonds when available"),
        )
        val blended = (equity * equityReturnPct + (100 - equity) * debtReturnPct) / 100.0
        val starter = round1000(needsBase)

        val steps = buildList {
            add(PlanStep("budget", "🧾", "Track every rupee for a month",
                "Log spends daily. You can't fix a leak you can't see. Keep needs under ${money(needsCap)} and wants under ${money(wants)}."))
            add(PlanStep("starter_fund", "🛟", "Starter emergency fund: one month",
                "Park about ${money(starter)} in a separate savings account or sweep FD before anything else.", starter))
            add(PlanStep("health", "🏥", "Health insurance",
                "A family floater of about ${money(health)} (or more in a metro). Employer cover alone can vanish when you change jobs. A super top-up is a cheap way to add more.", health))
            if (term != null) add(PlanStep("term", "☂️", "Pure term life insurance",
                "Since someone depends on your income, about ${money(term)} of cover (roughly 12× your yearly income). Pure term only; keep insurance and investing separate.", term))
            if (i.hasCostlyDebt || emiWarning != null) add(PlanStep("debt", "🔥", "Clear costly debt",
                "Pay off credit-card dues and personal loans before investing beyond small SIPs. Card interest is often 36–42% a year; no investment beats that."))
            add(PlanStep("full_fund", "🏦", "Full emergency fund: six months",
                "Build it to about ${money(emergency)} (6 × your essential monthly costs). Use half your monthly savings until it's done." +
                    if (i.emergencySavedPaise > 0) " You have ${money(i.emergencySavedPaise)} so far." else "", emergency))
            add(PlanStep("sip", "📈", "Start your monthly SIP",
                "Invest ${money(save)} a month: ${allocation.joinToString { "${it.label.lowercase()} ${money(it.monthlyPaise)}" }}. Automate it for the day after salary.", save))
            add(PlanStep("step_up", "🪜", "Step it up 10% every year",
                "Each April (or on every raise), increase your SIP by at least 10%. Save half of every raise before lifestyle catches up."))
            add(PlanStep("review", "🗓️", "Yearly money check-up",
                "Once a year: rebalance to your target mix, top up insurance cover, check nominees on every account."))
        }.map { it.copy(done = it.id in doneSteps || (it.id == "full_fund" && i.emergencySavedPaise >= emergency && emergency > 0)) }

        return SalaryPlan(
            input = i, needsCapPaise = needsCap, wantsCapPaise = wants, savePaise = save, savePct = savePct,
            emiPct = emiPct, emiWarning = emiWarning, emergencyTargetPaise = emergency, termCoverPaise = term, healthCoverPaise = health,
            steps = steps, allocation = allocation, equityPct = equity,
            corpus10y = InvestMath.sipFutureValue(save, blended, 10),
            corpus20y = InvestMath.sipFutureValue(save, blended, 20),
            corpus20yStepUp = stepUpSip(save, blended, 20, 10.0),
        )
    }

    /** SIP that grows by [stepUpPct] each year; paid at the start of each month. */
    fun stepUpSip(monthlyPaise: Long, annualRatePct: Double, years: Int, stepUpPct: Double): Long {
        val r = annualRatePct / 100 / 12
        var value = 0.0
        var sip = monthlyPaise.toDouble()
        for (y in 0 until years) {
            repeat(12) { value = (value + sip) * (1 + r) }
            sip *= 1 + stepUpPct / 100
        }
        return value.roundToLong()
    }

    private fun round100(p: Long) = (p / 100_00) * 100_00            // nearest ₹100 below
    private fun round1000(p: Long) = ((p + 500_00) / 1_000_00) * 1_000_00
    private fun round1L(p: Long) = ((p + 50_000_00) / 1_00_000_00) * 1_00_000_00
    private fun money(p: Long) = com.kharchacheck.app.core.model.Money.compact(p)

}
