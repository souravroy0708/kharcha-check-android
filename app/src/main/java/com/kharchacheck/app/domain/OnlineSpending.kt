package com.kharchacheck.app.domain

/** A specific trick or habit that makes people overspend online, and what to do about it. */
data class OnlineTip(val id: String, val emoji: String, val title: String, val trick: String, val fix: String, val categoryKeys: Set<String> = emptySet())

/**
 * The "dark patterns" Indian shopping, delivery and quick-commerce apps commonly use, written plainly.
 * Linked to categories so the guide can lead with the user's own biggest online leak.
 */
object OnlineSpending {
    private val food = setOf("food_delivery", "junk_food", "dining_out")
    private val qc = setOf("quick_commerce", "groceries")
    private val shop = setOf("impulse_shopping", "shopping", "shopping_clothes", "gadgets", "regret_misc")
    private val subs = setOf("unused_subscriptions", "ott")

    /** Category keys that are mostly spent online. */
    val onlineKeys = food + qc + shop + subs + setOf("gaming", "betting", "cab", "bank_charges", "fees_penalties", "cc_interest")

    val tips = listOf(
        OnlineTip("menu_markup", "🧾", "Delivery menus cost more", "Many restaurants list higher prices on delivery apps than in the restaurant, then add delivery, packing, platform and small-order fees.",
            "Compare the final bill with the restaurant's own price. Order directly or pick up when you can.", food),
        OnlineTip("hunger_ordering", "😋", "Ordering when hungry and tired", "Late-evening hunger is when apps push offers hardest, and when you're least careful.",
            "Keep two quick backup meals at home. Decide dinner by 6 pm.", food),
        OnlineTip("combo_upsell", "🍟", "\"Make it a meal?\"", "Combos and add-ons are designed to raise the order value a little at a time.",
            "Order what you came for. Skip the drink and dessert add-ons.", food),
        OnlineTip("delivery_pass", "🎫", "Delivery memberships", "A monthly pass feels like savings but nudges you to order more often to \"use it\".",
            "Only keep a pass if you'd order that much anyway. Check your last 3 months first.", food),
        OnlineTip("min_order", "⚡", "The minimum-order trap", "Free delivery above a threshold makes you add things you didn't need.",
            "Keep a running grocery list and order once or twice a week, not every time you remember one item.", qc),
        OnlineTip("ten_minutes", "⏱️", "10-minute delivery = 10-second decisions", "Instant delivery removes the pause that normally stops an impulse buy.",
            "Plan groceries weekly. Treat instant delivery as an emergency option, not the default.", qc),
        OnlineTip("fake_urgency", "⏳", "Fake countdowns and \"only 2 left\"", "Timers and low-stock badges create panic so you skip comparing.",
            "If it's a real need, it'll still be needed tomorrow. Close the app and come back after 24 hours.", shop),
        OnlineTip("sale_events", "🏷️", "Big sale days", "Sale events are built around fear of missing out. Prices often rise just before.",
            "Make a list before the sale, set a budget, and check price history. Buy only what's on the list.", shop),
        OnlineTip("cart_rule", "🛒", "The cart as a waiting room", "One-tap checkout and saved cards make buying frictionless.",
            "Add to cart, wait 24 hours. For anything above a few thousand rupees, wait 30 days.", shop),
        OnlineTip("saved_cards", "💳", "Saved cards and one-tap pay", "When paying is effortless, you notice it less.",
            "Remove saved cards from shopping apps. A few seconds of friction saves real money.", shop),
        OnlineTip("no_cost_emi", "📆", "\"No-cost EMI\"", "The discount often just covers the interest, and processing fees or GST can still apply. It also makes pricier items feel affordable.",
            "Ask: would I buy this if I had to pay in full today? If not, skip it.", shop),
        OnlineTip("bnpl", "🧾", "Buy Now, Pay Later", "Pay-later makes spending painless now and stacks up small dues across apps.",
            "Treat pay-later like a credit card. Keep it off, or set a hard monthly cap.", shop + setOf("fees_penalties")),
        OnlineTip("cashback", "💸", "Cashback that keeps you spending", "Cashback is often credited as app coins or vouchers that only work on the next purchase.",
            "Value cashback at zero unless it's real money in your bank. Never buy just to earn it.", shop),
        OnlineTip("influencers", "📱", "Influencer \"must-haves\"", "Many recommendations are paid or earn commission through affiliate links.",
            "Unfollow accounts that make you want to buy. Read independent reviews instead.", shop),
        OnlineTip("free_trials", "🔁", "Free trials that auto-renew", "Trials need your card or UPI mandate and quietly start charging.",
            "Set a reminder a day before every trial ends. Better: review UPI AutoPay mandates monthly in your UPI app.", subs),
        OnlineTip("sub_creep", "📺", "Subscription creep", "₹149 here, ₹299 there: streaming, music, cloud and app subscriptions add up quietly.",
            "List every subscription once a quarter. Keep one or two you actually use and rotate the rest.", subs),
        OnlineTip("annual_plans", "📅", "Annual plans you forget", "Yearly renewals hit when you least expect them.",
            "Add annual subscriptions to Bills & due dates so you decide before they renew.", subs),
        OnlineTip("game_coins", "🎮", "In-game coins and passes", "Virtual currency hides the real price and small top-ups feel harmless.",
            "Convert coins to rupees before buying. Set a monthly gaming budget in Category budgets.", setOf("gaming")),
        OnlineTip("betting_apps", "🎲", "Fantasy and betting apps", "These are designed so the platform wins over time, with bonuses that keep you playing.",
            "Set a strict limit or uninstall. Never chase losses.", setOf("betting")),
        OnlineTip("surge", "🚕", "Surge pricing", "Cab apps raise prices at peak times, and a habit of short rides adds up.",
            "Leave 15 minutes earlier, use metro or bus for regular routes, and compare two apps before booking.", setOf("cab")),
        OnlineTip("conv_fees", "🧮", "Convenience and platform fees", "Small fees on bills, tickets and recharges feel trivial individually.",
            "Pay utility bills and recharges from the source or a no-fee UPI option.", setOf("bank_charges")),
        OnlineTip("notifications", "🔕", "Sale notifications", "Push notifications and emails are timed to catch you when you're bored.",
            "Turn off promotional notifications for shopping and delivery apps. Unsubscribe from sale emails."),
        OnlineTip("uninstall", "🗑️", "Out of sight, out of cart", "Having shopping apps on the home screen invites browsing.",
            "Move them off the home screen, or uninstall and use the website only when you need something."),
        OnlineTip("returns", "📦", "\"I can always return it\"", "Easy returns lower the bar for buying, but many items never go back.",
            "Buy as if returns didn't exist."),
    )

    fun tipsFor(categoryKey: String): List<OnlineTip> = tips.filter { categoryKey in it.categoryKeys }

    /** The user's biggest online-heavy category this period, to lead the guide with. */
    fun biggestLeak(lines: List<CategoryLine>): CategoryLine? = lines.filter { it.category.key in onlineKeys && it.paise > 0 }.maxByOrNull { it.paise }
}
