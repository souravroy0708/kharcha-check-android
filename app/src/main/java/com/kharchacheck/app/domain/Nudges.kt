package com.kharchacheck.app.domain

import com.kharchacheck.app.core.model.Bucket
import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.Goal
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Three daily nudges, each with its own purpose and time. */
enum class NudgeSlot(val label: String, val emoji: String, val hour: Int, val minute: Int, val why: String) {
    MORNING("Invest for a purpose", "💰", 8, 30, "Start the day with your goals"),
    AFTERNOON("Avoid unnecessary spending", "🛑", 13, 0, "Lunchtime, when food-delivery and shopping urges peak"),
    EVENING("Money-saving trick", "💡", 19, 30, "Before evening scrolling and shopping"),
}

/** [key] identifies the message for de-duplication; [route] is the screen the notification opens. */
data class Nudge(val key: String, val title: String, val body: String, val route: String)

/** Facts about the user's money right now, prepared from entries (all optional / zero when unknown). */
data class NudgeContext(
    val today: LocalDate,
    val salaryJustArrived: Boolean = false,
    /** Goals with a plan that still need money this month: (emoji + name, amount still to invest). */
    val goalsDue: List<Pair<String, Long>> = emptyList(),
    val rescuedPaise: Long = 0,
    val investedLastMonth: Boolean = true,
    val hasIncome: Boolean = false,
    val deliveryOrdersThisWeek: Int = 0,
    val deliverySpendThisWeekPaise: Long = 0,
    val leaksThisWeekPaise: Long = 0,
    val avgWeeklyLeaksPaise: Long = 0,
    val wishlistReady: Int = 0,
    val budgetLeftPaise: Long? = null,
    val daysLeftInMonth: Int = 0,
    val subsYearlyPaise: Long = 0,
    val subsUnreviewed: Int = 0,
    val spentToday: Boolean = true,
    val taxRoomPaise: Long = 0,
)

object Nudges {
    // ---------------------------------------------------------------- new content (original, India-relevant)

    private val PURPOSE = listOf(
        "🎯" to "Give every rupee a job. Money without a purpose tends to drift into wants. Name it: emergency, home, education, retirement.",
        "🪜" to "Pay yourself first: set your SIPs for the day after salary, so investing happens before spending.",
        "📈" to "Time matters more than timing. Starting a SIP 5 years earlier can matter more than a much bigger SIP started later.",
        "🎓" to "Education costs have historically risen faster than general prices. For a child's goal, start early and use equity for long horizons.",
        "🏠" to "For a home, saving a bigger down payment means a smaller loan and far less interest over 20 years.",
        "🛟" to "An emergency fund isn't an investment to maximise; it's peace of mind. Keep 6 months of essentials somewhere safe and easy to reach.",
        "🧓" to "Retirement has no loan. Children's education can be funded by loans; your old age can't. Don't skip retirement saving.",
        "🔁" to "Automate it: a fixed SIP date removes the monthly decision, and decisions are where good intentions fail.",
        "📊" to "Check your goals monthly but your investments rarely. Frequent checking tempts you to react to normal market swings.",
        "🧾" to "Direct plans of mutual funds have lower costs than regular plans. Over decades, a small yearly difference grows large.",
        "⬆️" to "Got a raise? Increase your SIP by at least half of it before your lifestyle quietly absorbs the rest.",
        "🧮" to "₹100 a day is about ₹3,000 a month. As a 15-year SIP it can grow into lakhs. Small daily choices are big monthly ones.",
        "🛡️" to "Protect before you grow: health insurance and (if someone depends on you) term insurance come before risky investments.",
        "👧" to "Saving for a daughter? Sukanya Samriddhi Yojana is a government small-savings scheme for girls; check the current rate at a post office or bank.",
        "📅" to "Match the investment to the deadline: money needed within 3 years shouldn't be in equity.",
        "🧺" to "Diversify simply: one broad index fund for growth, PPF or EPF for stability. Simple portfolios are easier to stick with.",
        "💳" to "Clear credit-card dues in full every month. No investment reliably beats ~40% yearly card interest.",
        "🎁" to "Windfalls (bonus, gift, refund): before spending, send a fixed share, say half, straight to a goal.",
        "🌱" to "Your first ₹1 lakh is the hardest. After that, compounding and habit start doing more of the work.",
        "⏳" to "Missed a month's SIP? Don't stop. Restart this month; consistency over years beats perfection.",
        "🏦" to "Keep goal money separate from your spending account, so it can't quietly fund everyday wants.",
        "👨‍👩‍👧" to "Talk money with your family once a month: shared goals are easier to keep than secret ones.",
        "📉" to "Markets fall sometimes. For long goals, a SIP keeps buying through the dips; that's when units are cheapest.",
        "🧠" to "Write down why each goal matters. On days you want to spend, the reason is what holds you back.",
        "✅" to "Review nominations on every account and investment once a year. It takes minutes and protects your family.",
    )

