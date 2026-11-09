package com.kharchacheck.app.domain

import java.time.LocalDate
import kotlin.random.Random

enum class WisdomKind(val label: String) { THOUGHT("Money thought"), SAVE("Saving tip"), INVEST("Investing tip"), CUT("Cut a leak"), DEBT("Debt tip"), PROTECT("Stay safe"), MINDSET("Mindset") }

/** One daily card: a short line to remember, plus one small thing to do today. */
data class Wisdom(val id: Int, val kind: WisdomKind, val emoji: String, val line: String, val doToday: String)

/**
 * 100 original money sayings and tips, written for Indian everyday life.
 * Original text (not attributed quotes) so nothing is misquoted. Educational, not advice.
 *
 * Each install shows them in its own shuffled order, one per day, with no repeat until all 100 are seen.
 * Then a fresh shuffle starts, never opening with the card shown the day before.
 */
object DailyWisdom {
    private var n = 0
    private fun w(kind: WisdomKind, emoji: String, line: String, doToday: String) = Wisdom(++n, kind, emoji, line, doToday)
    private val T = WisdomKind.THOUGHT; private val S = WisdomKind.SAVE; private val I = WisdomKind.INVEST
    private val C = WisdomKind.CUT; private val D = WisdomKind.DEBT; private val P = WisdomKind.PROTECT; private val M = WisdomKind.MINDSET

