package com.kharchacheck.app.domain

enum class TipTopic(val label: String) { BUDGET("Budget"), CUT("Cut leaks"), SAVE("Save"), INVEST("Invest"), PROTECT("Protect"), DEBT("Debt") }

data class Tip(val id: String, val emoji: String, val title: String, val body: String, val topic: TipTopic)

/**
 * Educational money rules. Deliberately general and evergreen: no product names, no tax-slab specifics
 * (those change every budget), no promises of returns. Shown with an "education, not advice" note.
 */
object GoldenRules {
    const val DISCLAIMER = "Education, not financial advice. Returns are never guaranteed; for big decisions talk to a SEBI-registered adviser."

    val tips = listOf(
        Tip("pay_first", "🥇", "Pay yourself first", "On salary day, move your savings and SIPs out first. Spend what is left, not save what is left.", TipTopic.SAVE),
        Tip("503020", "🥧", "The 50/30/20 rule", "Aim for about 50% of income on needs, 30% on wants and 20% or more on savings and investing. Kharcha Check tracks this for you every month.", TipTopic.BUDGET),
        Tip("emergency", "🛟", "Emergency fund before investing", "Keep 6 months of essential expenses in a safe, easy-to-withdraw place like a savings account, sweep FD or liquid fund. It stops a bad month turning into debt.", TipTopic.SAVE),
        Tip("high_debt", "🔥", "Kill high-interest debt first", "Credit card dues and personal loans often cost far more per year than investments earn. Paying them off is a guaranteed return.", TipTopic.DEBT),
        Tip("cc_full", "💳", "Pay the full card bill, always", "Paying only the minimum due starts interest on the whole balance. Treat a credit card like a debit card with a delay.", TipTopic.DEBT),
        Tip("leaks", "🕳️", "Small leaks sink big ships", "₹150 a day on delivery and snacks is about ₹4,500 a month. Cutting one leak and investing it beats any coupon code.", TipTopic.CUT),
        Tip("wait_30", "⏳", "The 30-day rule for wants", "For any want above a few thousand rupees, wait 30 days. If you still want it, buy it guilt-free. Most impulse wants fade.", TipTopic.CUT),
        Tip("lifestyle", "📈", "Save half of every raise", "When income goes up, raise your SIP before your lifestyle. You won't miss money you never got used to spending.", TipTopic.SAVE),
        Tip("compounding", "🌱", "Start early, stay long", "Time in the market matters more than timing the market. ₹3,000 a month for 25 years can grow far more than ₹6,000 a month for 10 years.", TipTopic.INVEST),
        Tip("rule72", "✌️", "Rule of 72", "Divide 72 by the yearly return to see roughly how many years money takes to double. At 12% it's about 6 years; at 6% about 12.", TipTopic.INVEST),
        Tip("stepup", "🪜", "Step up your SIP every year", "Raising your SIP by 10% each year adds up to a much bigger corpus than a flat SIP, with almost no felt pain.", TipTopic.INVEST),
        Tip("diversify", "🧺", "Don't put all eggs in one basket", "Spread money across equity, debt and a little gold based on your goals and timeline. Rebalance once a year.", TipTopic.INVEST),
        Tip("low_cost", "🪶", "Watch costs", "A 1% higher yearly fee can eat a big part of long-term returns. Compare expense ratios; direct plans and index funds are usually cheaper.", TipTopic.INVEST),
        Tip("age_rule", "🎂", "100 minus age", "A simple starting point for how much to keep in equity: 100 minus your age, as a percentage. Adjust for your own risk comfort.", TipTopic.INVEST),
        Tip("insure", "☂️", "Insure first, then invest", "Get pure term life cover if anyone depends on your income, and health cover for the family. Keep insurance and investing separate.", TipTopic.PROTECT),
        Tip("understand", "🔍", "Never invest in what you don't understand", "If someone promises high, guaranteed returns quickly, walk away. Check registration with SEBI or RBI before trusting any scheme.", TipTopic.PROTECT),
        Tip("goals", "🎯", "Give every rupee a job", "Name your goals: emergency, a trip, a bike, retirement. Money with a purpose is harder to waste.", TipTopic.SAVE),
        Tip("review", "🗓️", "Do a monthly money date", "Spend 10 minutes at month-end: what went well, which leak to fix, and one target for next month. Kharcha Check's report is built for this.", TipTopic.BUDGET),
    )

    fun tipOfDay(epochDay: Long): Tip = tips[(epochDay % tips.size).toInt()]
}