    private val AVOID = listOf(
        "🥡" to "Before ordering lunch, check what's at home. Cooking twice a week instead of ordering can save thousands a month.",
        "🛒" to "Hungry, bored or tired? Those are the moments apps push offers. Wait 20 minutes before ordering.",
        "📦" to "Free-delivery thresholds make you add things you didn't need. Batch your orders into one weekly list.",
        "💳" to "Remove saved cards from shopping and food apps today. A few seconds of friction stops many impulse buys.",
        "⏳" to "\"Sale ends tonight\" is designed to rush you. If it's a real need, a similar deal will come again.",
        "🧾" to "Check the final price with platform, packing and convenience fees before paying, not the menu price.",
        "📱" to "Turn off promotional notifications from shopping and delivery apps. Fewer prompts, fewer purchases.",
        "🛍️" to "Window-shopping online is still shopping. If you're not looking for something specific, close the app.",
        "🔁" to "Each \"small\" daily spend × 30 is its monthly cost. ₹150 a day on snacks and drinks is ₹4,500 a month.",
        "👟" to "One in, one out: before buying clothes or gadgets, decide which old one goes.",
        "🎮" to "In-app purchases hide real prices behind coins. Convert to rupees before you tap buy.",
        "🧃" to "Carry a water bottle and a snack. Convenience purchases add up faster than any single big buy.",
        "🧮" to "Ask: will I still be glad I bought this in a month? If unsure, park it on the wishlist.",
        "🚕" to "Short cab rides add up. Walk, cycle or use the metro or bus for trips under 2 km when you can.",
        "🏷️" to "A discount on something you didn't need is still 100% wasted money.",
    )

    private val TRICKS = listOf(
        "❄️" to "Set the AC to 24°C. India's energy efficiency bureau (BEE) recommends it as the default; each degree lower uses more power.",
        "💡" to "Switch remaining bulbs and tubes to LED and choose 5-star BEE-rated appliances; the lower bills repay the cost.",
        "💊" to "Ask your doctor about generic medicines; Jan Aushadhi Kendras sell generics that are often much cheaper than branded ones.",
        "🏧" to "Use your own bank's ATM or UPI. Other banks' ATMs are free only a few times a month; after that each withdrawal costs a fee.",
        "🏦" to "Check your statement for bank charges: minimum-balance penalties, SMS fees, card fees. Many can be avoided or waived.",
        "📶" to "Compare your mobile plan with what you actually use. If data is left over every month, a cheaper plan does the job.",
        "📺" to "Rotate OTT subscriptions: keep one at a time, binge it, cancel, switch. You'll rarely miss the others.",
        "🧺" to "Buy staples (rice, dal, oil, atta) monthly in sensible bulk; buy perishables fresh and small.",
        "🗓️" to "Plan the week's meals on Sunday. A plan cuts both food waste and last-minute ordering.",
        "🥦" to "Evening markets often sell fresh vegetables cheaper as vendors clear stock.",
        "♻️" to "Sell what you don't use: old phones, furniture and books on resale apps turn clutter into savings.",
        "📚" to "Use libraries, e-book libraries and second-hand bookstores before buying new books.",
        "🔋" to "Unplug chargers and switch off appliances at the plug; standby power quietly adds to the bill.",
        "🚰" to "Fix leaking taps and running toilets: they waste water and, with motors and geysers, electricity too.",
        "🧥" to "Buy off-season: winter wear in summer and summer wear in winter is usually much cheaper.",
        "🛡️" to "Compare insurance renewal quotes every year; loyalty rarely gets you the best premium.",
        "🧑‍🍳" to "Cook in batches. One evening of cooking can cover two or three dinners and kill the urge to order.",
        "🥤" to "Make coffee or chai at home and carry it. A daily café habit can cost more than a monthly SIP.",
        "🧾" to "Pay utility bills on the provider's own site or a no-fee UPI option to avoid convenience fees.",
        "🎒" to "Pack lunch three days a week. It's one of the most reliable savings for office-goers.",
        "🚗" to "Keep tyres at the right pressure and service on time; it improves mileage and avoids costly repairs.",
        "🏠" to "Negotiate: rent renewals, broadband, insurance and even gym fees often have room if you ask.",
        "🎉" to "Set a festival budget before the season starts, and track gifts and sweets against it.",
        "🪙" to "Every Sunday, move whatever is left in your spending account to savings. Start the week with a clean slate.",
        "🌿" to "Try one no-spend day a week. It resets habits and shows how much is truly optional.",
        "📝" to "Write a shopping list and buy only what's on it, online or in store.",
        "📦" to "Return unwanted items within the window. Set a reminder the day an order arrives.",
        "🧴" to "Compare price per unit (per kg, per litre), not the pack price. Bigger isn't always cheaper.",
        "🔌" to "Run washing machines and dishwashers full, and use cold-water cycles when the clothes allow.",
        "👕" to "Care for clothes: wash gently, dry in shade, repair small tears. Things that last longer cost less.",
        "📵" to "Delete shopping apps you rarely need; use the website when you do. Out of sight, out of cart.",
        "🧠" to "Wait for the second thought: the first thought sells, the second one decides.",
        "🎯" to "Pick one spending category to cut this month. One focused change beats ten vague intentions.",
        "📊" to "Look at last month's top three categories. Your biggest saving is usually hiding there.",
        "🤝" to "Split recurring costs: family OTT plans, shared rides, joint bulk grocery buys with neighbours.",
        "🧹" to "Declutter one drawer; you'll find things you forgot you own and won't buy again.",
        "🔍" to "Before any purchase over ₹1,000, compare across two stores and check price history if you can.",
        "💬" to "Tell a friend your savings goal. Saying it out loud makes you more likely to stick to it.",
        "📆" to "Buy gifts in advance during normal prices instead of at the last minute, when you pay for convenience.",
        "🏃" to "Exercise at home or in a park before committing to an expensive gym; join only once the habit sticks.",
    )