    val all: List<Wisdom> = listOf(
        w(T, "🪙", "Paisa bachana bhi kamaane jaisa hai. Every rupee you don't waste is a rupee you earned twice.", "Log every spend today, even the ₹10 ones."),
        w(T, "🌱", "Small amounts, given time, become big amounts. Time is the one ingredient you can't buy later.", "Check how much ₹500 a month becomes in 10 years in the Grow tab."),
        w(T, "🧭", "A budget isn't a cage. It's a map that shows where your money is going before it's gone.", "Set or review your monthly budget in Settings."),
        w(T, "🔦", "What gets tracked gets managed. You can't fix a leak you can't see.", "Open this month's report and find your biggest category."),
        w(T, "🏗️", "Wealth is built from boring habits repeated for years, not from one lucky trade.", "Pick one boring money habit and do it today."),
        w(T, "⚖️", "Income is what you earn. Wealth is what you keep. Only one of them shows up when you need it.", "Note how much you kept last month, not how much you earned."),
        w(T, "🧱", "Every rich habit starts as a small one that someone refused to skip.", "Don't break your logging streak today."),
        w(T, "🪞", "Your bank statement is an honest diary of your priorities.", "Scroll last week's entries. Do they match what you care about?"),
        w(T, "🚦", "Spending isn't the enemy. Spending without thinking is.", "Before your next purchase, pause for ten seconds."),
        w(T, "🌊", "Money flows to where attention goes. Give yours a little every day.", "Spend two minutes in Kharcha Check before bed."),
        w(T, "🏔️", "Big goals look impossible from the bottom. They're climbed one month at a time.", "Break one savings goal into a monthly amount."),
        w(T, "🎯", "A goal without a number is just a wish. Name it, price it, date it.", "Create a savings goal with a target amount."),
        w(T, "🧮", "₹100 a day is ₹36,500 a year. Daily habits have annual price tags.", "Multiply one daily habit by 365 and see its yearly cost."),
        w(T, "🕰️", "The best day to start saving was years ago. The second best is today.", "Move any amount, even ₹100, to savings today."),
        w(T, "🔁", "Money habits compound just like money does, in both directions.", "Repeat one good money action from yesterday."),

        w(S, "🥇", "Pay yourself first. Save on salary day, then spend what's left.", "Set your SIP or RD date to the day after salary."),
        w(S, "🛟", "An emergency fund turns a crisis into an inconvenience.", "Start an Emergency fund goal in the Grow tab."),
        w(S, "🧺", "Keep different money in different pots: spend, save, invest. Mixing them invites leaks.", "Name where each of your accounts' money is meant for."),
        w(S, "📆", "Round-figure saving works: save ₹50 every time you skip a want.", "Skip one small want today and save that exact amount."),
        w(S, "🎁", "Bonus, gift or cashback? Save half before you celebrate with the other half.", "Decide now what you'll do with the next windfall."),
        w(S, "📈", "Got a raise? Raise your savings first, lifestyle second.", "Plan how much of your next raise goes to savings."),
        w(S, "🧾", "Annual costs hide in plain sight: insurance, school fees, festivals. Save for them monthly.", "List three yearly expenses and divide each by 12."),
        w(S, "🪔", "Festivals come every year, yet they surprise every budget. Start a festival fund now.", "Create a goal for the next big festival or wedding season."),
        w(S, "🗓️", "A no-spend day costs nothing and teaches everything.", "Try to make today a no-spend day."),
        w(S, "🏦", "Automate the good, so willpower is only needed for the rare.", "Automate one saving or investment instead of doing it by hand."),
        w(S, "🧊", "Cash sitting idle in a savings account loses value slowly to inflation. Give spare money a job.", "Check how much spare cash is sitting idle."),
        w(S, "🎒", "Saving for a want in advance makes it guilt-free. Buying it on EMI makes it expensive.", "Start a small goal for something you want."),
        w(S, "👪", "Teach kids the three jars: spend, save, share. The habit lasts longer than any pocket money.", "Talk to someone at home about one money habit."),
        w(S, "🧳", "Plan trips from a trip fund, not from the credit card limit.", "Price your next trip and set a monthly saving for it."),
        w(S, "📉", "Lower fixed costs are permanent savings. One renegotiated bill pays you every month.", "Review one recurring bill: plan, recharge or subscription."),

        w(I, "🌳", "Investing is planting a tree. Don't dig it up every week to check the roots.", "Decide how often you'll check investments, monthly at most."),
        w(I, "✌️", "Rule of 72: divide 72 by the yearly return to see roughly how many years money takes to double.", "Work out the doubling time for your own investments."),
        w(I, "🪜", "Step up your SIP every year, even a little. Small increases make a big difference over decades.", "Note a date to raise your SIP by 10%."),
        w(I, "🧺", "Diversify across equity, debt and a little gold. Different baskets fall on different days.", "Write down roughly how your savings are split today."),
        w(I, "🪶", "Costs are certain, returns are not. Prefer low-cost funds and check expense ratios.", "Look up the expense ratio of one fund you hold."),
        w(I, "⏳", "Time in the market matters more than timing the market.", "If you've been waiting for the perfect time, start small instead."),
        w(I, "🎢", "Markets fall sometimes. A long-term SIP buys more units when prices are low.", "Remind yourself why you invest before the next dip."),
        w(I, "🎂", "100 minus your age is a simple starting point for how much to keep in equity.", "Add your age in Settings to see your starting split."),
        w(I, "🎯", "Match the investment to the goal's timeline. Money needed soon shouldn't ride the market.", "Tag each goal as short, medium or long term."),
        w(I, "🔍", "Never invest in what you can't explain to a friend in two minutes.", "Explain one of your investments out loud."),
        w(I, "📚", "An hour of learning about money can be worth more than a month of overtime.", "Read one article or chapter on personal finance."),
        w(I, "🐢", "Slow and steady compounding beats fast and risky gambling.", "Avoid any tip that promises quick doubling."),
        w(I, "🧘", "The best portfolio is one you can hold calmly through bad news.", "Ask yourself: could I hold this through a 30% fall?"),
        w(I, "🏛️", "Long-term government schemes like PPF and NPS reward patience and discipline.", "Check if you're using any long-term scheme yet."),
        w(I, "🔄", "Rebalance once a year, not every time the news is loud.", "Set a yearly reminder to rebalance."),
        w(I, "🚫", "Stock tips from WhatsApp groups are usually someone else's exit plan.", "Mute one money-tips group you don't trust."),
        w(I, "📏", "Measure investment returns over years, not weeks.", "Look at a 5-year view, not today's change."),
        w(I, "🌾", "Start a SIP with whatever you can. ₹500 a month started today beats ₹5,000 someday.", "Find one small amount you could invest monthly."),

        w(C, "🛵", "Delivery fees, packing charges and surge pricing turn a ₹200 meal into ₹300.", "Cook or carry lunch once this week instead of ordering."),
        w(C, "⚡", "Ten-minute delivery is designed for ten-second decisions. Make a list and stick to it.", "Make one planned grocery list for the week."),
        w(C, "☕", "₹40 chai twice a day is about ₹2,400 a month. Enjoy it, but notice it.", "Count your paid chai and coffee this week."),
        w(C, "🛒", "Add to cart, then wait a day. If you still want it tomorrow, it's probably a real want.", "Leave one item in your cart overnight."),
        w(C, "🔁", "Unused subscriptions are money leaking quietly every month.", "Cancel one subscription you haven't used in 30 days."),
        w(C, "🍟", "Junk food costs twice: once at the shop, again in health later.", "Swap one packaged snack for fruit today."),
        w(C, "🏷️", "A sale saves money only if you were going to buy it anyway.", "Before buying on sale, ask: would I pay full price?"),
        w(C, "📱", "Shopping apps are built to keep you browsing. Unfollow, unsubscribe, uninstall.", "Turn off notifications from one shopping app."),
        w(C, "⏰", "Late fees and penalties are the easiest money to save: just pay on time.", "Set reminders for your next bill due dates."),
        w(C, "🧾", "Convenience fees add up. Pay bills from the source where they're free.", "Check if you're paying convenience fees anywhere."),
        w(C, "😬", "Mark regrets honestly. Your regret list is your easiest cut list.", "Tag your last regretted purchase as Regret."),
        w(C, "🎮", "In-app purchases are small by design. Total them before they total you.", "Check this month's game and app spending."),
        w(C, "🚕", "For short regular trips, metro, bus or walking can save more than you think.", "Compare a week of cab rides with public transport."),
        w(C, "🥤", "A cold drink a day is a small leak with a big yearly total.", "Carry a water bottle today."),
        w(C, "🎲", "Betting and fantasy apps are built so the house wins over time.", "If you use one, set a hard monthly limit or delete it."),
        w(C, "💡", "Switch off, unplug, fix the dripping tap. Utility savings are invisible but real.", "Find one switch or appliance to turn off when not in use."),
        w(C, "🎉", "Celebrate milestones, not every weekend. Special stays special when it's rare.", "Plan one free outing this week."),

        w(D, "🔥", "High-interest debt grows faster than most investments. Paying it off is a guaranteed return.", "List your debts from highest interest to lowest."),
        w(D, "💳", "Pay the full credit card bill, not the minimum due. Minimum payments keep you paying interest for years.", "Check your card's total due vs minimum due."),
        w(D, "📵", "An EMI is a promise from your future self. Make fewer promises for wants.", "Think twice before your next EMI on a gadget."),
        w(D, "🪓", "Pay the smallest debt first for momentum, or the costliest first for savings. Either way, start.", "Pick one debt and pay a little extra this month."),
        w(D, "🧾", "Buy Now Pay Later is still debt. It just doesn't look like debt.", "Add up any pending BNPL dues."),
        w(D, "🤝", "Lend only what you can afford never to see again, even to family.", "Log any money you've lent so you don't forget it."),
        w(D, "📊", "Keep total EMIs comfortably below a third of take-home pay so you still have room to breathe.", "Calculate your EMIs as a share of income."),
        w(D, "🛑", "Never borrow to invest or to speculate.", "Make sure no investment is funded with a loan."),
        w(D, "✂️", "Prepaying a loan, even a little, cuts both the tenure and the interest.", "Check whether your loan allows part-prepayment."),
        w(D, "🗂️", "Know your credit score. Late payments follow you for years.", "Check your credit report once this year."),

        w(P, "☂️", "If anyone depends on your income, pure term insurance comes before investing.", "Check whether your family would be covered."),
        w(P, "🏥", "One hospital stay can wipe out years of savings. Health insurance protects your goals.", "Check your health cover and its renewal date."),
        w(P, "🔐", "No real bank will ever ask for your OTP, PIN or CVV. Ever.", "Remind a family member never to share OTPs."),
        w(P, "🎣", "Too-good-to-be-true returns are usually exactly that. Check SEBI or RBI registration first.", "Verify one investment platform you use is registered."),
        w(P, "📲", "Never scan a QR code to receive money. QR codes are for paying, not receiving.", "Share this tip with someone older at home."),
        w(P, "🧓", "Write down where your accounts and policies are. Your family shouldn't have to guess.", "Start a simple list of your accounts and nominees."),
        w(P, "📝", "Add nominees to every account and investment. It takes minutes and saves months.", "Check nominee details on one account."),
        w(P, "🌐", "Free Wi-Fi and money apps don't mix. Bank only on networks you trust.", "Avoid banking on public Wi-Fi this week."),
        w(P, "📞", "Urgency is a scammer's favourite tool. Real problems can wait for you to call back.", "If a call rushes you about money, hang up and verify."),
        w(P, "🧩", "Keep insurance and investment separate. Each does its own job better.", "Check if any policy you hold mixes both."),

        w(M, "🙂", "Contentment is the cheapest luxury and the richest feeling.", "Write down three things you already own and love."),
        w(M, "👀", "Comparing your life to someone's Instagram is comparing your inside to their outside.", "Unfollow one account that makes you want to spend."),
        w(M, "🧠", "Every purchase is a trade of your hours. Ask how many hours something costs.", "Convert your next big purchase into work hours."),
        w(M, "🌤️", "A bad money month is data, not destiny. Learn, adjust and go again.", "Note one thing to do differently next month."),
        w(M, "🏃", "Progress beats perfection. A tracked, imperfect budget beats a perfect one you abandoned.", "Log today even if yesterday was missed."),
        w(M, "🤗", "Talking about money with your partner or family turns stress into teamwork.", "Share one money goal with someone close."),
        w(M, "🧘", "Spend on what matters to you, cut ruthlessly on what doesn't.", "Name one category you truly value and one you don't."),
        w(M, "🎖️", "Celebrate small wins. A leak-free week deserves a pat on the back.", "Check your streak and badges in the Grow tab."),
        w(M, "🔭", "Think in years for wealth and in days for habits.", "Pick a five-year goal and a one-day habit for it."),
        w(M, "🪴", "Financial peace isn't about having lots. It's about having enough and knowing it.", "Decide what 'enough' looks like for you this year."),
        w(M, "📣", "Status purchases impress strangers for a moment and cost you for years.", "Skip one purchase that's mainly for show."),
        w(M, "🚪", "Saying no to a want is saying yes to a goal.", "Turn down one unplanned spend today, guilt-free."),
        w(S, "🐷", "Coins and small change add up. A gullak still works in the UPI age: round up and set the difference aside.", "Round up today's spends and save the difference."),
        w(I, "🧑‍🏫", "Ask how an adviser gets paid before you take their advice.", "Find out how your insurance or fund agent earns their commission."),
        w(C, "🏪", "Buying in bulk saves money only on things you actually finish before they expire.", "Check your kitchen for anything bought in bulk and unused."),
    )

    /**
     * The card for [date]. [seed] is random per install, so each person gets their own order.
     * Within each 100-day cycle every card appears exactly once.
     */
    fun forDate(date: LocalDate, seed: Long): Wisdom {
        val day = date.toEpochDay()
        val size = all.size
        val cycle = Math.floorDiv(day, size.toLong())
        val pos = Math.floorMod(day, size.toLong()).toInt()
        val order = orderFor(cycle, seed)
        return all[order[pos]]
    }

    internal fun orderFor(cycle: Long, seed: Long): List<Int> {
        val order = raw(cycle, seed)
        // Avoid the same card on the last day of one cycle and the first day of the next.
        // Swapping the first two never changes a cycle's last card, so the check stays consistent.
        val prevLast = raw(cycle - 1, seed).last()
        return if (order.first() == prevLast) listOf(order[1], order[0]) + order.drop(2) else order
    }

    private fun raw(cycle: Long, seed: Long): List<Int> =
        all.indices.shuffled(Random(seed xor (cycle * GOLDEN)))

    private const val GOLDEN = -7046029254386353131L // 0x9E3779B97F4A7C15 as a signed Long
}