    // ---------------------------------------------------------------- pools: one per slot, each item a unique key

    private fun wisdomItems(vararg kinds: WisdomKind) = DailyWisdom.all.filter { it.kind in kinds }
        .map { "w${it.id}" to Pair("${it.emoji} ${it.kind.label}", "${it.line}\n\nTry today: ${it.doToday}") }
    private fun libItems(vararg topics: LibTopic) = WisdomLibrary.cards.filter { it.topic in topics }
        .map { "l:${it.id}" to Pair("${it.kind.emoji} From ${it.source.substringBefore(',').substringBefore(" (")}", "${it.idea}\n\n✅ ${it.action}") }
    private fun own(prefix: String, title: String, list: List<Pair<String, String>>) = list.mapIndexed { i, (e, t) -> "$prefix$i" to Pair("$e $title", t) }

    val pools: Map<NudgeSlot, List<Pair<String, Pair<String, String>>>> by lazy {
        mapOf(
            NudgeSlot.MORNING to own("p", "Invest with purpose", PURPOSE) + wisdomItems(WisdomKind.INVEST, WisdomKind.DEBT, WisdomKind.PROTECT) +
                libItems(LibTopic.INVESTING, LibTopic.DEBT, LibTopic.SAVING),
            NudgeSlot.AFTERNOON to own("a", "Spend less today", AVOID) + wisdomItems(WisdomKind.CUT) +
                OnlineSpending.tips.map { "o:${it.id}" to Pair("${it.emoji} ${it.title}", "${it.trick}\n\n✅ ${it.fix}") } + libItems(LibTopic.ONLINE, LibTopic.IMPULSE),
            NudgeSlot.EVENING to own("t", "Money-saving trick", TRICKS) + wisdomItems(WisdomKind.SAVE, WisdomKind.THOUGHT, WisdomKind.MINDSET) +
                libItems(LibTopic.HAPPINESS),
        )
    }

    /** The slot's pool item for [date]: a per-install shuffle, every item once per cycle, a new order each cycle. */
    fun poolItem(slot: NudgeSlot, date: LocalDate, seed: Long): Nudge {
        val pool = pools.getValue(slot)
        val day = date.toEpochDay()
        val cycle = Math.floorDiv(day, pool.size.toLong())
        val order = pool.indices.shuffled(Random(seed xor (cycle * 0x9E3779B97F4A7C15uL.toLong()) xor slot.ordinal.toLong()))
        val (key, content) = pool[order[Math.floorMod(day, pool.size.toLong()).toInt()]]
        return Nudge("s:$key", content.first, content.second, if (slot == NudgeSlot.AFTERNOON) "guide_online" else "guide")
    }

    // ---------------------------------------------------------------- personal nudges (preferred when they apply)

    /** Days before the same personal nudge may repeat. */
    private val COOLDOWN = mapOf("payfirst" to 25, "goal" to 3, "rescued" to 4, "noinvest" to 7, "delivery" to 3, "leakweek" to 3,
        "wish" to 2, "budget" to 3, "subs" to 10, "nospend" to 2, "tax" to 7)

    private fun personal(slot: NudgeSlot, c: NudgeContext): List<Nudge> {
        val out = mutableListOf<Nudge>()
        when (slot) {
            NudgeSlot.MORNING -> {
                val due = c.goalsDue.sumOf { it.second }
                if (c.salaryJustArrived && due > 0) out += Nudge("payfirst", "💸 Salary's in: pay yourself first",
                    "Move ${Money.format(due)} to your goals today, before the month's spending starts. Future you says thanks.", "goals")
                c.goalsDue.firstOrNull()?.let { (name, need) -> out += Nudge("goal", "🎯 $name: ${Money.format(need)} to go this month",
                    "Invest it this week and log it with \"Add money\" so your progress stays right.", "goals") }
                if (c.rescuedPaise >= 500_00) out += Nudge("rescued", "💰 You've rescued ${Money.format(c.rescuedPaise)} from leaks",
                    "You've spent less on leaks than usual this month. Move it to a goal before it leaks somewhere else.", "goals")
                if (c.hasIncome && !c.investedLastMonth) out += Nudge("noinvest", "📈 No investing last month",
                    "Start small: even a ₹500 monthly SIP builds the habit that matters most. Pick a goal and set a date.", "goals")
            }
            NudgeSlot.AFTERNOON -> {
                if (c.wishlistReady > 0) out += Nudge("wish", "🛒 ${c.wishlistReady} wishlist ${if (c.wishlistReady == 1) "item is" else "items are"} ready",
                    "The wait is over. Still want ${if (c.wishlistReady == 1) "it" else "them"}? Skipping keeps the money in your goals.", "wishlist")
                if (c.deliveryOrdersThisWeek >= 3) out += Nudge("delivery", "🛵 ${c.deliveryOrdersThisWeek} delivery orders this week",
                    "That's ${Money.format(c.deliverySpendThisWeekPaise)} so far. Lunch from home today could save about ${Money.format(c.deliverySpendThisWeekPaise / c.deliveryOrdersThisWeek)}.", "guide_online")
                if (c.avgWeeklyLeaksPaise > 0 && c.leaksThisWeekPaise * 10 > c.avgWeeklyLeaksPaise * 12) {
                    val pct = ((c.leaksThisWeekPaise - c.avgWeeklyLeaksPaise) * 100 / c.avgWeeklyLeaksPaise).toInt()
                    out += Nudge("leakweek", "🕳️ Leaks are $pct% above your usual this week",
                        "${Money.format(c.leaksThisWeekPaise)} on small avoidable spends. A no-leak afternoon brings the week back on track.", "guide_online")
                }
                c.budgetLeftPaise?.let { left -> if (c.daysLeftInMonth > 0 && left > 0) {
                    val perDay = left / c.daysLeftInMonth
                    if (perDay < 500_00) out += Nudge("budget", "🎯 ${Money.format(perDay)} a day left in your budget",
                        "${Money.format(left)} for the remaining ${c.daysLeftInMonth} days. Skip one want today to stay on track.", "home")
                } }
                if (c.subsUnreviewed > 0 && c.subsYearlyPaise >= 5_000_00) out += Nudge("subs", "🔁 Subscriptions cost ${Money.format(c.subsYearlyPaise)} a year",
                    "${c.subsUnreviewed} haven't been reviewed. Keep what you use, cancel the rest; it takes two minutes.", "subs")
            }
            NudgeSlot.EVENING -> {
                if (!c.spentToday) out += Nudge("nospend", "🌿 No spending today so far",
                    "Finish the day at zero for a no-spend day (+20 XP). Tomorrow's you will be glad.", "home")
                if (c.taxRoomPaise >= 1_000_00 && c.today.monthValue in 1..3) out += Nudge("tax", "🧾 You can still save ${Money.format(c.taxRoomPaise)} in tax",
                    "${ChronoUnit.DAYS.between(c.today, LocalDate.of(c.today.year, 3, 31))} days left in the financial year. See where you have room.", "tax")
            }
        }
        return out
    }

    /**
     * Today's nudge for a slot: the first personal nudge not shown within its cooldown, otherwise the slot's unique
     * pool item. [lastShown]: nudge key (or its cooldown family) → day last shown.
     */
    fun pick(slot: NudgeSlot, c: NudgeContext, seed: Long, lastShown: Map<String, LocalDate>): Nudge {
        personal(slot, c).firstOrNull { n ->
            val family = n.key.substringBefore(':')
            val last = lastShown[family] ?: return@firstOrNull true
            ChronoUnit.DAYS.between(last, c.today) >= (COOLDOWN[family] ?: 3)
        }?.let { return it }
        return poolItem(slot, c.today, seed)
    }

    // ---------------------------------------------------------------- context from the user's entries

    fun context(
        all: List<Txn>, cats: Map<Long, Category>, today: LocalDate,
        goals: List<Goal>, plans: Map<Long, GoalPlan>, monthlyBudgetPaise: Long,
        wishlistReady: Int, subsYearlyPaise: Long, subsUnreviewed: Int, taxRoomPaise: Long,
    ): NudgeContext {
        val month = YearMonth.from(today)
        fun bucket(t: Txn) = cats[t.categoryId]?.bucket
        val weekStart = today.minusDays(6)
        val week = all.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
        val leakWeek = week.filter { it.type == TxType.EXPENSE && bucket(it) == Bucket.LEAK }.sumOf { it.amountPaise }
        val last3 = (1..3).map { month.minusMonths(it.toLong()) }.toSet()
        val avgWeekly = all.filter { it.month in last3 && it.type == TxType.EXPENSE && bucket(it) == Bucket.LEAK }.sumOf { it.amountPaise } / 13
        val delivery = week.filter { cats[it.categoryId]?.key == "food_delivery" }
        val spendMonth = all.filter { it.month == month && it.type == TxType.EXPENSE && bucket(it)?.isSpend == true }.sumOf { it.amountPaise }
        val leaksMonth = all.filter { it.month == month && it.type == TxType.EXPENSE && bucket(it) == Bucket.LEAK }.sumOf { it.amountPaise }
        val avgLeaksMonth = all.filter { it.month in last3 && it.type == TxType.EXPENSE && bucket(it) == Bucket.LEAK }.sumOf { it.amountPaise } / 3
        val goalsDue = goals.filter { !it.archived && !it.done }.mapNotNull { g ->
            val p = plans[g.id] ?: return@mapNotNull null
            val m = GoalPlanner.plan(p, g.savedPaise)
            val need = (if (p.stepUp) m.sipStepUpStartPaise else m.sipFlatPaise) - all.filter { it.goalId == g.id && it.month == month }.sumOf { it.amountPaise }
            if (need > 0) "${g.emoji} ${g.name}" to need else null
        }
        return NudgeContext(
            today = today,
            salaryJustArrived = all.any { it.type == TxType.INCOME && cats[it.categoryId]?.key == "salary" && !it.date.isBefore(today.minusDays(2)) && !it.date.isAfter(today) },
            goalsDue = goalsDue,
            rescuedPaise = GoalPlanner.rescued(leaksMonth, avgLeaksMonth, today.dayOfMonth, month.lengthOfMonth()),
            investedLastMonth = all.any { it.month == month.minusMonths(1) && it.type == TxType.INVEST },
            hasIncome = all.any { it.type == TxType.INCOME && it.month in last3 },
            deliveryOrdersThisWeek = delivery.size, deliverySpendThisWeekPaise = delivery.sumOf { it.amountPaise },
            leaksThisWeekPaise = leakWeek, avgWeeklyLeaksPaise = avgWeekly,
            wishlistReady = wishlistReady,
            budgetLeftPaise = monthlyBudgetPaise.takeIf { it > 0 }?.let { it - spendMonth },
            daysLeftInMonth = month.lengthOfMonth() - today.dayOfMonth + 1,
            subsYearlyPaise = subsYearlyPaise, subsUnreviewed = subsUnreviewed,
            spentToday = all.any { it.date == today && it.type == TxType.EXPENSE },
            taxRoomPaise = taxRoomPaise,
        )
    }
}
